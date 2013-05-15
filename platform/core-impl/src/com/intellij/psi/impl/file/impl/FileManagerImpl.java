/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.ContentBasedFileSubstitutor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentSoftValueHashMap;
import com.intellij.util.containers.ConcurrentWeakValueHashMap;
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

  private final PsiManagerImpl myManager;
  private final FileIndexFacade myFileIndex;

  private final ConcurrentMap<VirtualFile, PsiDirectory> myVFileToPsiDirMap = new ConcurrentSoftValueHashMap<VirtualFile, PsiDirectory>();
  private final ConcurrentMap<VirtualFile, FileViewProvider> myVFileToViewProviderMap = new ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider>();

  private boolean myInitialized = false;
  private boolean myDisposed = false;

  private final FileDocumentManager myFileDocumentManager;
  private final MessageBusConnection myConnection;
  @SuppressWarnings("UnusedDeclaration")
  private final LowMemoryWatcher myLowMemoryWatcher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      processQueue();
    }
  });

  public FileManagerImpl(PsiManagerImpl manager, FileDocumentManager fileDocumentManager, FileIndexFacade fileIndex) {
    myManager = manager;
    myFileIndex = fileIndex;
    myConnection = manager.getProject().getMessageBus().connect();

    myFileDocumentManager = fileDocumentManager;

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
    Disposer.register(manager.getProject(), this);
  }

  private static final VirtualFile NULL = new LightVirtualFile();

  public void processQueue() {
    // just to call processQueue()
    myVFileToViewProviderMap.remove(NULL);
  }

  @TestOnly
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

          for (Language language : provider.getLanguages()) {
            final PsiFile psi = provider.getPsi(language);
            if (psi instanceof PsiFileImpl) {
              ((PsiFileImpl)psi).clearCaches();
            }
          }
        }
        removeInvalidFilesAndDirs(false);
      }
    });
  }

  @Override
  public void dispose() {
    if (myInitialized) {
      myConnection.disconnect();
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myDisposed = true;
  }

  @Override
  @TestOnly
  public void cleanupForNextTest() {
    myVFileToViewProviderMap.clear();
    myVFileToPsiDirMap.clear();
    processQueue();
  }

  @Override
  @NotNull
  public FileViewProvider findViewProvider(@NotNull final VirtualFile file) {
    assert !file.isDirectory();
    FileViewProvider viewProvider = getFromInjected(file);
    if (viewProvider != null) return viewProvider;
    viewProvider = myVFileToViewProviderMap.get(file);
    if(viewProvider == null) {
      viewProvider = ConcurrencyUtil.cacheOrGet(myVFileToViewProviderMap, file, createFileViewProvider(file, true));
    }
    return viewProvider;
  }

  @Override
  public FileViewProvider findCachedViewProvider(@NotNull final VirtualFile file) {
    FileViewProvider viewProvider = getFromInjected(file);
    if (viewProvider != null) return viewProvider;
    return myVFileToViewProviderMap.get(file);
  }

  @Nullable
  private FileViewProvider getFromInjected(VirtualFile file) {
    if (file instanceof VirtualFileWindow) {
      DocumentWindow document = ((VirtualFileWindow)file).getDocumentWindow();
      PsiFile psiFile = PsiDocumentManager.getInstance(myManager.getProject()).getCachedPsiFile(document);
      if (psiFile == null) return null;
      return psiFile.getViewProvider();
    }
    return null;
  }

  @Override
  public void setViewProvider(@NotNull final VirtualFile virtualFile, final FileViewProvider fileViewProvider) {
    if (!(virtualFile instanceof VirtualFileWindow)) {
      if (fileViewProvider == null) {
        myVFileToViewProviderMap.remove(virtualFile);
      }
      else {
        myVFileToViewProviderMap.put(virtualFile, fileViewProvider);
      }
    }
  }

  @Override
  @NotNull
  public FileViewProvider createFileViewProvider(@NotNull final VirtualFile file, boolean physical) {
    Language language = getLanguage(file);
    final FileViewProviderFactory factory = language == null
                                            ? FileTypeFileViewProviders.INSTANCE.forFileType(file.getFileType())
                                            : LanguageFileViewProviders.INSTANCE.forLanguage(language);
    FileViewProvider viewProvider = factory == null ? null : factory.createFileViewProvider(file, language, myManager, physical);

    return viewProvider == null ? new SingleRootFileViewProvider(myManager, file, physical) : viewProvider;
  }

  @Nullable
  private Language getLanguage(final VirtualFile file) {
    final FileType fileType = file.getFileType();
    Project project = myManager.getProject();
    if (fileType instanceof LanguageFileType) {
      return LanguageSubstitutors.INSTANCE.substituteLanguage(((LanguageFileType)fileType).getLanguage(), file, project);
    }
    // Define language for binary file
    final ContentBasedFileSubstitutor[] processors = Extensions.getExtensions(ContentBasedFileSubstitutor.EP_NAME);
    for (ContentBasedFileSubstitutor processor : processors) {
      Language language = processor.obtainLanguageForFile(file);
      if (language != null) {
        return language;
      }
    }

    return null;
  }

  public void markInitialized() {
    LOG.assertTrue(!myInitialized);
    myDisposed = false;
    myInitialized = true;
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

      event = new PsiTreeChangeEventImpl(myManager);
      event.setPropertyName(PsiTreeChangeEvent.PROP_FILE_TYPES);
      myManager.propertyChanged(event);
    }
  }

  private boolean myProcessingFileTypesChange = false;
  private void handleFileTypesChange(final FileTypesChanged runnable) {
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
    HashMap<VirtualFile, FileViewProvider> fileToViewProvider = new HashMap<VirtualFile, FileViewProvider>(myVFileToViewProviderMap);
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
    }

    if (!vFile.isDirectory()) return null;
    dispatchPendingEvents();

    return findDirectoryImpl(vFile);
  }

  @Nullable
  private PsiDirectory findDirectoryImpl(final VirtualFile vFile) {
    PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
    if (psiDir != null) return psiDir;

    if (myFileIndex.isExcludedFile(vFile)) return null;

    VirtualFile parent = vFile.getParent();
    if (parent != null) { //?
      findDirectoryImpl(parent);// need to cache parent directory - used for firing events
    }

    psiDir = PsiDirectoryFactory.getInstance(myManager.getProject()).createDirectory(vFile);
    return ConcurrencyUtil.cacheOrGet(myVFileToPsiDirMap, vFile, psiDir);
  }

  PsiDirectory getCachedDirectory(VirtualFile vFile) {
    return myVFileToPsiDirMap.get(vFile);
  }

  void cacheViewProvider(@NotNull VirtualFile vFile, FileViewProvider viewProvider) {
    if (viewProvider == null) {
      removeCachedViewProvider(vFile);
    }
    else {
      myVFileToViewProviderMap.put(vFile, viewProvider);
    }
  }

  void removeCachedViewProvider(@NotNull VirtualFile vFile) {
    myVFileToViewProviderMap.remove(vFile);
  }

  void removeFilesAndDirsRecursively(VirtualFile vFile) {
    VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
          myVFileToPsiDirMap.remove(file);
        }
        else {
          myVFileToViewProviderMap.remove(file);
        }
        return true;
      }
    });
  }

  @Nullable
  PsiFile getCachedPsiFileInner(VirtualFile file) {
    final FileViewProvider fileViewProvider = myVFileToViewProviderMap.get(file);
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

        PsiFile psi = view.getPsi(view.getBaseLanguage());
        if (psi == null || !psiFile1.getClass().equals(psi.getClass()) ||
             psiFile1.getViewProvider().getBaseLanguage() != view.getBaseLanguage() // e.g. JSP <-> JSPX
           ) {
          iterator.remove();
        }
        else if (psi instanceof PsiFileImpl) {
          ((PsiFileImpl)psi).clearCaches();
        }
      }
    }
    myVFileToViewProviderMap.clear();
    myVFileToViewProviderMap.putAll(fileToPsiFileMap);
  }

  @Override
  public void reloadFromDisk(@NotNull PsiFile file) {
    reloadFromDisk(file, false);
  }

  void reloadFromDisk(PsiFile file, boolean ignoreDocument) {
    VirtualFile vFile = file.getVirtualFile();
    assert vFile != null;

    if (file instanceof PsiBinaryFile) return;
    FileDocumentManager fileDocumentManager = myFileDocumentManager;
    Document document = fileDocumentManager.getCachedDocument(vFile);
    if (document != null && !ignoreDocument){
      fileDocumentManager.reloadFromDisk(document);
    }
    else{
      PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
      event.setParent(file);
      event.setFile(file);
      if (file instanceof PsiFileImpl && ((PsiFileImpl)file).isContentsLoaded()) {
        event.setOffset(0);
        event.setOldLength(file.getTextLength());
      }
      myManager.beforeChildrenChange(event);

      if (file instanceof PsiFileEx) {
        ((PsiFileEx)file).onContentReload();
      }

      myManager.childrenChanged(event);
    }
  }
}
