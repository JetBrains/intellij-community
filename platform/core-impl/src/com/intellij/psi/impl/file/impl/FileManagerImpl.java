/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.psi.impl.file.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class FileManagerImpl implements FileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.impl.FileManagerImpl");
  private final Key<FileViewProvider> myPsiHardRefKey = Key.create("HARD_REFERENCE_TO_PSI"); //non-static!

  private final PsiManagerImpl myManager;
  private final FileIndexFacade myFileIndex;

  private final ConcurrentMap<VirtualFile, PsiDirectory> myVFileToPsiDirMap = ContainerUtil.createConcurrentSoftValueMap();
  private final ConcurrentMap<VirtualFile, FileViewProvider> myVFileToViewProviderMap = ContainerUtil.createConcurrentWeakValueMap();

  private boolean myInitialized;
  private boolean myDisposed;

  private final FileDocumentManager myFileDocumentManager;
  private final MessageBusConnection myConnection;

  public FileManagerImpl(PsiManagerImpl manager, FileDocumentManager fileDocumentManager, FileIndexFacade fileIndex) {
    myManager = manager;
    myFileIndex = fileIndex;
    myConnection = manager.getProject().getMessageBus().connect();

    myFileDocumentManager = fileDocumentManager;

    Disposer.register(manager.getProject(), this);
    LowMemoryWatcher.register(new Runnable() {
      @Override
      public void run() {
        processQueue();
      }
    }, this);
  }

  private static final VirtualFile NULL = new LightVirtualFile();

  public void processQueue() {
    // just to call processQueue()
    myVFileToViewProviderMap.remove(NULL);
  }

  @TestOnly
  @NotNull
  public ConcurrentMap<VirtualFile, FileViewProvider> getVFileToViewProviderMap() {
    return myVFileToViewProviderMap;
  }

  private void updateAllViewProviders() {
    handleFileTypesChange(new FileTypesChanged() {
      @Override
      protected void updateMaps() {
        for (final FileViewProvider provider : myVFileToViewProviderMap.values()) {
          if (!provider.getVirtualFile().isValid()) {
            continue;
          }

          clearPsiCaches(provider);
        }
        removeInvalidFilesAndDirs(false);
        checkLanguageChange();
      }
    });
  }

  public static void clearPsiCaches(@NotNull FileViewProvider provider) {
    if (provider instanceof SingleRootFileViewProvider) {
      for (PsiFile root : ((SingleRootFileViewProvider)provider).getCachedPsiFiles()) {
        if (root instanceof PsiFileImpl) {
          ((PsiFileImpl)root).clearCaches();
        }
      }
    } else {
      for (Language language : provider.getLanguages()) {
        final PsiFile psi = provider.getPsi(language);
        if (psi instanceof PsiFileImpl) {
          ((PsiFileImpl)psi).clearCaches();
        }
      }
    }
  }

  private void checkLanguageChange() {
    Map<VirtualFile, FileViewProvider> fileToPsiFileMap = new THashMap<VirtualFile, FileViewProvider>(myVFileToViewProviderMap);
    Map<VirtualFile, FileViewProvider> originalFileToPsiFileMap = new THashMap<VirtualFile, FileViewProvider>(myVFileToViewProviderMap);
    myVFileToViewProviderMap.clear();
    for (Iterator<VirtualFile> iterator = fileToPsiFileMap.keySet().iterator(); iterator.hasNext();) {
      VirtualFile vFile = iterator.next();
      Language language = LanguageUtil.getLanguageForPsi(myManager.getProject(), vFile);
      if (language != null && language != fileToPsiFileMap.get(vFile).getBaseLanguage()) {
        iterator.remove();
      }
    }
    myVFileToViewProviderMap.putAll(fileToPsiFileMap);
    markInvalidations(originalFileToPsiFileMap);
  }

  public void forceReload(@NotNull VirtualFile vFile) {
    LanguageSubstitutors.cancelReparsing(vFile);
    FileViewProvider viewProvider = findCachedViewProvider(vFile);
    if (viewProvider == null) {
      return;
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    setViewProvider(vFile, null);

    VirtualFile dir = vFile.getParent();
    PsiDirectory parentDir = dir == null ? null : getCachedDirectory(dir);
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    if (parentDir != null) {
      event.setParent(parentDir);
      myManager.childrenChanged(event);
    }
    else {
      firePropertyChangedForUnloadedPsi(event, vFile);
    }
  }

  void firePropertyChangedForUnloadedPsi(@NotNull PsiTreeChangeEventImpl event, @NotNull VirtualFile vFile) {
    event.setPropertyName(PsiTreeChangeEvent.PROP_UNLOADED_PSI);
    event.setOldValue(vFile);
    event.setNewValue(vFile);

    myManager.beforePropertyChange(event);
    myManager.propertyChanged(event);
  }

  @Override
  public void dispose() {
    if (myInitialized) {
      myConnection.disconnect();
    }
    clearViewProviders();

    myDisposed = true;
  }

  private void clearViewProviders() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    DebugUtil.startPsiModification("clearViewProviders");
    try {
      for (final FileViewProvider provider : myVFileToViewProviderMap.values()) {
        markInvalidated(provider);
      }
      myVFileToViewProviderMap.clear();
    }
    finally {
      DebugUtil.finishPsiModification();
    }
  }

  @Override
  @TestOnly
  public void cleanupForNextTest() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        clearViewProviders();
      }
    });

    myVFileToPsiDirMap.clear();
    ((PsiModificationTrackerImpl)myManager.getModificationTracker()).incCounter();
  }

  @Override
  @NotNull
  public FileViewProvider findViewProvider(@NotNull final VirtualFile file) {
    assert !file.isDirectory();
    FileViewProvider viewProvider = findCachedViewProvider(file);
    if (viewProvider != null) return viewProvider;

    viewProvider = createFileViewProvider(file, true);
    if (file instanceof LightVirtualFile) {
      return file.putUserDataIfAbsent(myPsiHardRefKey, viewProvider);
    }
    return ConcurrencyUtil.cacheOrGet(myVFileToViewProviderMap, file, viewProvider);
  }

  @Override
  public FileViewProvider findCachedViewProvider(@NotNull final VirtualFile file) {
    FileViewProvider viewProvider = getFromInjected(file);
    if (viewProvider == null) viewProvider = myVFileToViewProviderMap.get(file);
    if (viewProvider == null) viewProvider = file.getUserData(myPsiHardRefKey);
    return viewProvider;
  }

  @Nullable
  private FileViewProvider getFromInjected(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) {
      DocumentWindow document = ((VirtualFileWindow)file).getDocumentWindow();
      PsiFile psiFile = PsiDocumentManager.getInstance(myManager.getProject()).getCachedPsiFile(document);
      if (psiFile == null) return null;
      return psiFile.getViewProvider();
    }
    return null;
  }

  @Override
  public void setViewProvider(@NotNull final VirtualFile virtualFile, @Nullable final FileViewProvider fileViewProvider) {
    FileViewProvider prev = findCachedViewProvider(virtualFile);
    if (prev == fileViewProvider) return;
    if (prev != null) {
      DebugUtil.startPsiModification(null);
      try {
        markInvalidated(prev);
        DebugUtil.onInvalidated(prev);
      }
      finally {
        DebugUtil.finishPsiModification();
      }
    }

    if (!(virtualFile instanceof VirtualFileWindow)) {
      if (fileViewProvider == null) {
        myVFileToViewProviderMap.remove(virtualFile);
      }
      else {
        if (virtualFile instanceof LightVirtualFile) {
          virtualFile.putUserData(myPsiHardRefKey, fileViewProvider);
        } else {
          myVFileToViewProviderMap.put(virtualFile, fileViewProvider);
        }
      }
    }
  }

  @Override
  @NotNull
  public FileViewProvider createFileViewProvider(@NotNull final VirtualFile file, boolean eventSystemEnabled) {
    FileType fileType = file.getFileType();
    Language language = LanguageUtil.getLanguageForPsi(myManager.getProject(), file);
    FileViewProviderFactory factory = language == null
                                      ? FileTypeFileViewProviders.INSTANCE.forFileType(fileType)
                                      : LanguageFileViewProviders.INSTANCE.forLanguage(language);
    FileViewProvider viewProvider = factory == null ? null : factory.createFileViewProvider(file, language, myManager, eventSystemEnabled);

    return viewProvider == null ? new SingleRootFileViewProvider(myManager, file, eventSystemEnabled, fileType) : viewProvider;
  }

  public void markInitialized() {
    LOG.assertTrue(!myInitialized);
    myDisposed = false;
    myInitialized = true;

    myConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        updateAllViewProviders();
      }

      @Override
      public void exitDumbMode() {
        updateAllViewProviders();
      }
    });
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  void processFileTypesChanged() {
    handleFileTypesChange(new FileTypesChanged() {
      @Override
      protected void updateMaps() {
        removeInvalidFilesAndDirs(true);
      }
    });
  }

  private abstract class FileTypesChanged implements Runnable {
    protected abstract void updateMaps();

    @Override
    public void run() {
      PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
      event.setPropertyName(PsiTreeChangeEvent.PROP_FILE_TYPES);
      myManager.beforePropertyChange(event);

      updateMaps();

      myManager.propertyChanged(event);
    }
  }

  private boolean myProcessingFileTypesChange;
  private void handleFileTypesChange(@NotNull FileTypesChanged runnable) {
    if (myProcessingFileTypesChange) return;
    myProcessingFileTypesChange = true;
    try {
      ApplicationManager.getApplication().runWriteAction(runnable);
    }
    finally {
      myProcessingFileTypesChange = false;
    }
  }

  void dispatchPendingEvents() {
    if (!myInitialized) {
      LOG.error("Project is not yet initialized: "+myManager.getProject());
    }
    if (myDisposed) {
      LOG.error("Project is already disposed: "+myManager.getProject());
    }

    myConnection.deliverImmediately();
  }

  @TestOnly
  public void checkConsistency() {
    Map<VirtualFile, FileViewProvider> fileToViewProvider = new HashMap<VirtualFile, FileViewProvider>(myVFileToViewProviderMap);
    myVFileToViewProviderMap.clear();
    for (VirtualFile vFile : fileToViewProvider.keySet()) {
      final FileViewProvider fileViewProvider = fileToViewProvider.get(vFile);

      LOG.assertTrue(vFile.isValid());
      PsiFile psiFile1 = findFile(vFile);
      if (psiFile1 != null && fileViewProvider != null && fileViewProvider.isPhysical()) { // might get collected
        PsiFile psi = fileViewProvider.getPsi(fileViewProvider.getBaseLanguage());
        assert psi != null : fileViewProvider +"; "+fileViewProvider.getBaseLanguage()+"; "+psiFile1;
        assert psiFile1.getClass().equals(psi.getClass()) : psiFile1 +"; "+psi + "; "+psiFile1.getClass() +"; "+psi.getClass();
      }
    }

    HashMap<VirtualFile, PsiDirectory> fileToPsiDirMap = new HashMap<VirtualFile, PsiDirectory>(myVFileToPsiDirMap);
    myVFileToPsiDirMap.clear();

    for (VirtualFile vFile : fileToPsiDirMap.keySet()) {
      LOG.assertTrue(vFile.isValid());
      PsiDirectory psiDir1 = findDirectory(vFile);
      LOG.assertTrue(psiDir1 != null);

      VirtualFile parent = vFile.getParent();
      if (parent != null) {
        LOG.assertTrue(myVFileToPsiDirMap.containsKey(parent));
      }
    }
  }

  @Override
  @Nullable
  public PsiFile findFile(@NotNull VirtualFile vFile) {
    if (vFile.isDirectory()) return null;
    final Project project = myManager.getProject();
    if (project.isDefault()) return null;

    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!vFile.isValid()) {
      LOG.error("Invalid file: " + vFile);
      return null;
    }

    dispatchPendingEvents();
    final FileViewProvider viewProvider = findViewProvider(vFile);
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  @Override
  @Nullable
  public PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LOG.assertTrue(vFile.isValid(), "Invalid file");
    if (myDisposed) {
      LOG.error("Project is already disposed: " + myManager.getProject());
    }
    if (!myInitialized) return null;

    dispatchPendingEvents();

    return getCachedPsiFileInner(vFile);
  }

  @Override
  @Nullable
  public PsiDirectory findDirectory(@NotNull VirtualFile vFile) {
    LOG.assertTrue(myInitialized, "Access to psi files should be performed only after startup activity");
    if (myDisposed) {
      LOG.error("Access to psi files should not be performed after project disposal: "+myManager.getProject());
    }


    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!vFile.isValid()) {
      LOG.error("File is not valid:" + vFile);
      return null;
    }

    if (!vFile.isDirectory()) return null;
    dispatchPendingEvents();

    return findDirectoryImpl(vFile);
  }

  @Nullable
  private PsiDirectory findDirectoryImpl(@NotNull VirtualFile vFile) {
    PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
    if (psiDir != null) return psiDir;

    if (Registry.is("ide.hide.excluded.files")) {
      if (myFileIndex.isExcludedFile(vFile)) return null;
    }
    else {
      if (myFileIndex.isUnderIgnored(vFile)) return null;
    }

    VirtualFile parent = vFile.getParent();
    if (parent != null) { //?
      findDirectoryImpl(parent);// need to cache parent directory - used for firing events
    }

    psiDir = PsiDirectoryFactory.getInstance(myManager.getProject()).createDirectory(vFile);
    return ConcurrencyUtil.cacheOrGet(myVFileToPsiDirMap, vFile, psiDir);
  }

  public PsiDirectory getCachedDirectory(@NotNull VirtualFile vFile) {
    return myVFileToPsiDirMap.get(vFile);
  }

  void removeFilesAndDirsRecursively(@NotNull VirtualFile vFile) {
    DebugUtil.startPsiModification("removeFilesAndDirsRecursively");
    try {
      VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (file.isDirectory()) {
            myVFileToPsiDirMap.remove(file);
          }
          else {
            FileViewProvider viewProvider = myVFileToViewProviderMap.remove(file);
            if (viewProvider != null) {
              markInvalidated(viewProvider);
            }
          }
          return true;
        }
      });
    }
    finally {
      DebugUtil.finishPsiModification();
    }
  }

  private void markInvalidated(@NotNull FileViewProvider viewProvider) {
    if (viewProvider instanceof SingleRootFileViewProvider) {
      ((SingleRootFileViewProvider)viewProvider).markInvalidated();
    }
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
    if (document != null) {
      ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(myManager.getProject())).associatePsi(document, null);
    }
    virtualFile.putUserData(myPsiHardRefKey, null);
  }

  @Nullable
  PsiFile getCachedPsiFileInner(@NotNull VirtualFile file) {
    FileViewProvider fileViewProvider = myVFileToViewProviderMap.get(file);
    if (fileViewProvider == null) fileViewProvider = file.getUserData(myPsiHardRefKey);
    return fileViewProvider instanceof SingleRootFileViewProvider
           ? ((SingleRootFileViewProvider)fileViewProvider).getCachedPsi(fileViewProvider.getBaseLanguage()) : null;
  }

  @NotNull
  @Override
  public List<PsiFile> getAllCachedFiles() {
    List<PsiFile> files = new ArrayList<PsiFile>();
    for (FileViewProvider provider : myVFileToViewProviderMap.values()) {
      if (provider instanceof SingleRootFileViewProvider) {
        ContainerUtil.addIfNotNull(files, ((SingleRootFileViewProvider)provider).getCachedPsi(provider.getBaseLanguage()));
      }
    }
    return files;
  }

  void removeInvalidFilesAndDirs(boolean useFind) {
    Map<VirtualFile, PsiDirectory> fileToPsiDirMap = new THashMap<VirtualFile, PsiDirectory>(myVFileToPsiDirMap);
    if (useFind) {
      myVFileToPsiDirMap.clear();
    }
    for (Iterator<VirtualFile> iterator = fileToPsiDirMap.keySet().iterator(); iterator.hasNext();) {
      VirtualFile vFile = iterator.next();
      if (!vFile.isValid()) {
        iterator.remove();
      }
      else {
        PsiDirectory psiDir = findDirectory(vFile);
        if (psiDir == null) {
          iterator.remove();
        }
      }
    }
    myVFileToPsiDirMap.clear();
    myVFileToPsiDirMap.putAll(fileToPsiDirMap);

    // note: important to update directories map first - findFile uses findDirectory!
    Map<VirtualFile, FileViewProvider> fileToPsiFileMap = new THashMap<VirtualFile, FileViewProvider>(myVFileToViewProviderMap);
    Map<VirtualFile, FileViewProvider> originalFileToPsiFileMap = new THashMap<VirtualFile, FileViewProvider>(myVFileToViewProviderMap);
    if (useFind) {
      myVFileToViewProviderMap.clear();
    }
    for (Iterator<VirtualFile> iterator = fileToPsiFileMap.keySet().iterator(); iterator.hasNext();) {
      VirtualFile vFile = iterator.next();

      if (!vFile.isValid()) {
        iterator.remove();
        continue;
      }

      if (useFind) {
        FileViewProvider view = fileToPsiFileMap.get(vFile);
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
    }
    myVFileToViewProviderMap.clear();
    myVFileToViewProviderMap.putAll(fileToPsiFileMap);

    markInvalidations(originalFileToPsiFileMap);
  }

  static boolean areViewProvidersEquivalent(@NotNull FileViewProvider view1, @NotNull FileViewProvider view2) {
    if (view1.getClass() != view2.getClass() || view1.getFileType() != view2.getFileType()) return false;

    Language baseLanguage = view1.getBaseLanguage();
    if (baseLanguage != view2.getBaseLanguage()) return false;

    if (!view1.getLanguages().equals(view2.getLanguages())) return false;
    PsiFile psi1 = view1.getPsi(baseLanguage);
    PsiFile psi2 = view2.getPsi(baseLanguage);
    if (psi1 == null) return psi2 == null;
    if (psi1.getClass() != psi2.getClass()) return false;

    return true;
  }

  private void markInvalidations(@NotNull Map<VirtualFile, FileViewProvider> originalFileToPsiFileMap) {
    DebugUtil.startPsiModification(null);
    try {
      for (Map.Entry<VirtualFile, FileViewProvider> entry : originalFileToPsiFileMap.entrySet()) {
        FileViewProvider viewProvider = entry.getValue();
        if (myVFileToViewProviderMap.get(entry.getKey()) != viewProvider) {
          markInvalidated(viewProvider);
        }
      }
    }
    finally {
      DebugUtil.finishPsiModification();
    }
  }

  @Override
  public void reloadFromDisk(@NotNull PsiFile file) {
    reloadFromDisk(file, false);
  }

  void reloadFromDisk(@NotNull PsiFile file, boolean ignoreDocument) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    VirtualFile vFile = file.getVirtualFile();
    assert vFile != null;

    if (file instanceof PsiBinaryFile) return;
    FileDocumentManager fileDocumentManager = myFileDocumentManager;
    Document document = fileDocumentManager.getCachedDocument(vFile);
    if (document != null && !ignoreDocument){
      fileDocumentManager.reloadFromDisk(document);
    }
    else {
      FileViewProvider latestProvider = createFileViewProvider(vFile, false);
      if (latestProvider.getPsi(latestProvider.getBaseLanguage()) instanceof PsiBinaryFile) {
        forceReload(vFile);
        return;
      }

      FileViewProvider viewProvider = file.getViewProvider();
      if (viewProvider instanceof SingleRootFileViewProvider) {
        ((SingleRootFileViewProvider)viewProvider).onContentReload();
      } else {
        LOG.error("Invalid view provider: " + viewProvider + " of " + viewProvider.getClass());
      }
    }
  }
}
