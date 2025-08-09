// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl;

import com.intellij.codeInsight.multiverse.*;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.StackOverflowPreventedException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class FileManagerImpl implements FileManagerEx {
  private static final Key<Boolean> IN_COMA = Key.create("IN_COMA");
  private static final Logger LOG = Logger.getInstance(FileManagerImpl.class);
  private final Key<FileViewProvider> myPsiHardRefKey = Key.create("HARD_REFERENCE_TO_PSI"); //non-static!

  private final PsiManagerImpl myManager;
  private final NotNullLazyValue<? extends FileIndexFacade> myFileIndex;

  private final AtomicReference<ConcurrentMap<VirtualFile, PsiDirectory>> myVFileToPsiDirMap = new AtomicReference<>();
  private final FileViewProviderCache myVFileToViewProviderMap;

  /**
   * Holds thread-local temporary providers that are sometimes needed while checking if a file is valid
   */
  private final TemporaryProviderStorage myTempProviders;

  private final MessageBusConnection myConnection;

  public FileManagerImpl(@NotNull PsiManagerImpl manager, @NotNull NotNullLazyValue<? extends FileIndexFacade> fileIndex) {
    myManager = manager;
    myFileIndex = fileIndex;

    myVFileToViewProviderMap = CodeInsightContexts.isSharedSourceSupportEnabled(manager.getProject())
                               ? new MultiverseFileViewProviderCache()
                               : new ClassicFileViewProviderCache();

    myTempProviders = CodeInsightContexts.isSharedSourceSupportEnabled(manager.getProject())
                      ? new ClassicTemporaryProviderStorage()
                      : new MultiverseTemporaryProviderStorage();

    myConnection = manager.getProject().getMessageBus().connect(manager);

    LowMemoryWatcher.register(this::processQueue, manager);

    myConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        processFileTypesChanged(false);
      }

      @Override
      public void exitDumbMode() {
        processFileTypesChanged(false);
      }
    });
  }

  @Override
  public void processQueue() {
    myVFileToViewProviderMap.processQueue();
  }

  private @NotNull ConcurrentMap<VirtualFile, PsiDirectory> getVFileToPsiDirMap() {
    ConcurrentMap<VirtualFile, PsiDirectory> map = myVFileToPsiDirMap.get();
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myVFileToPsiDirMap, ContainerUtil.createConcurrentSoftValueMap());
    }
    return map;
  }

  @TestOnly
  @Override
  public void assertNoInjectedFragmentsStoredInMaps() {
    myVFileToViewProviderMap.forEach((file, __, provider) -> {
      if (file instanceof VirtualFileWindow) {
        throw new AssertionError(file);
      }
      // todo IJPL-339 investigate what happens with injection hosts here
      PsiLanguageInjectionHost injectionHost = InjectedLanguageManager.getInstance(myManager.getProject()).getInjectionHost(provider);
      if (injectionHost != null) {
        throw new AssertionError(injectionHost);
      }
    });
  }

  /**
   * Updates the context of [viewProvider] to [context] if the current context of viewProvider is anyContext.
   *
   * @return updated context of viewProvider, or `null` if viewProvider is missing in the cache.
   */
  @ApiStatus.Internal
  @Override
  public @Nullable CodeInsightContext trySetContext(@NotNull FileViewProvider viewProvider, @NotNull CodeInsightContext context) {
    VirtualFile vFile = viewProvider.getVirtualFile();
    if (vFile instanceof LightVirtualFile) {
      installContext(viewProvider, context);
      return context;
    }

    return myVFileToViewProviderMap.trySetContext(viewProvider, context);
  }

  public static void clearPsiCaches(@NotNull FileViewProvider viewProvider) {
    ((AbstractFileViewProvider)viewProvider).getCachedPsiFiles().forEach(PsiFile::clearCaches);
  }

  @Override
  public void forceReload(@NotNull VirtualFile vFile) {
    LanguageSubstitutors.cancelReparsing(vFile);
    List<FileViewProvider> viewProviders = findCachedViewProviders(vFile);
    if (viewProviders.isEmpty()) {
      return;
    }
    if (!CodeInsightContextUtil.isEventSystemEnabled(viewProviders)) {
      setViewProvider(vFile, null);
      return;
    }

    // write access is necessary only when the event system is enabled for the file.
    ThreadingAssertions.assertWriteAccess();

    VirtualFile dir = vFile.getParent();
    PsiDirectory parentDir = dir == null ? null : getCachedDirectory(dir);
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    if (parentDir == null) {
      event.setPropertyName(PsiTreeChangeEvent.PROP_UNLOADED_PSI);

      myManager.beforePropertyChange(event);
      setViewProvider(vFile, null);
      myManager.propertyChanged(event);
    } else {
      event.setParent(parentDir);

      myManager.beforeChildrenChange(event);
      setViewProvider(vFile, null);
      myManager.childrenChanged(event);
    }
  }

  @Override
  public void firePropertyChangedForUnloadedPsi() {
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setPropertyName(PsiTreeChangeEvent.PROP_UNLOADED_PSI);

    myManager.beforePropertyChange(event);
    myManager.propertyChanged(event);
  }

  @Override
  public void dispose() {
    clearViewProviders();
  }

  @RequiresWriteLock
  private void clearViewProviders() {
    DebugUtil.performPsiModification("clearViewProviders", () -> {
      myVFileToViewProviderMap.forEach((__, ___, provider) -> {
        markInvalidated(provider);
      });
      myVFileToViewProviderMap.clear();
    });
  }

  @Override
  @TestOnly
  public void cleanupForNextTest() {
    ApplicationManager.getApplication().runWriteAction(this::clearViewProviders);

    myVFileToPsiDirMap.set(null);
    myManager.dropPsiCaches();
  }

  @Override
  public @NotNull FileViewProvider findViewProvider(@NotNull VirtualFile vFile) {
    return findViewProvider(vFile, CodeInsightContexts.anyContext());
  }

  @Override
  public @NotNull FileViewProvider findViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    assert !vFile.isDirectory();
    FileViewProvider cachedViewProvider = findCachedViewProvider(vFile, context);
    if (cachedViewProvider != null) return cachedViewProvider;
    if (vFile instanceof VirtualFileWindow) {
      throw new IllegalStateException("File " + vFile + " is invalid");
    }

    if (myTempProviders.contains(vFile, context)) {
      return Objects.requireNonNull(myTempProviders.get(vFile, context), "Recursive file view provider creation");
    }

    FileViewProvider viewProvider = createFileViewProvider(vFile, context, !LightVirtualFile.shouldSkipEventSystem(vFile));
    if (vFile instanceof LightVirtualFile) {
      checkLightFileHasNoOtherPsi((LightVirtualFile)vFile);
      return vFile.putUserDataIfAbsent(myPsiHardRefKey, viewProvider);
    }
    return myVFileToViewProviderMap.cacheOrGet(vFile, context, viewProvider);
  }

  @Override
  public @NotNull List<FileViewProvider> findCachedViewProviders(@NotNull VirtualFile vFile) {
    List<FileViewProvider> providers = getRawCachedViewProviders(vFile);
    return mapNotNull(providers, viewProvider -> {
      return reanimateProviderIfNecessary(vFile, viewProvider);
    });
  }

  private void checkLightFileHasNoOtherPsi(@NotNull LightVirtualFile vFile) {
    FileViewProvider viewProvider = FileDocumentManager.getInstance().findCachedPsiInAnyProject(vFile);
    if (viewProvider != null) {
      Project project = viewProvider.getManager().getProject();
      if (project != myManager.getProject()) {
        String psiFiles = viewProvider.getAllFiles().stream().map(f -> f.getClass() + " [" + f.getLanguage() + "]").collect(Collectors.joining(", "));
        LOG.error(
          "Light files should have PSI only in one project, existing=" + viewProvider + " in " + project + ", requested in " + myManager.getProject()
          + "; psiFiles: " + psiFiles);
      }
    }
  }

  @Override
  public @Nullable FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile) {
    return findCachedViewProvider(vFile, CodeInsightContexts.anyContext());
  }

  @Override
  public FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    FileViewProvider viewProvider = getRawCachedViewProvider(vFile, context);
    return reanimateProviderIfNecessary(vFile, viewProvider);
  }

  private @Nullable FileViewProvider reanimateProviderIfNecessary(@NotNull VirtualFile vFile,
                                                                  @Nullable FileViewProvider viewProvider) {
    if (viewProvider instanceof AbstractFileViewProvider && viewProvider.getUserData(IN_COMA) != null) {
      CodeInsightContext context = getRawContext(viewProvider);
      FileViewProvider temp = myTempProviders.get(vFile, context);
      if (temp != null) {
        return temp;
      }

      if (!evaluateValidity((AbstractFileViewProvider)viewProvider)) {
        return null;
      }
    }
    return viewProvider;
  }

  private @Nullable FileViewProvider getRawCachedViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    FileViewProvider viewProvider = myVFileToViewProviderMap.get(vFile, context);
    return viewProvider == null ? vFile.getUserData(myPsiHardRefKey) : viewProvider;
  }

  /**
   * @return associated psi file, it's it cached in {@link #myVFileToViewProviderMap}
   * It tries to not perform any expensive ops like creating files/reparse/resurrecting PsiFile from temp comatose state.
   */
  @ApiStatus.Internal
  @Override
  public @Nullable PsiFile getRawCachedFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    FileViewProvider viewProvider = getRawCachedViewProvider(vFile, context);
    return viewProvider == null ? null :
           viewProvider instanceof AbstractFileViewProvider ? ((AbstractFileViewProvider)viewProvider).getCachedPsi(viewProvider.getBaseLanguage())
                                                            : viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  private @NotNull @Unmodifiable List<FileViewProvider> getRawCachedViewProviders(@NotNull VirtualFile vFile) {
    List<FileViewProvider> providers = myVFileToViewProviderMap.getAllProviders(vFile);
    if (!providers.isEmpty()) {
      if (providers.size() == 1) {
        return Collections.singletonList(providers.get(0));
      }
      else {
        return new ArrayList<>(providers);
      }
    }

    FileViewProvider provider = vFile.getUserData(myPsiHardRefKey);
    return ContainerUtil.createMaybeSingletonList(provider);
  }

  @Override
  public void setViewProvider(@NotNull VirtualFile vFile, @Nullable FileViewProvider viewProvider) {
    // todo IJPL-339 investigate if we need a context here
    if (viewProvider == null) {
      // Let's drop all providers.
      // Please add a new method if you need to drop only a single provider. But this seems to be a suspicious idea,
      // because shouldn't you drop other providers as well?
      dropAllProviders(vFile);
    }
    else {
      changeFileProvider(vFile, viewProvider);
    }
  }

  private void changeFileProvider(@NotNull VirtualFile vFile,
                                  @NotNull FileViewProvider viewProvider) {

    if (vFile instanceof LightVirtualFile) {
      FileViewProvider prev = getRawCachedViewProvider(vFile, CodeInsightContexts.anyContext());
      if (prev == viewProvider) return;

      if (prev != null) {
        DebugUtil.performPsiModification(null, () -> markInvalidated(prev));
      }

      checkLightFileHasNoOtherPsi((LightVirtualFile)vFile);
      vFile.putUserData(myPsiHardRefKey, viewProvider);
    }
    else {
      ThreadingAssertions.assertWriteAccess();
      List<FileViewProvider> prevProviders = getRawCachedViewProviders(vFile);
      if (prevProviders.size() == 1 && prevProviders.get(0) == viewProvider) {
        return;
      }

      DebugUtil.performPsiModification(null, () -> {
        for (FileViewProvider prevProvider : prevProviders) {
          markInvalidated(prevProvider);
        }
      });

      myVFileToViewProviderMap.removeAllFileViewProvidersAndSet(vFile, viewProvider);
    }
  }

  private void dropAllProviders(@NotNull VirtualFile vFile) {
    if (vFile instanceof LightVirtualFile) {
      FileViewProvider oldProvider = vFile.getUserData(myPsiHardRefKey);
      if (oldProvider != null) {
        DebugUtil.performPsiModification(null, () -> markInvalidated(oldProvider));
      }
      vFile.putUserData(myPsiHardRefKey, null);
    }
    else {
      Iterable<FileViewProvider> map = myVFileToViewProviderMap.remove(vFile);
      if (map != null) {
        for (FileViewProvider oldProvider : map) {
          DebugUtil.performPsiModification(null, () -> markInvalidated(oldProvider));
        }
      }
    }
  }

  @NotNull
  @Override
  public FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile, boolean eventSystemEnabled) {
    return createFileViewProvider(vFile, CodeInsightContexts.anyContext(), eventSystemEnabled);
  }

  @Override
  public @NotNull FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile,
                                                          @NotNull CodeInsightContext context,
                                                          boolean eventSystemEnabled) {
    FileType fileType = vFile.getFileType();
    Language language = LanguageUtil.getLanguageForPsi(myManager.getProject(), vFile, fileType);
    FileViewProviderFactory factory = language == null
                                      ? FileTypeFileViewProviders.INSTANCE.forFileType(fileType)
                                      : LanguageFileViewProviders.INSTANCE.forLanguage(language);

    FileViewProvider viewProvider = factory != null
                                    ? factory.createFileViewProvider(vFile, language, myManager, eventSystemEnabled)
                                    : new SingleRootFileViewProvider(myManager, vFile, eventSystemEnabled, fileType);

    installContext(viewProvider, context);

    return viewProvider;
  }

  private void installContext(@NotNull FileViewProvider viewProvider, @NotNull CodeInsightContext context) {
    if (!CodeInsightContexts.isSharedSourceSupportEnabled(myManager.getProject())) {
      return;
    }

    CodeInsightContextManagerImpl codeInsightContextManager =
      (CodeInsightContextManagerImpl)CodeInsightContextManager.getInstance(myManager.getProject());

    codeInsightContextManager.setCodeInsightContext(viewProvider, context);
  }

  private boolean myProcessingFileTypesChange;

  @ApiStatus.Internal
  @Override
  public void processFileTypesChanged(boolean clearViewProviders) {
    if (myProcessingFileTypesChange) return;
    myProcessingFileTypesChange = true;
    DebugUtil.performPsiModification(null, () -> {
      try {
        ApplicationManager.getApplication().runWriteAction(() -> {
          PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
          event.setPropertyName(PsiTreeChangeEvent.PROP_FILE_TYPES);
          myManager.beforePropertyChange(event);

          possiblyInvalidatePhysicalPsi();
          if (clearViewProviders) {
            clearViewProviders();
          }

          myManager.propertyChanged(event);
        });
      }
      finally {
        myProcessingFileTypesChange = false;
      }
    });
  }

  @RequiresWriteLock
  @ApiStatus.Internal
  @Override
  public void possiblyInvalidatePhysicalPsi() {
    removeInvalidDirs();
    myVFileToViewProviderMap.forEach((__, ___, viewProvider) -> {
      markPossiblyInvalidated(viewProvider);
    });
  }

  @ApiStatus.Internal
  @Override
  public void dispatchPendingEvents() {
    Project project = myManager.getProject();
    if (project.isDisposed()) {
      LOG.error("Project is already disposed: " + project);
    }
    myConnection.deliverImmediately();
  }

  @TestOnly
  @Override
  public void checkConsistency() {
    removePossiblyInvalidated();

    List<Entry> values = myVFileToViewProviderMap.getAllEntries();
    myVFileToViewProviderMap.clear();
    for (Entry entry : values) {
      VirtualFile vFile = entry.getFile();
      CodeInsightContext context = entry.getContext();
      FileViewProvider viewProvider = entry.getProvider();
      LOG.assertTrue(vFile.isValid());
      PsiFile psiFile1 = findFile(vFile, context);
      if (psiFile1 != null && viewProvider.isPhysical()) {
        PsiFile psi = viewProvider.getPsi(viewProvider.getBaseLanguage());
        assert psi != null : viewProvider +"; "+viewProvider.getBaseLanguage()+"; "+psiFile1;
        assert psiFile1.getClass().equals(psi.getClass()) : psiFile1 +"; "+psi + "; "+psiFile1.getClass() +"; "+psi.getClass();
      }
    }

    Map<VirtualFile, PsiDirectory> fileToPsiDirMap = new HashMap<>(getVFileToPsiDirMap());
    myVFileToPsiDirMap.set(null);

    for (VirtualFile vFile : fileToPsiDirMap.keySet()) {
      LOG.assertTrue(vFile.isValid());
      PsiDirectory psiDir1 = findDirectory(vFile);
      LOG.assertTrue(psiDir1 != null);

      VirtualFile parent = vFile.getParent();
      if (parent != null) {
        LOG.assertTrue(getVFileToPsiDirMap().get(parent) != null);
      }
    }
  }

  @TestOnly
  private void removePossiblyInvalidated() {
    List<Entry> valuesBefore = myVFileToViewProviderMap.getAllEntries();
    for (Entry entry : valuesBefore) {
      findCachedViewProvider(entry.getFile(), entry.getContext()); // complete delayed validity checks
    }
  }

  @Override
  @RequiresReadLock
  public @Nullable PsiFile findFile(@NotNull VirtualFile vFile) {
    CodeInsightContext context = CodeInsightContexts.anyContext();
    return findFile(vFile, context);
  }

  @Override
  @RequiresReadLock
  public @Nullable PsiFile findFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    if (vFile.isDirectory()) return null;

    if (!vFile.isValid()) {
      LOG.error(new InvalidVirtualFileAccessException(vFile));
      return null;
    }

    dispatchPendingEvents();
    FileViewProvider viewProvider = findViewProvider(vFile, context);
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  @RequiresReadLock
  @Override
  public @Nullable PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    return getCachedPsiFile(vFile, CodeInsightContexts.anyContext());
  }

  @Override
  public @Nullable PsiFile getCachedPsiFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    if (!vFile.isValid()) {
      throw new InvalidVirtualFileAccessException(vFile);
    }

    Project project = myManager.getProject();
    if (project.isDisposed()) {
      LOG.error("Project is already disposed: " + project);
    }

    dispatchPendingEvents();

    return getCachedPsiFileInner(vFile, context);
  }

  @RequiresReadLock
  @Override
  public @Nullable PsiDirectory findDirectory(@NotNull VirtualFile vFile) {
    Project project = myManager.getProject();
    if (project.isDisposed()) {
      LOG.error("Access to psi files should not be performed after project disposal: " + project);
    }

    if (!vFile.isValid()) {
      LOG.error(new InvalidVirtualFileAccessException(vFile));
      return null;
    }

    if (!vFile.isDirectory()) {
      return null;
    }
    dispatchPendingEvents();

    return findDirectoryImpl(vFile, getVFileToPsiDirMap());
  }

  private @Nullable PsiDirectory findDirectoryImpl(@NotNull VirtualFile vFile, @NotNull ConcurrentMap<VirtualFile, PsiDirectory> psiDirMap) {
    PsiDirectory psiDir = psiDirMap.get(vFile);
    if (psiDir != null) return psiDir;

    if (isExcludedOrIgnored(vFile)) return null;

    VirtualFile parent = vFile.getParent();
    if (parent != null) { //?
      findDirectoryImpl(parent, psiDirMap);// need to cache parent directory - used for firing events
    }

    psiDir = PsiDirectoryFactory.getInstance(myManager.getProject()).createDirectory(vFile);
    return ConcurrencyUtil.cacheOrGet(psiDirMap, vFile, psiDir);
  }

  private boolean isExcludedOrIgnored(@NotNull VirtualFile vFile) {
    if (myManager.getProject().isDefault()) return false;
    FileIndexFacade fileIndexFacade = myFileIndex.getValue();
    return Registry.is("ide.hide.excluded.files") ? fileIndexFacade.isExcludedFile(vFile) : fileIndexFacade.isUnderIgnored(vFile);
  }

  @Override
  public PsiDirectory getCachedDirectory(@NotNull VirtualFile vFile) {
    return getVFileToPsiDirMap().get(vFile);
  }

  @ApiStatus.Internal
  @Override
  public void removeFilesAndDirsRecursively(@NotNull VirtualFile vFile) {
    DebugUtil.performPsiModification("removeFilesAndDirsRecursively", () -> {
      VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (file.isDirectory()) {
            getVFileToPsiDirMap().remove(file);
          }
          else {
            Iterable<FileViewProvider> oldProviders = myVFileToViewProviderMap.remove(file);
            if (oldProviders != null) {
              for (FileViewProvider viewProvider : oldProviders) {
                markInvalidated(viewProvider);
              }
            }
          }
          return true;
        }
      });
    });
  }

  private void markInvalidated(@NotNull FileViewProvider viewProvider) {
    viewProvider.putUserData(IN_COMA, null);
    ((AbstractFileViewProvider)viewProvider).markInvalidated();
    viewProvider.getVirtualFile().putUserData(myPsiHardRefKey, null);
  }

  public static void markPossiblyInvalidated(@NotNull FileViewProvider viewProvider) {
    LOG.assertTrue(!(viewProvider instanceof FreeThreadedFileViewProvider));
    viewProvider.putUserData(IN_COMA, true);
    ((AbstractFileViewProvider)viewProvider).markPossiblyInvalidated();
    clearPsiCaches(viewProvider);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable PsiFile getCachedPsiFileInner(@NotNull VirtualFile file, @NotNull CodeInsightContext context) {
    FileViewProvider viewProvider = findCachedViewProvider(file, context);
    return viewProvider == null ? null : ((AbstractFileViewProvider)viewProvider).getCachedPsi(viewProvider.getBaseLanguage());
  }

  @Override
  public @NotNull List<PsiFile> getAllCachedFiles() {
    List<PsiFile> files = new ArrayList<>();
    myVFileToViewProviderMap.forEach((file, codeInsightContext, __) -> {
      FileViewProvider updatedProvider = findCachedViewProvider(file, codeInsightContext);
      if (updatedProvider != null) {
        ContainerUtil.addAllNotNull(files, ((AbstractFileViewProvider)updatedProvider).getCachedPsiFiles());
      }
    });
    return files;
  }

  @RequiresWriteLock
  private void removeInvalidDirs() {
    myVFileToPsiDirMap.set(null);
  }

  @RequiresWriteLock
  @ApiStatus.Internal
  @Override
  public void removeInvalidFilesAndDirs(boolean useFind) {
    removeInvalidDirs();

    // note: important to update directories the map first - findFile uses findDirectory!
    ArrayList<Entry> fileToPsiFileMap = new ArrayList<>(myVFileToViewProviderMap.getAllEntries());
    List<Entry> originalFileToPsiFileMap = myVFileToViewProviderMap.getAllEntries();
    if (useFind) {
      myVFileToViewProviderMap.clear();
    }
    for (Iterator<Entry> iterator = fileToPsiFileMap.iterator(); iterator.hasNext();) {
      Entry entry = iterator.next();
      VirtualFile vFile = entry.getFile();
      CodeInsightContext context = entry.getContext();

      if (!vFile.isValid()) {
        iterator.remove();
        continue;
      }

      FileViewProvider viewProvider = entry.getProvider();
      if (useFind) {
        PsiFile psiFile1 = findFile(vFile, context);
        if (psiFile1 == null) {
          iterator.remove();
          continue;
        }

        if (!areViewProvidersEquivalent(viewProvider, psiFile1.getViewProvider())) {
          iterator.remove();
        }
        else {
          clearPsiCaches(viewProvider);
        }
      }
      else if (!evaluateValidity((AbstractFileViewProvider)viewProvider)) {
        iterator.remove();
      }
    }
    myVFileToViewProviderMap.replaceAll(fileToPsiFileMap);

    markInvalidations(originalFileToPsiFileMap);
  }

  @ApiStatus.Internal
  public static boolean areViewProvidersEquivalent(@NotNull FileViewProvider view1, @NotNull FileViewProvider view2) {
    if (view1.getClass() != view2.getClass() || view1.getFileType() != view2.getFileType()) return false;

    Language baseLanguage = view1.getBaseLanguage();
    if (baseLanguage != view2.getBaseLanguage()) return false;

    if (!view1.getLanguages().equals(view2.getLanguages())) return false;
    PsiFile psi1 = view1.getPsi(baseLanguage);
    PsiFile psi2 = view2.getPsi(baseLanguage);
    if (psi1 == null || psi2 == null) return psi1 == psi2;
    return psi1.getClass() == psi2.getClass();
  }

  private void markInvalidations(@NotNull List<Entry> originalFileToPsiFileMap) {
    if (!originalFileToPsiFileMap.isEmpty()) {
      DebugUtil.performPsiModification(null, ()->{
        for (Entry entry : originalFileToPsiFileMap) {
          FileViewProvider viewProvider = entry.getProvider();
          if (myVFileToViewProviderMap.get(entry.getFile(), entry.getContext()) != viewProvider) {
            markInvalidated(viewProvider);
          }
        }
      });
    }
  }

  @RequiresWriteLock
  @Override
  public void reloadFromDisk(@NotNull PsiFile psiFile) {
    VirtualFile vFile = psiFile.getVirtualFile();
    assert vFile != null;

    Document document = FileDocumentManager.getInstance().getCachedDocument(vFile);
    if (document != null) {
      FileDocumentManager.getInstance().reloadFromDisk(document, psiFile.getProject());
    }
    else {
      reloadPsiAfterTextChange(psiFile.getViewProvider(), vFile);
    }
  }

  @ApiStatus.Internal
  @Override
  public void reloadPsiAfterTextChange(@NotNull FileViewProvider viewProvider, @NotNull VirtualFile vFile) {
    if (!areViewProvidersEquivalent(viewProvider, createFileViewProvider(vFile, false))) {
      forceReload(vFile);
      return;
    }

    ((AbstractFileViewProvider)viewProvider).onContentReload();
  }

  /**
   * Should be called only from implementations of {@link PsiFile#isValid()}, only after they've been {@link PsiFileEx#markInvalidated()},
   * and only to check if they can be made valid again.
   * Synchronized by read-write action. Calls from several threads in read action for the same virtual file are allowed.
   * @return if the file is still valid
   */
  @RequiresReadLock(generateAssertion = false)
  @Override
  public boolean evaluateValidity(@NotNull PsiFile file) {
    AbstractFileViewProvider viewProvider = (AbstractFileViewProvider)file.getViewProvider();
    return evaluateValidity(viewProvider) && viewProvider.getCachedPsiFiles().contains(file);
  }

  @RequiresReadLock
  private boolean evaluateValidity(@NotNull AbstractFileViewProvider viewProvider) {
    VirtualFile file = viewProvider.getVirtualFile();
    // todo IJPL-339 maybe rework evaluate validity
    //      so that all view providers are invalidated together?
    CodeInsightContext context = getRawContext(viewProvider);
    if (getRawCachedViewProvider(file, context) != viewProvider) {
      return false;
    }

    if (viewProvider.getUserData(IN_COMA) == null) {
      return true;
    }

    if (shouldResurrect(viewProvider, file)) {
      viewProvider.putUserData(IN_COMA, null);
      FileViewProvider cachedProvider = getRawCachedViewProvider(file, context);
      LOG.assertTrue(
        cachedProvider == viewProvider,
        "Cached: " + cachedProvider + ", expected: " + viewProvider
      );

      for (PsiFile psiFile : viewProvider.getCachedPsiFiles()) {
        // update "myPossiblyInvalidated" fields in files by calling "isValid"
        // that will call us recursively again, but since we're not IN_COMA now, we'll exit earlier and avoid SOE
        if (!psiFile.isValid()) {
          LOG.error(new PsiInvalidElementAccessException(psiFile));
        }
      }
      return true;
    }

    myVFileToViewProviderMap.remove(file, context, viewProvider);
    file.replace(myPsiHardRefKey, viewProvider, null);
    viewProvider.putUserData(IN_COMA, null);

    return false;
  }

  private boolean shouldResurrect(@NotNull FileViewProvider viewProvider, @NotNull VirtualFile file) {
    if (!file.isValid()) return false;

    CodeInsightContext context = getRawContext(viewProvider);
    if (myTempProviders.contains(file, context)) {
      LOG.error(new StackOverflowPreventedException("isValid leads to endless recursion in " + viewProvider.getClass() + ": " + new ArrayList<>(viewProvider.getLanguages())));
    }
    myTempProviders.put(file, context, null);
    try {
      if (!isContextRelevant(file, context)) {
        // invalid PsiFile if its context is not associated with the file anymore
        return false;
      }

      FileViewProvider recreated = createFileViewProvider(file, true);
      myTempProviders.put(file, context, recreated);
      return areViewProvidersEquivalent(viewProvider, recreated) &&
             ContainerUtil.all(((AbstractFileViewProvider)viewProvider).getCachedPsiFiles(), FileManagerImpl::isValidOriginal);
    }
    finally {
      FileViewProvider temp = myTempProviders.remove(file, context);
      if (temp != null) {
        DebugUtil.performPsiModification("invalidate temp view provider", ((AbstractFileViewProvider)temp)::markInvalidated);
      }
    }
  }

  /**
   * @return true if `context` is still relevant for the `file`. It's relevant if {@link CodeInsightContextManager#getCodeInsightContexts)}
   *         contain `context` or if `context` is `default` or `any`.
   */
  @RequiresReadLock
  private boolean isContextRelevant(@NotNull VirtualFile file, @NotNull CodeInsightContext context) {
    if (!CodeInsightContexts.isSharedSourceSupportEnabled(myManager.getProject())) {
      return true;
    }

    if (context == CodeInsightContexts.anyContext()) {
      return true;
    }

    List<@NotNull CodeInsightContext> contexts = CodeInsightContextManager.getInstance(myManager.getProject()).getCodeInsightContexts(file);
    return contexts.contains(context);
  }

  private static boolean isValidOriginal(@NotNull PsiFile file) {
    PsiFile original = file.getOriginalFile();
    return original == file || original.isValid();
  }

  /**
   * Find PsiFile for the supplied VirtualFile similar to {@link #getCachedPsiFile(VirtualFile)},
   * but without any attempts to resurrect the temporary invalidated file (see {@link #shouldResurrect(FileViewProvider, VirtualFile)}) or check its validity.
   * Useful for retrieving the PsiFile in EDT where expensive PSI operations are prohibited.
   * Do not use, since this is an extremely fragile and low-level API that can return surprising results. Use {@link #getCachedPsiFile(VirtualFile)} instead.
   */
  @RequiresReadLock
  @Override
  public PsiFile getFastCachedPsiFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    if (!vFile.isValid()) {
      throw new InvalidVirtualFileAccessException(vFile);
    }
    Project project = myManager.getProject();
    if (project.isDisposed()) {
      LOG.error("Project is already disposed: " + project);
    }
    dispatchPendingEvents();

    FileViewProvider viewProvider = getRawCachedViewProvider(vFile, context);
    if (viewProvider == null || viewProvider.getUserData(IN_COMA) != null) {
      return null;
    }
    return ((AbstractFileViewProvider)viewProvider).getCachedPsi(viewProvider.getBaseLanguage());
  }

  // todo IJPL-339 investigate this method usages!!!
  @Override
  public void forEachCachedDocument(@NotNull Consumer<? super @NotNull Document> consumer) {
    myVFileToViewProviderMap.forEachKey(file -> {
      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null) {
        consumer.accept(document);
      }
    });
  }

  private @NotNull CodeInsightContext getRawContext(@NotNull FileViewProvider fileViewProvider) {
    if (CodeInsightContexts.isSharedSourceSupportEnabled(myManager.getProject())) {
      CodeInsightContextManagerImpl manager =
        (CodeInsightContextManagerImpl)CodeInsightContextManager.getInstance(myManager.getProject());
      return manager.getCodeInsightContextRaw(fileViewProvider);
    }
    else {
      return CodeInsightContexts.defaultContext();
    }
  }

  @SuppressWarnings("UsagesOfObsoleteApi")
  private static <T, R> @Unmodifiable List<R> mapNotNull(@NotNull List<T> list, @NotNull Function<? super T, ? extends R> mapper) {
    if (list.size() == 1) {
      return ContainerUtil.createMaybeSingletonList(mapper.apply(list.get(0)));
    }
    else {
      return ContainerUtil.mapNotNull(list, mapper);
    }
  }
}
