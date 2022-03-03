// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.file.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.model.ModelBranch;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
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

  public void processQueue() {
    // just to call processQueue()
    ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
    if (map != null) {
      map.remove(NULL);
    }
  }

  @ApiStatus.Internal
  @NotNull
  public ConcurrentMap<VirtualFile, FileViewProvider> getVFileToViewProviderMap() {
    ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myVFileToViewProviderMap, ContainerUtil.createConcurrentWeakValueMap());
    }
    return map;
  }

  @NotNull
  private ConcurrentMap<VirtualFile, PsiDirectory> getVFileToPsiDirMap() {
    ConcurrentMap<VirtualFile, PsiDirectory> map = myVFileToPsiDirMap.get();
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myVFileToPsiDirMap, ContainerUtil.createConcurrentSoftValueMap());
    }
    return map;
  }

  public static void clearPsiCaches(@NotNull FileViewProvider provider) {
    ((AbstractFileViewProvider)provider).getCachedPsiFiles().forEach(PsiFile::clearCaches);
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

    ApplicationManager.getApplication().assertWriteAccessAllowed();

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

  private void clearViewProviders() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    DebugUtil.performPsiModification("clearViewProviders", () -> {
      ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
      if (map != null) {
        for (FileViewProvider provider : map.values()) {
          markInvalidated(provider);
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
  @NotNull
  public FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    assert !file.isDirectory();
    FileViewProvider viewProvider = findCachedViewProvider(file);
    if (viewProvider != null) return viewProvider;
    if (file instanceof VirtualFileWindow) {
      throw new IllegalStateException("File " + file + " is invalid");
    }

    Map<VirtualFile, FileViewProvider> tempMap = myTempProviders.get();
    if (tempMap.containsKey(file)) {
      return Objects.requireNonNull(tempMap.get(file), "Recursive file view provider creation");
    }

    viewProvider = createFileViewProvider(file, ModelBranch.getFileBranch(file) == null);
    if (file instanceof LightVirtualFile) {
      checkLightFileHasNoOtherPsi((LightVirtualFile)file);
      return file.putUserDataIfAbsent(myPsiHardRefKey, viewProvider);
    }
    return ConcurrencyUtil.cacheOrGet(getVFileToViewProviderMap(), file, viewProvider);
  }

  private void checkLightFileHasNoOtherPsi(@NotNull LightVirtualFile file) {
    FileViewProvider vp = FileDocumentManager.getInstance().findCachedPsiInAnyProject(file);
    if (vp != null) {
      Project project = vp.getManager().getProject();
      if (project != myManager.getProject()) {
        String psiFiles = vp.getAllFiles().stream().map(f -> f.getClass() + " [" + f.getLanguage() + "]").collect(Collectors.joining(", "));
        LOG.error(
          "Light files should have PSI only in one project, existing=" + vp + " in " + project + ", requested in " + myManager.getProject()
          + "; psiFiles: " + psiFiles);
      }
    }
  }

  @Override
  public FileViewProvider findCachedViewProvider(@NotNull VirtualFile file) {
    FileViewProvider viewProvider = getRawCachedViewProvider(file);

    if (viewProvider instanceof AbstractFileViewProvider && viewProvider.getUserData(IN_COMA) != null) {
      Map<VirtualFile, FileViewProvider> tempMap = myTempProviders.get();
      FileViewProvider temp = tempMap.get(file);
      if (temp != null) {
        return temp;
      }

      if (!evaluateValidity((AbstractFileViewProvider)viewProvider)) {
        return null;
      }
    }
    return viewProvider;
  }

  @Nullable
  private FileViewProvider getRawCachedViewProvider(@NotNull VirtualFile file) {
    ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
    FileViewProvider viewProvider = map == null ? null : map.get(file);
    return viewProvider == null ? file.getUserData(myPsiHardRefKey) : viewProvider;
  }

  @Override
  public void setViewProvider(@NotNull VirtualFile virtualFile, @Nullable FileViewProvider fileViewProvider) {
    FileViewProvider prev = getRawCachedViewProvider(virtualFile);
    if (prev == fileViewProvider) return;
    if (prev != null) {
      DebugUtil.performPsiModification(null, () -> markInvalidated(prev));
    }

    if (fileViewProvider == null) {
      getVFileToViewProviderMap().remove(virtualFile);
      virtualFile.putUserData(myPsiHardRefKey, null);
    }
    else if (virtualFile instanceof LightVirtualFile) {
      checkLightFileHasNoOtherPsi((LightVirtualFile)virtualFile);
      virtualFile.putUserData(myPsiHardRefKey, fileViewProvider);
    }
    else {
      getVFileToViewProviderMap().put(virtualFile, fileViewProvider);
    }
  }

  @Override
  @NotNull
  public FileViewProvider createFileViewProvider(@NotNull VirtualFile file, boolean eventSystemEnabled) {
    FileType fileType = file.getFileType();
    Language language = LanguageUtil.getLanguageForPsi(myManager.getProject(), file, fileType);
    FileViewProviderFactory factory = language == null
                                      ? FileTypeFileViewProviders.INSTANCE.forFileType(fileType)
                                      : LanguageFileViewProviders.INSTANCE.forLanguage(language);
    FileViewProvider viewProvider = factory == null ? null : factory.createFileViewProvider(file, language, myManager, eventSystemEnabled);

    return viewProvider == null ? new SingleRootFileViewProvider(myManager, file, eventSystemEnabled, fileType) : viewProvider;
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

  void possiblyInvalidatePhysicalPsi() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    removeInvalidDirs();
    for (FileViewProvider provider : getVFileToViewProviderMap().values()) {
      markPossiblyInvalidated(provider);
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
    for (VirtualFile file : new ArrayList<>(getVFileToViewProviderMap().keySet())) {
      findCachedViewProvider(file); // complete delayed validity checks
    }

    Map<VirtualFile, FileViewProvider> fileToViewProvider = new HashMap<>(getVFileToViewProviderMap());
    myVFileToViewProviderMap.set(null);
    for (Map.Entry<VirtualFile, FileViewProvider> entry : fileToViewProvider.entrySet()) {
      FileViewProvider fileViewProvider = entry.getValue();
      VirtualFile vFile = entry.getKey();
      LOG.assertTrue(vFile.isValid());
      PsiFile psiFile1 = findFile(vFile);
      if (psiFile1 != null && fileViewProvider != null && fileViewProvider.isPhysical()) { // might get collected
        PsiFile psi = fileViewProvider.getPsi(fileViewProvider.getBaseLanguage());
        assert psi != null : fileViewProvider +"; "+fileViewProvider.getBaseLanguage()+"; "+psiFile1;
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
  @Nullable
  public PsiFile findFile(@NotNull VirtualFile vFile) {
    if (vFile.isDirectory()) return null;

    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!vFile.isValid()) {
      LOG.error("Invalid file: " + vFile);
      return null;
    }

    dispatchPendingEvents();
    FileViewProvider viewProvider = findViewProvider(vFile);
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  @Override
  @Nullable
  public PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
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

  @Override
  @Nullable
  public PsiDirectory findDirectory(@NotNull VirtualFile vFile) {
    Project project = myManager.getProject();
    if (project.isDisposed()) {
      LOG.error("Access to psi files should not be performed after project disposal: " + project);
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!vFile.isValid()) {
      LOG.error("File is not valid:" + vFile);
      return null;
    }

    if (!vFile.isDirectory()) {
      return null;
    }
    dispatchPendingEvents();

    return findDirectoryImpl(vFile, getVFileToPsiDirMap());
  }

  @Nullable
  private PsiDirectory findDirectoryImpl(@NotNull VirtualFile vFile, @NotNull ConcurrentMap<VirtualFile, PsiDirectory> psiDirMap) {
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
    FileViewProvider fileViewProvider = findCachedViewProvider(file);
    return fileViewProvider != null ? ((AbstractFileViewProvider)fileViewProvider).getCachedPsi(fileViewProvider.getBaseLanguage()) : null;
  }

  @NotNull
  @Override
  public List<PsiFile> getAllCachedFiles() {
    List<PsiFile> files = new ArrayList<>();
    for (VirtualFile file : new ArrayList<>(getVFileToViewProviderMap().keySet())) {
      FileViewProvider provider = findCachedViewProvider(file);
      if (provider != null) {
        ContainerUtil.addAllNotNull(files, ((AbstractFileViewProvider)provider).getCachedPsiFiles());
      }
    }
    return files;
  }

  private void removeInvalidDirs() {
    myVFileToPsiDirMap.set(null);
  }

  void removeInvalidFilesAndDirs(boolean useFind) {
    removeInvalidDirs();

    // note: important to update directories map first - findFile uses findDirectory!
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

      FileViewProvider view = fileToPsiFileMap.get(vFile);
      if (useFind) {
        if (view == null) { // soft ref. collected
          iterator.remove();
          continue;
        }
        PsiFile psiFile1 = findFile(vFile);
        if (psiFile1 == null) {
          iterator.remove();
          continue;
        }

        if (!areViewProvidersEquivalent(view, psiFile1.getViewProvider())) {
          iterator.remove();
        }
        else {
          clearPsiCaches(view);
        }
      }
      else if (!evaluateValidity((AbstractFileViewProvider)view)) {
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

  @Override
  public void reloadFromDisk(@NotNull PsiFile file) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    VirtualFile vFile = file.getVirtualFile();
    assert vFile != null;

    Document document = FileDocumentManager.getInstance().getCachedDocument(vFile);
    if (document != null) {
      FileDocumentManager.getInstance().reloadFromDisk(document);
    }
    else {
      reloadPsiAfterTextChange(file.getViewProvider(), vFile);
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
  public boolean evaluateValidity(@NotNull PsiFile file) {
    AbstractFileViewProvider vp = (AbstractFileViewProvider)file.getViewProvider();
    return evaluateValidity(vp) && vp.getCachedPsiFiles().contains(file);
  }

  private boolean evaluateValidity(@NotNull AbstractFileViewProvider viewProvider) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    VirtualFile file = viewProvider.getVirtualFile();
    if (getRawCachedViewProvider(file) != viewProvider) {
      return false;
    }

    if (viewProvider.getUserData(IN_COMA) == null) {
      return true;
    }

    if (shouldResurrect(viewProvider, file)) {
      viewProvider.putUserData(IN_COMA, null);
      LOG.assertTrue(getRawCachedViewProvider(file) == viewProvider);

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
             !ContainerUtil.exists(((AbstractFileViewProvider)viewProvider).getCachedPsiFiles(), FileManagerImpl::hasInvalidOriginal);
    }
    finally {
      FileViewProvider temp = tempProviders.remove(file);
      if (temp != null) {
        DebugUtil.performPsiModification("invalidate temp view provider", ((AbstractFileViewProvider)temp)::markInvalidated);
      }
    }
  }

  private static boolean hasInvalidOriginal(@NotNull PsiFile file) {
    PsiFile original = file.getOriginalFile();
    return original != file && !original.isValid();
  }
}
