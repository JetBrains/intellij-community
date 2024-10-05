// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl;

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
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class FileManagerImpl implements FileManager {
  private static final Key<Boolean> IN_COMA = Key.create("IN_COMA");
  private static final Logger LOG = Logger.getInstance(FileManagerImpl.class);
  private final Key<FileViewProvider> myPsiHardRefKey = Key.create("HARD_REFERENCE_TO_PSI"); //non-static!

  private final PsiManagerImpl myManager;
  private final NotNullLazyValue<? extends FileIndexFacade> myFileIndex;

  private final AtomicReference<ConcurrentMap<VirtualFile, PsiDirectory>> myVFileToPsiDirMap = new AtomicReference<>();
  private final AtomicReference<ConcurrentMap<VirtualFile, FileViewProvider>> myVFileToViewProviderMap = new AtomicReference<>();

  /**
   * Holds thread-local temporary providers that are sometimes needed while checking if a file is valid
   */
  private final ThreadLocal<Map<VirtualFile, FileViewProvider>> myTempProviders = ThreadLocal.withInitial(HashMap::new);

  private final MessageBusConnection myConnection;

  public FileManagerImpl(@NotNull PsiManagerImpl manager, @NotNull NotNullLazyValue<? extends FileIndexFacade> fileIndex) {
    myManager = manager;
    myFileIndex = fileIndex;
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

  private static final VirtualFile NULL = new LightVirtualFile();

  /**
   * Removes garbage from myVFileToViewProviderMap
   */
  public void processQueue() {
    ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
    if (map == null) return;

    // myVFileToViewProviderMap is in fact ConcurrentWeakValueHashMap.
    // calling map.remove(unrelated-object) calls ConcurrentWeakValueHashMap#processQueue under the hood
    map.remove(NULL);
  }

  @NotNull
  private ConcurrentMap<VirtualFile, FileViewProvider> getVFileToViewProviderMap() {
    ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myVFileToViewProviderMap, CollectionFactory.createConcurrentWeakValueMap());
    }
    return map;
  }

  private @NotNull ConcurrentMap<VirtualFile, PsiDirectory> getVFileToPsiDirMap() {
    ConcurrentMap<VirtualFile, PsiDirectory> map = myVFileToPsiDirMap.get();
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myVFileToPsiDirMap, ContainerUtil.createConcurrentSoftValueMap());
    }
    return map;
  }

  @TestOnly
  public void assertNoInjectedFragmentsStoredInMaps() {
    ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
    for (Map.Entry<VirtualFile, FileViewProvider> entry : map.entrySet()) {
      if (entry.getKey() instanceof VirtualFileWindow) {
        throw new AssertionError(entry.getKey());
      }
      FileViewProvider provider = entry.getValue();
      PsiLanguageInjectionHost injectionHost = InjectedLanguageManager.getInstance(myManager.getProject()).getInjectionHost(provider);
      if (injectionHost != null) {
        throw new AssertionError(injectionHost);
      }
    }
  }

  public static void clearPsiCaches(@NotNull FileViewProvider viewProvider) {
    ((AbstractFileViewProvider)viewProvider).getCachedPsiFiles().forEach(PsiFile::clearCaches);
  }

  public void forceReload(@NotNull VirtualFile vFile) {
    LanguageSubstitutors.cancelReparsing(vFile);
    FileViewProvider viewProvider = findCachedViewProvider(vFile);
    if (viewProvider == null) {
      return;
    }
    if (!viewProvider.isEventSystemEnabled()) {
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

  public void firePropertyChangedForUnloadedPsi() {
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setPropertyName(PsiTreeChangeEvent.PROP_UNLOADED_PSI);

    myManager.beforePropertyChange(event);
    myManager.propertyChanged(event);
  }

  public void dispose() {
    clearViewProviders();
  }

  @RequiresWriteLock
  private void clearViewProviders() {
    DebugUtil.performPsiModification("clearViewProviders", () -> {
      ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
      if (map != null) {
        for (FileViewProvider viewProvider : map.values()) {
          markInvalidated(viewProvider);
        }
      }
      myVFileToViewProviderMap.set(null);
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
    assert !vFile.isDirectory();
    FileViewProvider viewProvider = findCachedViewProvider(vFile);
    if (viewProvider != null) return viewProvider;
    if (vFile instanceof VirtualFileWindow) {
      throw new IllegalStateException("File " + vFile + " is invalid");
    }

    Map<VirtualFile, FileViewProvider> tempMap = myTempProviders.get();
    if (tempMap.containsKey(vFile)) {
      return Objects.requireNonNull(tempMap.get(vFile), "Recursive file view provider creation");
    }

    viewProvider = createFileViewProvider(vFile, !LightVirtualFile.shouldSkipEventSystem(vFile));
    if (vFile instanceof LightVirtualFile) {
      checkLightFileHasNoOtherPsi((LightVirtualFile)vFile);
      return vFile.putUserDataIfAbsent(myPsiHardRefKey, viewProvider);
    }
    return ConcurrencyUtil.cacheOrGet(getVFileToViewProviderMap(), vFile, viewProvider);
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
  public FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile) {
    FileViewProvider viewProvider = getRawCachedViewProvider(vFile);

    if (viewProvider instanceof AbstractFileViewProvider && viewProvider.getUserData(IN_COMA) != null) {
      Map<VirtualFile, FileViewProvider> tempMap = myTempProviders.get();
      FileViewProvider temp = tempMap.get(vFile);
      if (temp != null) {
        return temp;
      }

      if (!evaluateValidity((AbstractFileViewProvider)viewProvider)) {
        return null;
      }
    }
    return viewProvider;
  }

  private @Nullable FileViewProvider getRawCachedViewProvider(@NotNull VirtualFile vFile) {
    ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
    FileViewProvider viewProvider = map == null ? null : map.get(vFile);
    return viewProvider == null ? vFile.getUserData(myPsiHardRefKey) : viewProvider;
  }

  @Override
  public void setViewProvider(@NotNull VirtualFile vFile, @Nullable FileViewProvider viewProvider) {
    FileViewProvider prev = getRawCachedViewProvider(vFile);
    if (prev == viewProvider) return;
    if (prev != null) {
      DebugUtil.performPsiModification(null, () -> markInvalidated(prev));
    }

    if (viewProvider == null) {
      getVFileToViewProviderMap().remove(vFile);
      vFile.putUserData(myPsiHardRefKey, null);
    }
    else if (vFile instanceof LightVirtualFile) {
      checkLightFileHasNoOtherPsi((LightVirtualFile)vFile);
      vFile.putUserData(myPsiHardRefKey, viewProvider);
    }
    else {
      ThreadingAssertions.assertWriteAccess();
      getVFileToViewProviderMap().put(vFile, viewProvider);
    }
  }

  @Override
  public @NotNull FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile, boolean eventSystemEnabled) {
    FileType fileType = vFile.getFileType();
    Language language = LanguageUtil.getLanguageForPsi(myManager.getProject(), vFile, fileType);
    FileViewProviderFactory factory = language == null
                                      ? FileTypeFileViewProviders.INSTANCE.forFileType(fileType)
                                      : LanguageFileViewProviders.INSTANCE.forLanguage(language);
    FileViewProvider viewProvider = factory == null ? null : factory.createFileViewProvider(vFile, language, myManager, eventSystemEnabled);

    return viewProvider == null ? new SingleRootFileViewProvider(myManager, vFile, eventSystemEnabled, fileType) : viewProvider;
  }

  private boolean myProcessingFileTypesChange;

  void processFileTypesChanged(boolean clearViewProviders) {
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
  void possiblyInvalidatePhysicalPsi() {
    removeInvalidDirs();
    for (FileViewProvider viewProvider : getVFileToViewProviderMap().values()) {
      markPossiblyInvalidated(viewProvider);
    }
  }

  void dispatchPendingEvents() {
    Project project = myManager.getProject();
    if (project.isDisposed()) {
      LOG.error("Project is already disposed: " + project);
    }
    myConnection.deliverImmediately();
  }

  @TestOnly
  void checkConsistency() {
    for (VirtualFile vFile : new ArrayList<>(getVFileToViewProviderMap().keySet())) {
      findCachedViewProvider(vFile); // complete delayed validity checks
    }

    Map<VirtualFile, FileViewProvider> fileToViewProvider = new HashMap<>(getVFileToViewProviderMap());
    myVFileToViewProviderMap.set(null);
    for (Map.Entry<VirtualFile, FileViewProvider> entry : fileToViewProvider.entrySet()) {
      FileViewProvider viewProvider = entry.getValue();
      VirtualFile vFile = entry.getKey();
      LOG.assertTrue(vFile.isValid());
      PsiFile psiFile1 = findFile(vFile);
      if (psiFile1 != null && viewProvider != null && viewProvider.isPhysical()) { // might get collected
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

  @Override
  @RequiresReadLock
  public @Nullable PsiFile findFile(@NotNull VirtualFile vFile) {
    if (vFile.isDirectory()) return null;

    if (!vFile.isValid()) {
      LOG.error(new InvalidVirtualFileAccessException(vFile));
      return null;
    }

    dispatchPendingEvents();
    FileViewProvider viewProvider = findViewProvider(vFile);
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  @RequiresReadLock
  @Override
  public @Nullable PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    if (!vFile.isValid()) {
      throw new InvalidVirtualFileAccessException(vFile);
    }

    Project project = myManager.getProject();
    if (project.isDisposed()) {
      LOG.error("Project is already disposed: " + project);
    }

    dispatchPendingEvents();

    return getCachedPsiFileInner(vFile);
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

  public PsiDirectory getCachedDirectory(@NotNull VirtualFile vFile) {
    return getVFileToPsiDirMap().get(vFile);
  }

  void removeFilesAndDirsRecursively(@NotNull VirtualFile vFile) {
    DebugUtil.performPsiModification("removeFilesAndDirsRecursively", () -> {
      VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (file.isDirectory()) {
            getVFileToPsiDirMap().remove(file);
          }
          else {
            FileViewProvider viewProvider = getVFileToViewProviderMap().remove(file);
            if (viewProvider != null) {
              markInvalidated(viewProvider);
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

  @Nullable
  PsiFile getCachedPsiFileInner(@NotNull VirtualFile file) {
    FileViewProvider viewProvider = findCachedViewProvider(file);
    return viewProvider == null ? null : ((AbstractFileViewProvider)viewProvider).getCachedPsi(viewProvider.getBaseLanguage());
  }

  @Override
  public @NotNull List<PsiFile> getAllCachedFiles() {
    List<PsiFile> files = new ArrayList<>();
    for (VirtualFile file : new ArrayList<>(getVFileToViewProviderMap().keySet())) {
      FileViewProvider viewProvider = findCachedViewProvider(file);
      if (viewProvider != null) {
        ContainerUtil.addAllNotNull(files, ((AbstractFileViewProvider)viewProvider).getCachedPsiFiles());
      }
    }
    return files;
  }

  @RequiresWriteLock
  private void removeInvalidDirs() {
    myVFileToPsiDirMap.set(null);
  }

  @RequiresWriteLock
  void removeInvalidFilesAndDirs(boolean useFind) {
    removeInvalidDirs();

    // note: important to update directories the map first - findFile uses findDirectory!
    Map<VirtualFile, FileViewProvider> fileToPsiFileMap = new HashMap<>(getVFileToViewProviderMap());
    Map<VirtualFile, FileViewProvider> originalFileToPsiFileMap = new HashMap<>(getVFileToViewProviderMap());
    if (useFind) {
      myVFileToViewProviderMap.set(null);
    }
    for (Iterator<VirtualFile> iterator = fileToPsiFileMap.keySet().iterator(); iterator.hasNext();) {
      VirtualFile vFile = iterator.next();

      if (!vFile.isValid()) {
        iterator.remove();
        continue;
      }

      FileViewProvider viewProvider = fileToPsiFileMap.get(vFile);
      if (useFind) {
        if (viewProvider == null) { // soft ref. collected
          iterator.remove();
          continue;
        }
        PsiFile psiFile1 = findFile(vFile);
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
    myVFileToViewProviderMap.set(null);
    getVFileToViewProviderMap().putAll(fileToPsiFileMap);

    markInvalidations(originalFileToPsiFileMap);
  }

  static boolean areViewProvidersEquivalent(@NotNull FileViewProvider view1, @NotNull FileViewProvider view2) {
    if (view1.getClass() != view2.getClass() || view1.getFileType() != view2.getFileType()) return false;

    Language baseLanguage = view1.getBaseLanguage();
    if (baseLanguage != view2.getBaseLanguage()) return false;

    if (!view1.getLanguages().equals(view2.getLanguages())) return false;
    PsiFile psi1 = view1.getPsi(baseLanguage);
    PsiFile psi2 = view2.getPsi(baseLanguage);
    if (psi1 == null || psi2 == null) return psi1 == psi2;
    return psi1.getClass() == psi2.getClass();
  }

  private void markInvalidations(@NotNull Map<VirtualFile, FileViewProvider> originalFileToPsiFileMap) {
    if (!originalFileToPsiFileMap.isEmpty()) {
      DebugUtil.performPsiModification(null, ()->{
        for (Map.Entry<VirtualFile, FileViewProvider> entry : originalFileToPsiFileMap.entrySet()) {
          FileViewProvider viewProvider = entry.getValue();
          if (getVFileToViewProviderMap().get(entry.getKey()) != viewProvider) {
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

  void reloadPsiAfterTextChange(@NotNull FileViewProvider viewProvider, @NotNull VirtualFile vFile) {
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
  public boolean evaluateValidity(@NotNull PsiFile file) {
    AbstractFileViewProvider viewProvider = (AbstractFileViewProvider)file.getViewProvider();
    return evaluateValidity(viewProvider) && viewProvider.getCachedPsiFiles().contains(file);
  }

  @RequiresReadLock
  private boolean evaluateValidity(@NotNull AbstractFileViewProvider viewProvider) {
    VirtualFile file = viewProvider.getVirtualFile();
    if (getRawCachedViewProvider(file) != viewProvider) {
      return false;
    }

    if (viewProvider.getUserData(IN_COMA) == null) {
      return true;
    }

    if (shouldResurrect(viewProvider, file)) {
      viewProvider.putUserData(IN_COMA, null);
      FileViewProvider cachedProvider = getRawCachedViewProvider(file);
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

    getVFileToViewProviderMap().remove(file, viewProvider);
    file.replace(myPsiHardRefKey, viewProvider, null);
    viewProvider.putUserData(IN_COMA, null);

    return false;
  }

  private boolean shouldResurrect(@NotNull FileViewProvider viewProvider, @NotNull VirtualFile file) {
    if (!file.isValid()) return false;

    Map<VirtualFile, FileViewProvider> tempProviders = myTempProviders.get();
    if (tempProviders.containsKey(file)) {
      LOG.error(new StackOverflowPreventedException("isValid leads to endless recursion in " + viewProvider.getClass() + ": " + new ArrayList<>(viewProvider.getLanguages())));
    }
    tempProviders.put(file, null);
    try {
      FileViewProvider recreated = createFileViewProvider(file, true);
      tempProviders.put(file, recreated);
      return areViewProvidersEquivalent(viewProvider, recreated) &&
             ContainerUtil.all(((AbstractFileViewProvider)viewProvider).getCachedPsiFiles(), FileManagerImpl::isValidOriginal);
    }
    finally {
      FileViewProvider temp = tempProviders.remove(file);
      if (temp != null) {
        DebugUtil.performPsiModification("invalidate temp view provider", ((AbstractFileViewProvider)temp)::markInvalidated);
      }
    }
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
  @ApiStatus.Internal
  @RequiresReadLock
  public PsiFile getFastCachedPsiFile(@NotNull VirtualFile vFile) {
    if (!vFile.isValid()) {
      throw new InvalidVirtualFileAccessException(vFile);
    }
    Project project = myManager.getProject();
    if (project.isDisposed()) {
      LOG.error("Project is already disposed: " + project);
    }
    dispatchPendingEvents();
    FileViewProvider viewProvider = getRawCachedViewProvider(vFile);
    if (viewProvider == null || viewProvider.getUserData(IN_COMA) != null) {
      return null;
    }
    return ((AbstractFileViewProvider)viewProvider).getCachedPsi(viewProvider.getBaseLanguage());
  }

  @ApiStatus.Internal
  public void forEachCachedDocument(@NotNull Consumer<? super @NotNull Document> consumer) {
    ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
    if (map != null) {
      map.keySet().forEach(virtualFile -> {
        Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
        if (document != null) {
          consumer.accept(document);
        }
      });
    }
  }
}
