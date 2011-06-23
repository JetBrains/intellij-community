/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl;

import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormatterImpl;
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.psi.impl.cache.impl.CompositeCacheManager;
import com.intellij.psi.impl.cache.impl.IndexCacheManagerImpl;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PsiManagerImpl extends PsiManagerEx implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiManagerImpl");

  private final Project myProject;
  private final MessageBus myMessageBus;

  private final FileManager myFileManager;
  private final PsiSearchHelperImpl mySearchHelper;
  private final CacheManager myCacheManager;
  private final PsiModificationTrackerImpl myModificationTracker;
  private final ResolveCache myResolveCache;
  private final NotNullLazyValue<CachedValuesManager> myCachedValuesManager = new NotNullLazyValue<CachedValuesManager>() {
    @NotNull
    @Override
    protected CachedValuesManager compute() {
      return CachedValuesManager.getManager(myProject);
    }
  };

  private final List<PsiTreeChangePreprocessor> myTreeChangePreprocessors = ContainerUtil.createEmptyCOWList();
  private final List<PsiTreeChangeListener> myTreeChangeListeners = ContainerUtil.createEmptyCOWList();
  private boolean myTreeChangeEventIsFiring = false;

  private boolean myIsDisposed;

  private VirtualFileFilter myAssertOnFileLoadingFilter = VirtualFileFilter.NONE;

  private final AtomicInteger myBatchFilesProcessingModeCount = new AtomicInteger(0);

  private static final Key<PsiFile> CACHED_PSI_FILE_COPY_IN_FILECONTENT = Key.create("CACHED_PSI_FILE_COPY_IN_FILECONTENT");
  public static final Topic<AnyPsiChangeListener> ANY_PSI_CHANGE_TOPIC = Topic.create("PSI_CHANGE_TOPIC",AnyPsiChangeListener.class, Topic.BroadcastDirection.TO_PARENT);

  private final List<LanguageInjector> myLanguageInjectors = ContainerUtil.createEmptyCOWList();

  public PsiManagerImpl(Project project,
                        final ProjectRootManagerEx projectRootManagerEx,
                        StartupManager startupManager,
                        FileTypeManager fileTypeManager,
                        FileDocumentManager fileDocumentManager,
                        PsiBuilderFactory psiBuilderFactory,
                        MessageBus messageBus) {
    myProject = project;
    myMessageBus = messageBus;

    //We need to initialize PsiBuilderFactory service so it won't initialize under PsiLock from ChameleonTransform
    @SuppressWarnings({"UnusedDeclaration", "UnnecessaryLocalVariable"}) Object used = psiBuilderFactory;

    boolean isProjectDefault = project.isDefault();

    myFileManager = isProjectDefault ? new EmptyFileManager(this) : new FileManagerImpl(this, fileTypeManager, fileDocumentManager,
                                                                                                    projectRootManagerEx);
    mySearchHelper = new PsiSearchHelperImpl(this);
    final CompositeCacheManager cacheManager = new CompositeCacheManager();
    if (isProjectDefault) {
      cacheManager.addCacheManager(new EmptyCacheManager());
    }
    else {
      cacheManager.addCacheManager(new IndexCacheManagerImpl(this));
    }
    final CacheManager[] managers = myProject.getComponents(CacheManager.class);
    for (CacheManager manager : managers) {
      cacheManager.addCacheManager(manager);
    }

    myCacheManager = cacheManager;

    myModificationTracker = new PsiModificationTrackerImpl(myProject);
    myTreeChangePreprocessors.add(myModificationTracker);
    myResolveCache = new ResolveCache(this);

    if (startupManager != null) {
      startupManager.registerPreStartupActivity(
        new Runnable() {
          public void run() {
            runPreStartupActivity();
          }
        }
      );
    }
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myFileManager.dispose();
    myCacheManager.dispose();

    myIsDisposed = true;
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  public void dropResolveCaches() {
    myResolveCache.clearCache();
    ((FileManagerImpl)myFileManager).processQueue();
    physicalChange();
    nonPhysicalChange();
  }

  @Override
  public void dropFileCaches(@NotNull PsiFile file) {
    InjectedLanguageUtil.clearCachedInjectedFragmentsForFile(file);
  }

  public boolean isInProject(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file instanceof PsiFileImpl && file.isPhysical() && file.getViewProvider().getVirtualFile() instanceof LightVirtualFile) return true;

    if (element instanceof PsiDirectoryContainer) {
      PsiDirectory[] dirs = ((PsiDirectoryContainer) element).getDirectories();
      for (PsiDirectory dir : dirs) {
        if (!isInProject(dir)) return false;
      }
      return true;
    }

    VirtualFile virtualFile = null;
    if (file != null) {
      virtualFile = file.getViewProvider().getVirtualFile();
    }
    else if (element instanceof PsiFileSystemItem) {
      virtualFile = ((PsiFileSystemItem)element).getVirtualFile();
    }

    if (virtualFile != null) {
      Module module = ModuleUtil.findModuleForFile(virtualFile, element.getProject());
      return module != null;
    }
    return false;
  }

  public void performActionWithFormatterDisabled(final Runnable r) {
    performActionWithFormatterDisabled(new Computable<Object>() {
      @Override
      public Object compute() {
        r.run();
        return null;
      }
    });
  }

  public <T extends Throwable> void performActionWithFormatterDisabled(final ThrowableRunnable<T> r) throws T {
    final Throwable[] throwable = new Throwable[1];

    performActionWithFormatterDisabled(new Computable<Object>() {
      @Override
      public Object compute() {
        try {
          r.run();
        }
        catch (Throwable t) {
          throwable[0] = t;
        }
        return null;
      }
    });

    if (throwable[0] != null) {
      //noinspection unchecked
      throw (T)throwable[0];
    }
  }

  public <T> T performActionWithFormatterDisabled(final Computable<T> r) {
    return ((FormatterImpl)FormatterEx.getInstance()).runWithFormattingDisabled(new Computable<T>() {
      @Override
      public T compute() {
        final PostprocessReformattingAspect component = PostprocessReformattingAspect.getInstance(getProject());
        return component.disablePostprocessFormattingInside(r);
      }
    });
  }

  @NotNull
  public List<? extends LanguageInjector> getLanguageInjectors() {
    return myLanguageInjectors;
  }

  public void registerLanguageInjector(@NotNull LanguageInjector injector) {
    myLanguageInjectors.add(injector);
    InjectedLanguageManagerImpl.getInstanceImpl(myProject).psiManagerInjectorsChanged();
  }

  public void registerLanguageInjector(@NotNull final LanguageInjector injector, Disposable parentDisposable) {
    registerLanguageInjector(injector);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        unregisterLanguageInjector(injector);
      }
    });
  }

  public void unregisterLanguageInjector(@NotNull LanguageInjector injector) {
    myLanguageInjectors.remove(injector);
    InjectedLanguageManagerImpl.getInstanceImpl(myProject).psiManagerInjectorsChanged();
  }

  public void postponeAutoFormattingInside(Runnable runnable) {
    PostprocessReformattingAspect.getInstance(getProject()).postponeFormattingInside(runnable);
  }


  public void projectClosed() {
  }

  public void projectOpened() {
  }

  private void runPreStartupActivity() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("PsiManager.runPreStartupActivity()");
    }
    myFileManager.runStartupActivity();

    myCacheManager.initialize();

    StartupManagerEx startupManager = StartupManagerEx.getInstanceEx(myProject);
    if (startupManager != null) {
      CacheUpdater[] updaters = myCacheManager.getCacheUpdaters();
      for (CacheUpdater updater : updaters) {
        startupManager.registerCacheUpdater(updater);
      }
    }
  }

  public void setAssertOnFileLoadingFilter(VirtualFileFilter filter) {
    // Find something to ensure there's no changed files waiting to be processed in repository indices.
    myAssertOnFileLoadingFilter = filter;
  }

  public boolean isAssertOnFileLoading(@NotNull VirtualFile file) {
    return myAssertOnFileLoadingFilter.accept(file);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public FileManager getFileManager() {
    return myFileManager;
  }

  @NotNull
  public CacheManager getCacheManager() {
    if (myIsDisposed) {
      LOG.error("Project is already disposed.");
    }
    return myCacheManager;
  }

  @NotNull
  public CodeStyleManager getCodeStyleManager() {
    return CodeStyleManager.getInstance(myProject);
  }

  @NotNull
  public ResolveCache getResolveCache() {
    ProgressManager.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly
    return myResolveCache;
  }


  public boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    ProgressManager.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    if (element1 == element2) return true;
    if (element1 == null || element2 == null) {
      return false;
    }

    return element1.equals(element2) || element1.isEquivalentTo(element2) || element2.isEquivalentTo(element1);
  }

  public PsiFile findFile(@NotNull VirtualFile file) {
    return myFileManager.findFile(file);
  }

  @Nullable
  public FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    return myFileManager.findViewProvider(file);
  }

  @TestOnly
  public void cleanupForNextTest() {
    myFileManager.cleanupForNextTest();
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
  }

  @Nullable
  public PsiFile getFile(FileContent content) {
    PsiFile psiFile = content.getUserData(CACHED_PSI_FILE_COPY_IN_FILECONTENT);
    if (psiFile == null) {
      final VirtualFile vFile = content.getVirtualFile();
      psiFile = myFileManager.getCachedPsiFile(vFile);
      if (psiFile == null) {
        psiFile = findFile(vFile);
        if (psiFile == null) return null;
        psiFile = CacheUtil.createFileCopy(content, psiFile);
      }
      //psiFile = content.putUserDataIfAbsent(CACHED_PSI_FILE_COPY_IN_FILECONTENT, psiFile);
      content.putUserData(CACHED_PSI_FILE_COPY_IN_FILECONTENT, psiFile);
    }

    LOG.assertTrue(psiFile instanceof PsiCompiledElement || psiFile.isValid());
    return psiFile;
  }

  public PsiDirectory findDirectory(@NotNull VirtualFile file) {
    ProgressManager.checkCanceled();

    return myFileManager.findDirectory(file);
  }


  public void invalidateFile(@NotNull PsiFile file) {
    if (myIsDisposed) {
      LOG.error("Disposed PsiManager calls invalidateFile!");
    }

    final VirtualFile virtualFile = file.getVirtualFile();
    if (file.getViewProvider().isPhysical() && myCacheManager != null) {
      myCacheManager.addOrInvalidateFile(virtualFile);
    }
  }

  public void reloadFromDisk(@NotNull PsiFile file) {
    myFileManager.reloadFromDisk(file);
  }

  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
    myTreeChangeListeners.add(listener);
  }

  public void addPsiTreeChangeListener(@NotNull final PsiTreeChangeListener listener, Disposable parentDisposable) {
    addPsiTreeChangeListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removePsiTreeChangeListener(listener);
      }
    });
  }

  public void removePsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
    myTreeChangeListeners.remove(listener);
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEventImpl event) {
    beforeAnyChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_ADDITION);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforeChildAddition: parent = " + event.getParent()
      );
    }
    fireEvent(event);
  }

  public void beforeChildRemoval(@NotNull PsiTreeChangeEventImpl event) {
    beforeAnyChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_REMOVAL);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforeChildRemoval: child = " + event.getChild()
        + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
  }

  public void beforeChildReplacement(@NotNull PsiTreeChangeEventImpl event) {
    beforeAnyChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_REPLACEMENT);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforeChildReplacement: oldChild = " + event.getOldChild()
        + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
  }

  public void beforeChildrenChange(PsiTreeChangeEventImpl event) {
    beforeAnyChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILDREN_CHANGE);
    if (LOG.isDebugEnabled()) {
      LOG.debug("beforeChildrenChange: parent = " + event.getParent());
    }
    fireEvent(event);
  }

  public void beforeChildMovement(PsiTreeChangeEventImpl event) {
    beforeAnyChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_CHILD_MOVEMENT);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforeChildMovement: child = " + event.getChild()
        + ", oldParent = " + event.getOldParent()
        + ", newParent = " + event.getNewParent()
      );
    }
    fireEvent(event);
  }

  public void beforePropertyChange(PsiTreeChangeEventImpl event) {
    beforeAnyChange(true);
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.BEFORE_PROPERTY_CHANGE);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforePropertyChange: element = " + event.getElement()
        + ", propertyName = " + event.getPropertyName()
        + ", oldValue = " + event.getOldValue()
      );
    }
    fireEvent(event);
  }

  public void childAdded(PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILD_ADDED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childAdded: child = " + event.getChild()
        + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
    afterAnyChange(true);
  }

  public void childRemoved(PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILD_REMOVED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childRemoved: child = " + event.getChild() + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
    afterAnyChange(true);
  }

  public void childReplaced(PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILD_REPLACED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childReplaced: oldChild = " + event.getOldChild()
        + ", newChild = " + event.getNewChild()
        + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
    afterAnyChange(true);
  }

  public void childMoved(PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childMoved: child = " + event.getChild()
        + ", oldParent = " + event.getOldParent()
        + ", newParent = " + event.getNewParent()
      );
    }
    fireEvent(event);
    afterAnyChange(true);
  }

  public void childrenChanged(PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.CHILDREN_CHANGED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childrenChanged: parent = " + event.getParent()
      );
    }
    fireEvent(event);
    afterAnyChange(true);
  }

  public void propertyChanged(PsiTreeChangeEventImpl event) {
    event.setCode(PsiTreeChangeEventImpl.PsiEventType.PROPERTY_CHANGED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "propertyChanged: element = " + event.getElement()
        + ", propertyName = " + event.getPropertyName()
        + ", oldValue = " + event.getOldValue()
        + ", newValue = " + event.getNewValue()
      );
    }
    fireEvent(event);
    afterAnyChange(true);
  }

  public void addTreeChangePreprocessor(PsiTreeChangePreprocessor preprocessor) {
    myTreeChangePreprocessors.add(preprocessor);
  }

  private void fireEvent(PsiTreeChangeEventImpl event) {
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
      for(PsiTreeChangePreprocessor preprocessor: myTreeChangePreprocessors) {
        preprocessor.treeChanged(event);
      }

      for (PsiTreeChangeListener listener : myTreeChangeListeners) {
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
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    finally {
      if (isRealTreeChange) {
        myTreeChangeEventIsFiring = false;
      }
    }
  }

  public void registerRunnableToRunOnChange(@NotNull final Runnable runnable) {
    myMessageBus.connect().subscribe(ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        if (isPhysical) runnable.run();
      }

      @Override
      public void afterPsiChanged(boolean isPhysical) {
      }
    });
  }

  public void registerRunnableToRunOnAnyChange(@NotNull final Runnable runnable) { // includes non-physical changes
    myMessageBus.connect().subscribe(ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        runnable.run();
      }

      @Override
      public void afterPsiChanged(boolean isPhysical) {
      }
    });
  }

  public void registerRunnableToRunAfterAnyChange(@NotNull final Runnable runnable) { // includes non-physical changes
    myMessageBus.connect().subscribe(ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
      }

      @Override
      public void afterPsiChanged(boolean isPhysical) {
        runnable.run();
      }
    });
  }

  public void nonPhysicalChange() {
    beforeAnyChange(false);
  }

  public void physicalChange() {
    beforeAnyChange(true);
  }

  private void beforeAnyChange(boolean isPhysical) {
    myMessageBus.syncPublisher(ANY_PSI_CHANGE_TOPIC).beforePsiChanged(isPhysical);
  }

  private void afterAnyChange(boolean isPhysical) {
    myMessageBus.syncPublisher(ANY_PSI_CHANGE_TOPIC).afterPsiChanged(isPhysical);
  }

  @NotNull
  public PsiSearchHelper getSearchHelper() {
    return mySearchHelper;
  }

  @NotNull
  public PsiModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  @NotNull
  public CachedValuesManager getCachedValuesManager() {
    return myCachedValuesManager.getValue();
  }

  public void moveDirectory(@NotNull final PsiDirectory dir, @NotNull PsiDirectory newParent) throws IncorrectOperationException {
    checkMove(dir, newParent);

    try {
      dir.getVirtualFile().move(this, newParent.getVirtualFile());
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(),e);
    }
  }

  public void moveFile(@NotNull final PsiFile file, @NotNull PsiDirectory newParent) throws IncorrectOperationException {
    checkMove(file, newParent);

    try {
      final VirtualFile virtualFile = file.getVirtualFile();
      assert virtualFile != null;
      virtualFile.move(this, newParent.getVirtualFile());
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(),e);
    }
  }

  public void checkMove(@NotNull PsiElement element, @NotNull PsiElement newContainer) throws IncorrectOperationException {
    if (element instanceof PsiDirectoryContainer) {
      PsiDirectory[] dirs = ((PsiDirectoryContainer)element).getDirectories();
      if (dirs.length == 0) {
        throw new IncorrectOperationException();
      }
      else if (dirs.length > 1) {
        throw new IncorrectOperationException(
          "Moving of packages represented by more than one physical directory is not supported.");
      }
      checkMove(dirs[0], newContainer);
      return;
    }

    //element.checkDelete(); //move != delete + add
    newContainer.checkAdd(element);
    MoveFilesOrDirectoriesUtil.checkIfMoveIntoSelf(element, newContainer);
  }

  public void startBatchFilesProcessingMode() {
    myBatchFilesProcessingModeCount.incrementAndGet();
  }

  public void finishBatchFilesProcessingMode() {
    myBatchFilesProcessingModeCount.decrementAndGet();
    LOG.assertTrue(myBatchFilesProcessingModeCount.get() >= 0);
  }

  public boolean isBatchFilesProcessingMode() {
    return myBatchFilesProcessingModeCount.get() > 0;
  }

  @NotNull
  public String getComponentName() {
    return "PsiManager";
  }
}
