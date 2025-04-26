// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.file.impl.FileManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class PsiManagerImpl extends PsiManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance(PsiManagerImpl.class);

  private final Project myProject;
  private final NotNullLazyValue<? extends FileIndexFacade> myFileIndex;
  private final PsiModificationTracker myModificationTracker;

  private final FileManagerEx myFileManager;

  private final List<PsiTreeChangePreprocessor> myTreeChangePreprocessors = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<PsiTreeChangeListener> myTreeChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myTreeChangeEventIsFiring;

  private VirtualFileFilter myAssertOnFileLoadingFilter = VirtualFileFilter.NONE;

  private final AtomicInteger myBatchFilesProcessingModeCount = new AtomicInteger(0);

  @Topic.ProjectLevel
  public static final Topic<AnyPsiChangeListener> ANY_PSI_CHANGE_TOPIC = new Topic<>(AnyPsiChangeListener.class, Topic.BroadcastDirection.TO_PARENT);

  public PsiManagerImpl(@NotNull Project project) {
    // we need to initialize PsiBuilderFactory service, so it won't initialize under PsiLock from ChameleonTransform
    PsiBuilderFactory.getInstance();

    myProject = project;
    myFileIndex = NotNullLazyValue.createValue(() -> FileIndexFacade.getInstance(project));
    myModificationTracker = PsiModificationTracker.getInstance(project);

    myFileManager = new FileManagerImpl(this, myFileIndex);

    myTreeChangePreprocessors.add((PsiTreeChangePreprocessor)myModificationTracker);
  }

  @Override
  public void dispose() {
    myFileManager.dispose();
  }

  @Override
  public boolean isDisposed() {
    return myProject.isDisposed();
  }

  @Override
  public void dropResolveCaches() {
    myFileManager.processQueue();
    beforeChange(true);
  }

  @Override
  @RequiresEdt
  public void dropPsiCaches() {
    dropResolveCaches();
    ApplicationManager.getApplication().runWriteAction(myFileManager::firePropertyChangedForUnloadedPsi);
  }

  @Override
  public boolean isInProject(@NotNull PsiElement element) {
    if (element instanceof PsiDirectoryContainer) {
      PsiDirectory[] dirs = ((PsiDirectoryContainer)element).getDirectories();
      for (PsiDirectory dir : dirs) {
        if (!isInProject(dir)) return false;
      }
      return true;
    }

    PsiFile file = element.getContainingFile();
    VirtualFile virtualFile = null;
    if (file != null) {
      virtualFile = file.getViewProvider().getVirtualFile();
    }
    else if (element instanceof PsiFileSystemItem) {
      virtualFile = ((PsiFileSystemItem)element).getVirtualFile();
    }
    if (file != null && file.isPhysical() && virtualFile.getFileSystem() instanceof NonPhysicalFileSystem) return true;

    return virtualFile != null && myFileIndex.getValue().isInContent(virtualFile);
  }

  @Override
  public @Nullable FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile) {
    return myFileManager.findCachedViewProvider(vFile);
  }

  @Override
  @TestOnly
  public void setAssertOnFileLoadingFilter(@NotNull VirtualFileFilter filter, @NotNull Disposable parentDisposable) {
    // Find something to ensure there are no changed files waiting to be processed in repository indices.
    myAssertOnFileLoadingFilter = filter;
    Disposer.register(parentDisposable, () -> myAssertOnFileLoadingFilter = VirtualFileFilter.NONE);
  }

  @Override
  public boolean isAssertOnFileLoading(@NotNull VirtualFile file) {
    return myAssertOnFileLoadingFilter.accept(file);
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull FileManager getFileManager() {
    return myFileManager;
  }

  @Override
  public boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    if (element1 == element2) return true;
    if (element1 == null || element2 == null) {
      return false;
    }

    return element1.equals(element2) || element1.isEquivalentTo(element2) || element2.isEquivalentTo(element1);
  }

  @Override
  @RequiresReadLock
  public PsiFile findFile(@NotNull VirtualFile file) {
    ProgressIndicatorProvider.checkCanceled();
    return myFileManager.findFile(file);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable PsiFile findFile(@NotNull VirtualFile file, @NotNull CodeInsightContext context) {
    ProgressIndicatorProvider.checkCanceled();
    return myFileManager.findFile(file, context);
  }

  @Override
  public @NotNull FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    ProgressIndicatorProvider.checkCanceled();
    return myFileManager.findViewProvider(file);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull FileViewProvider findViewProvider(@NotNull VirtualFile file, @NotNull CodeInsightContext context) {
    ProgressIndicatorProvider.checkCanceled();
    return myFileManager.findViewProvider(file, context);
  }

  @Override
  public PsiDirectory findDirectory(@NotNull VirtualFile file) {
    ProgressIndicatorProvider.checkCanceled();
    return myFileManager.findDirectory(file);
  }

  @Override
  public void reloadFromDisk(@NotNull PsiFile file) {
    myFileManager.reloadFromDisk(file);
  }

  @Override
  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
    myTreeChangeListeners.add(listener);
  }

  @Override
  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener, @NotNull Disposable parentDisposable) {
    addPsiTreeChangeListener(listener);
    Disposer.register(parentDisposable, () -> removePsiTreeChangeListener(listener));
  }

  @Override
  public void removePsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
    myTreeChangeListeners.remove(listener);
  }

  private static @NonNls String logPsi(@Nullable PsiElement element) {
    return element == null ? "null" : element.getClass().getName();
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEventImpl event) {
    beforeChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_ADDITION);
    if (LOG.isDebugEnabled()) {
      LOG.debug("beforeChildAddition: event = " + event);
    }
    fireEvent(event);
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEventImpl event) {
    beforeChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_REMOVAL);
    if (LOG.isDebugEnabled()) {
      LOG.debug("beforeChildRemoval: child = " + logPsi(event.getChild()) + ", parent = " + logPsi(event.getParent()));
    }
    fireEvent(event);
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEventImpl event) {
    beforeChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_REPLACEMENT);
    if (LOG.isDebugEnabled()) {
      LOG.debug("beforeChildReplacement: oldChild = " + logPsi(event.getOldChild()));
    }
    fireEvent(event);
  }

  public void beforeChildrenChange(@NotNull PsiTreeChangeEventImpl event) {
    beforeChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILDREN_CHANGE);
    if (LOG.isDebugEnabled()) {
      LOG.debug("beforeChildrenChange: parent = " + logPsi(event.getParent()));
    }
    fireEvent(event);
  }

  public void beforeChildMovement(@NotNull PsiTreeChangeEventImpl event) {
    beforeChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_MOVEMENT);
    if (LOG.isDebugEnabled()) {
      LOG.debug("beforeChildMovement: child = " + logPsi(event.getChild()) + ", oldParent = " + logPsi(event.getOldParent()) + ", newParent = " + logPsi(event.getNewParent()));
    }
    fireEvent(event);
  }

  public void beforePropertyChange(@NotNull PsiTreeChangeEventImpl event) {
    beforeChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_PROPERTY_CHANGE);
    if (LOG.isDebugEnabled()) {
      LOG.debug("beforePropertyChange: element = " + logPsi(event.getElement()) + ", propertyName = " + event.getPropertyName() + ", oldValue = " +
                arrayToString(event.getOldValue()));
    }
    fireEvent(event);
  }

  private static Object arrayToString(Object value) {
    return value instanceof Object[] ? Arrays.deepToString((Object[])value) : value;
  }

  public void childAdded(@NotNull PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILD_ADDED);
    if (LOG.isDebugEnabled()) {
      LOG.debug("childAdded: child = " + logPsi(event.getChild()) + ", parent = " + logPsi(event.getParent()));
    }
    fireEvent(event);
    afterChange(true);
  }

  public void childRemoved(@NotNull PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILD_REMOVED);
    if (LOG.isDebugEnabled()) {
      LOG.debug("childRemoved: child = " + logPsi(event.getChild()) + ", parent = " + logPsi(event.getParent()));
    }
    fireEvent(event);
    afterChange(true);
  }

  public void childReplaced(@NotNull PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILD_REPLACED);
    if (LOG.isDebugEnabled()) {
      LOG.debug("childReplaced: oldChild = " + logPsi(event.getOldChild()) + ", newChild = " + logPsi(event.getNewChild()) + ", parent = " + logPsi(event.getParent()));
    }
    fireEvent(event);
    afterChange(true);
  }

  public void childMoved(@NotNull PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED);
    if (LOG.isDebugEnabled()) {
      LOG.debug("childMoved: child = " + logPsi(event.getChild()) + ", oldParent = " + logPsi(event.getOldParent()) + ", newParent = " + logPsi(event.getNewParent()));
    }
    fireEvent(event);
    afterChange(true);
  }

  public void childrenChanged(@NotNull PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILDREN_CHANGED);
    if (LOG.isDebugEnabled()) {
      LOG.debug("childrenChanged: parent = " + logPsi(event.getParent()));
    }
    fireEvent(event);
    afterChange(true);
  }

  public void propertyChanged(@NotNull PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.PROPERTY_CHANGED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "propertyChanged: element = " + logPsi(event.getElement())
        + ", propertyName = " + event.getPropertyName()
        + ", oldValue = " + arrayToString(event.getOldValue())
        + ", newValue = " + arrayToString(event.getNewValue())
      );
    }
    fireEvent(event);
    afterChange(true);
  }

  public void addTreeChangePreprocessor(@NotNull PsiTreeChangePreprocessor preprocessor) {
    myTreeChangePreprocessors.add(preprocessor);
  }

  public void removeTreeChangePreprocessor(@NotNull PsiTreeChangePreprocessor preprocessor) {
    myTreeChangePreprocessors.remove(preprocessor);
  }

  private void fireEvent(@NotNull PsiTreeChangeEventImpl event) {
    boolean isRealTreeChange = event.getCode() != PsiTreeChangeEventImpl.PsiEventType.PROPERTY_CHANGED
                               && event.getCode() != PsiTreeChangeEventImpl.PsiEventType.BEFORE_PROPERTY_CHANGE;

    PsiFile file = event.getFile();
    if (file == null || file.isPhysical()) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }
    if (isRealTreeChange) {
      LOG.assertTrue(!myTreeChangeEventIsFiring, "Changes to PSI are not allowed inside event processing");
      myTreeChangeEventIsFiring = true;
    }
    try {
      for (PsiTreeChangePreprocessor preprocessor : myTreeChangePreprocessors) {
        preprocessor.treeChanged(event);
      }
      for (PsiTreeChangePreprocessor preprocessor : PsiTreeChangePreprocessor.EP.getExtensions(myProject)) {
        try {
          preprocessor.treeChanged(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
      for (PsiTreeChangeListener listener : myTreeChangeListeners) {
        notifyPsiTreeChangeListener(event, listener);
      }
      for (PsiTreeChangeListener listener : PsiTreeChangeListener.EP.getExtensions(myProject)) {
        notifyPsiTreeChangeListener(event, listener);
      }
    }
    finally {
      if (isRealTreeChange) {
        myTreeChangeEventIsFiring = false;
      }
    }
  }

  private static void notifyPsiTreeChangeListener(@NotNull PsiTreeChangeEventImpl event, PsiTreeChangeListener listener) {
    try {
      switch (event.getCode()) {
        case BEFORE_CHILD_ADDITION:
          listener.beforeChildAddition(event);
          break;

        case BEFORE_CHILD_REMOVAL:
          listener.beforeChildRemoval(event);
          break;

        case BEFORE_CHILD_REPLACEMENT:
          listener.beforeChildReplacement(event);
          break;

        case BEFORE_CHILD_MOVEMENT:
          listener.beforeChildMovement(event);
          break;

        case BEFORE_CHILDREN_CHANGE:
          listener.beforeChildrenChange(event);
          break;

        case BEFORE_PROPERTY_CHANGE:
          listener.beforePropertyChange(event);
          break;

        case CHILD_ADDED:
          listener.childAdded(event);
          break;

        case CHILD_REMOVED:
          listener.childRemoved(event);
          break;

        case CHILD_REPLACED:
          listener.childReplaced(event);
          break;

        case CHILD_MOVED:
          listener.childMoved(event);
          break;

        case CHILDREN_CHANGED:
          listener.childrenChanged(event);
          break;

        case PROPERTY_CHANGED:
          listener.propertyChanged(event);
          break;
      }
    }
    catch (Throwable e) {
      if (e instanceof ControlFlowException) {
        LOG.warn(e);
      }
      else {
        LOG.error(e);
      }
    }
  }

  @Override
  public void beforeChange(boolean isPhysical) {
    myProject.getMessageBus().syncPublisher(ANY_PSI_CHANGE_TOPIC).beforePsiChanged(isPhysical);
  }

  @Override
  public void afterChange(boolean isPhysical) {
    myProject.getMessageBus().syncPublisher(ANY_PSI_CHANGE_TOPIC).afterPsiChanged(isPhysical);
  }

  @Override
  public @NotNull PsiModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  @Override
  public void startBatchFilesProcessingMode() {
    myBatchFilesProcessingModeCount.incrementAndGet();
  }

  @Override
  public void finishBatchFilesProcessingMode() {
    int after = myBatchFilesProcessingModeCount.decrementAndGet();
    LOG.assertTrue(after >= 0);
  }

  @Override
  public <T> T runInBatchFilesMode(@NotNull Computable<T> runnable) {
    startBatchFilesProcessingMode();
    try {
      return runnable.compute();
    }
    finally {
      finishBatchFilesProcessingMode();
    }
  }

  @Override
  public boolean isBatchFilesProcessingMode() {
    return myBatchFilesProcessingModeCount.get() > 0;
  }

  @TestOnly
  public void cleanupForNextTest() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myFileManager.cleanupForNextTest();
    dropPsiCaches();
  }

  public void dropResolveCacheRegularly(@NotNull ProgressIndicator indicator) {
    indicator = ProgressWrapper.unwrap(indicator);
    if (indicator instanceof ProgressIndicatorEx) {
      ((ProgressIndicatorEx)indicator).addStateDelegate(new AbstractProgressIndicatorExBase() {
        private final AtomicLong lastClearedTimeStamp = new AtomicLong();

        @Override
        public void setFraction(double fraction) {
          long current = System.currentTimeMillis();
          long last = lastClearedTimeStamp.get();
          if (current - last >= 500 && lastClearedTimeStamp.compareAndSet(last, current)) {
            // fraction is changed when each file is processed =>
            // resolve caches used when searching in that file are likely to be not needed anymore
            dropResolveCaches();
          }
        }
      });
    }
  }
}
