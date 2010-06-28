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

package com.intellij.psi.impl.file.impl;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentWeakValueHashMap;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class FileManagerImpl implements FileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.impl.FileManagerImpl");

  private final PsiManagerImpl myManager;

  private final FileTypeManager myFileTypeManager;

  private final ProjectRootManager myProjectRootManager;
  private ProjectFileIndex myProjectFileIndex = null;

  private final ConcurrentMap<VirtualFile, PsiDirectory> myVFileToPsiDirMap = new ConcurrentHashMap<VirtualFile, PsiDirectory>();
  private final ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider> myVFileToViewProviderMap = new ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider>();

  private boolean myInitialized = false;
  private boolean myDisposed = false;

  private final FileDocumentManager myFileDocumentManager;
  private final MessageBusConnection myConnection;

  public FileManagerImpl(PsiManagerImpl manager,
                         FileTypeManager fileTypeManager,
                         FileDocumentManager fileDocumentManager,
                         ProjectRootManager projectRootManager) {
    myFileTypeManager = fileTypeManager;
    myManager = manager;
    myConnection = manager.getProject().getMessageBus().connect();

    myFileDocumentManager = fileDocumentManager;
    myProjectRootManager = projectRootManager;

    myConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {

      public void enteredDumbMode() {
        recalcAllViewProviders();
      }

      public void exitDumbMode() {
        recalcAllViewProviders();
      }
    });
  }

  public void processQueue() {
    myVFileToViewProviderMap.processQueue();
  }

  @TestOnly
  public ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider> getVFileToViewProviderMap() {
    return myVFileToViewProviderMap;
  }

  private void recalcAllViewProviders() {
    handleFileTypesChange(new FileTypesChanged() {
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

  public void dispose() {
    if (myInitialized) {
      myConnection.disconnect();
    }
    myDisposed = true;
  }

  public void cleanupForNextTest() {
    myVFileToViewProviderMap.clear();
    myVFileToPsiDirMap.clear();
    processQueue();
  }

  @NotNull
  public FileViewProvider findViewProvider(@NotNull final VirtualFile file) {
    FileViewProvider viewProvider = getFromInjected(file);
    if (viewProvider != null) return viewProvider;
    viewProvider = myVFileToViewProviderMap.get(file);
    if(viewProvider == null) {
      viewProvider = ConcurrencyUtil.cacheOrGet(myVFileToViewProviderMap, file, createFileViewProvider(file, true));
    }
    return viewProvider;
  }

  public FileViewProvider findCachedViewProvider(@NotNull final VirtualFile file) {
    FileViewProvider viewProvider = getFromInjected(file);
    if (viewProvider != null) return viewProvider;
    return myVFileToViewProviderMap.get(file);
  }

  private FileViewProvider getFromInjected(VirtualFile file) {
    if (file instanceof VirtualFileWindow) {
      DocumentWindow document = ((VirtualFileWindow)file).getDocumentWindow();
      PsiFile psiFile = PsiDocumentManagerImpl.getInstance(myManager.getProject()).getCachedPsiFile(document);
      if (psiFile == null) return null;
      return psiFile.getViewProvider();
    }
    return null;
  }

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
    final ContentBasedClassFileProcessor[] processors = Extensions.getExtensions(ContentBasedClassFileProcessor.EP_NAME);
    for (ContentBasedClassFileProcessor processor : processors) {
      Language language = processor.obtainLanguageForFile(file);
      if (language != null) {
        return language;
      }
    }

    return null;
  }

  public void runStartupActivity() {
    LOG.assertTrue(!myInitialized);
    myDisposed = false;
    myInitialized = true;

    myProjectFileIndex = myProjectRootManager.getFileIndex();

    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new MyVirtualFileListener()));

    myConnection.subscribe(AppTopics.FILE_TYPES, new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {}

      public void fileTypesChanged(FileTypeEvent e) {
        handleFileTypesChange(new FileTypesChanged() {
          protected void updateMaps() {
            removeInvalidFilesAndDirs(true);
          }
        });
      }
    });

    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyModuleRootListener());
    myConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new MyFileDocumentManagerAdapter());
  }

  private abstract class FileTypesChanged implements Runnable {
    protected abstract void updateMaps();

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

  private void dispatchPendingEvents() {
    if (!myInitialized) {
      LOG.error("Project is not yet initialized");
    }
    if (myDisposed) {
      LOG.error("Project is already disposed");
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
        assert psiFile1.getClass().equals(fileViewProvider.getPsi(fileViewProvider.getBaseLanguage()).getClass());
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

  @Nullable
  public PsiFile findFile(@NotNull VirtualFile vFile) {
    if (vFile.isDirectory()) return null;
    final ProjectEx project = (ProjectEx)myManager.getProject();
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

  @Nullable
  public PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LOG.assertTrue(vFile.isValid());
    LOG.assertTrue(!myDisposed);
    if (!myInitialized) return null;

    dispatchPendingEvents();

    return getCachedPsiFileInner(vFile);
  }

  @NotNull
  public GlobalSearchScope getResolveScope(@NotNull PsiElement element) {
    ProgressManager.checkCanceled();

    VirtualFile vFile;
    final Project project = myManager.getProject();
    if (element instanceof PsiDirectory) {
      vFile = ((PsiDirectory)element).getVirtualFile();
    }
    else {
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile instanceof PsiCodeFragment) {
        final GlobalSearchScope forcedScope = ((PsiCodeFragment)containingFile).getForcedResolveScope();
        if (forcedScope != null) {
          return forcedScope;
        }
        final PsiElement context = containingFile.getContext();
        if (context == null) {
          return GlobalSearchScope.allScope(project);
        }
        return getResolveScope(context);
      }

      final PsiFile contextFile = containingFile != null ? FileContextUtil.getContextFile(containingFile) : null;
      if (contextFile == null) {
        return GlobalSearchScope.allScope(project);
      }
      else if (contextFile instanceof FileResolveScopeProvider) {
        return ((FileResolveScopeProvider) contextFile).getFileResolveScope();
      }
      vFile = contextFile.getOriginalFile().getVirtualFile();
    }
    if (vFile == null) {
      return GlobalSearchScope.allScope(project);
    }

    return getDefaultResolveScope(vFile);
  }

  public GlobalSearchScope getDefaultResolveScope(VirtualFile vFile) {
    GlobalSearchScope scope = getInherentResolveScope(vFile);
    for (ResolveScopeEnlarger enlarger : ResolveScopeEnlarger.EP_NAME.getExtensions()) {
      final SearchScope extra = enlarger.getAdditionalResolveScope(vFile, myManager.getProject());
      if (extra != null) {
        scope = scope.union(extra);
      }
    }
    return scope;
  }

  private GlobalSearchScope getInherentResolveScope(VirtualFile vFile) {
    ProjectFileIndex projectFileIndex = myProjectRootManager.getFileIndex();
    Module module = projectFileIndex.getModuleForFile(vFile);
    if (module != null) {
      boolean includeTests = projectFileIndex.isInTestSourceContent(vFile) ||
                             !projectFileIndex.isContentJavaSourceFile(vFile);
      return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, includeTests);
    }
    else {
      // resolve references in libraries in context of all modules which contain it
      List<Module> modulesLibraryUsedIn = new ArrayList<Module>();
      List<OrderEntry> orderEntries = projectFileIndex.getOrderEntriesForFile(vFile);

      for (OrderEntry entry : orderEntries) {
        ProgressManager.checkCanceled();

        if (entry instanceof JdkOrderEntry) {
          return ((ProjectRootManagerEx)myProjectRootManager).getScopeForJdk((JdkOrderEntry)entry);
        }

        if (entry instanceof LibraryOrderEntry || entry instanceof ModuleOrderEntry) {
          modulesLibraryUsedIn.add(entry.getOwnerModule());
        }
      }

      return ((ProjectRootManagerEx)myProjectRootManager).getScopeForLibraryUsedIn(modulesLibraryUsedIn);
    }
  }

  @NotNull
  public GlobalSearchScope getUseScope(@NotNull PsiElement element) {
    VirtualFile vFile;
    final GlobalSearchScope allScope = GlobalSearchScope.allScope(myManager.getProject());
    if (element instanceof PsiDirectory) {
      vFile = ((PsiDirectory)element).getVirtualFile();
    }
    else {
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) return allScope;
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return allScope;
      vFile = virtualFile.getParent();
    }

    if (vFile == null) return allScope;
    ProjectFileIndex projectFileIndex = myProjectRootManager.getFileIndex();
    Module module = projectFileIndex.getModuleForFile(vFile);
    if (module != null) {
      boolean isTest = projectFileIndex.isInTestSourceContent(vFile);
      return isTest
             ? GlobalSearchScope.moduleTestsWithDependentsScope(module)
             : GlobalSearchScope.moduleWithDependentsScope(module);
    }
    else {
      final PsiFile f = element.getContainingFile();
      final VirtualFile vf = f == null ? null : f.getVirtualFile();

      return f == null || vf == null || vf.isDirectory() || allScope.contains(vf)
             ? allScope : GlobalSearchScope.fileScope(f).uniteWith(allScope);
    }
  }


  @Nullable
  private PsiFile createFileCopyWithNewName(VirtualFile vFile, String name) {
    // TODO[ik] remove this. Event handling and generation must be in view providers mechanism since we
    // need to track changes in _all_ psi views (e.g. namespace changes in XML)
    final FileTypeManager instance = FileTypeManager.getInstance();
    if(instance.isFileIgnored(name)) return null;
    final FileType fileTypeByFileName = instance.getFileTypeByFileName(name);
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    return PsiFileFactory.getInstance(myManager.getProject()).createFileFromText(name, fileTypeByFileName,
                                                                                 document != null ? document.getCharsSequence() : "", vFile.getModificationStamp(),
                                                                                 true, false);
  }

  @Nullable
  public PsiDirectory findDirectory(@NotNull VirtualFile vFile) {
    LOG.assertTrue(myInitialized, "Access to psi files should be performed only after startup activity");
    LOG.assertTrue(!myDisposed, "Access to psi files should not be performed after disposal");

    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!vFile.isValid()) {
      LOG.error("File is not valid:" + vFile.getName());
    }

    if (!vFile.isDirectory()) return null;
    dispatchPendingEvents();

    return findDirectoryImpl(vFile);
  }

  private PsiDirectory findDirectoryImpl(final VirtualFile vFile) {
    PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
    if (psiDir != null) return psiDir;

    if (myProjectRootManager.getFileIndex().isIgnored(vFile)) return null;

    VirtualFile parent = vFile.getParent();
    if (parent != null) { //?
      findDirectoryImpl(parent);// need to cache parent directory - used for firing events
    }

    psiDir = PsiDirectoryFactory.getInstance(myManager.getProject()).createDirectory(vFile);
    return ConcurrencyUtil.cacheOrGet(myVFileToPsiDirMap, vFile, psiDir);
  }


  @Nullable
  private PsiFile getCachedPsiFileInner(VirtualFile file) {
    final FileViewProvider fileViewProvider = myVFileToViewProviderMap.get(file);
    return fileViewProvider instanceof SingleRootFileViewProvider
           ? ((SingleRootFileViewProvider)fileViewProvider).getCachedPsi(fileViewProvider.getBaseLanguage()) : null;
  }

  public List<PsiFile> getAllCachedFiles() {
    List<PsiFile> files = new ArrayList<PsiFile>();
    for (FileViewProvider provider : myVFileToViewProviderMap.values()) {
      if (provider instanceof SingleRootFileViewProvider) {
        files.add(((SingleRootFileViewProvider)provider).getCachedPsi(provider.getBaseLanguage()));
      }
    }
    return files;
  }

  private void removeInvalidFilesAndDirs(boolean useFind) {
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

  public void reloadFromDisk(@NotNull PsiFile file) {
    reloadFromDisk(file, false);
  }

  private void reloadFromDisk(PsiFile file, boolean ignoreDocument) {
    VirtualFile vFile = file.getVirtualFile();
    assert vFile != null;

    if (!(file instanceof PsiBinaryFile)) {
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

  private class MyVirtualFileListener extends VirtualFileAdapter {
    public void contentsChanged(final VirtualFileEvent event) {
      // handled by FileDocumentManagerListener
    }

    public void fileCreated(VirtualFileEvent event) {
      final VirtualFile vFile = event.getFile();

      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          public void run() {
            VirtualFile parent = vFile.getParent();
            PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);
            if (parentDir == null) return; // do not notifyListeners event if parent directory was never accessed via PSI

            if (!vFile.isDirectory()) {
              PsiFile psiFile = findFile(vFile);
              if (psiFile != null) {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                myManager.beforeChildAddition(treeEvent);
                treeEvent.setChild(psiFile);
                myManager.childAdded(treeEvent);
              }
            }
            else {
              PsiDirectory psiDir = findDirectory(vFile);
              if (psiDir != null) {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                myManager.beforeChildAddition(treeEvent);
                treeEvent.setChild(psiDir);
                myManager.childAdded(treeEvent);
              }
            }
          }
        }
      );
    }

    public void beforeFileDeletion(VirtualFileEvent event) {
      final VirtualFile vFile = event.getFile();

      VirtualFile parent = vFile.getParent();
      final PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);
      if (parentDir == null) return; // do not notify listeners if parent directory was never accessed via PSI

      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          public void run() {
            if (!vFile.isDirectory()) {
              PsiFile psiFile = getCachedPsiFile(vFile);
              if (psiFile != null) {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                treeEvent.setChild(psiFile);
                myManager.beforeChildRemoval(treeEvent);
              }
            }
            else {
              PsiDirectory psiDir = findDirectory(vFile);
              if (psiDir != null) {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                treeEvent.setChild(psiDir);
                myManager.beforeChildRemoval(treeEvent);
              }
            }
          }
        }
      );
    }

    public void fileDeleted(final VirtualFileEvent event) {
      final VirtualFile vFile = event.getFile();

      VirtualFile parent = event.getParent();
      final PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);

      final PsiFile psiFile = getCachedPsiFileInner(vFile);
      if (psiFile != null) {
        myVFileToViewProviderMap.remove(vFile);

        if (parentDir != null) {
          ApplicationManager.getApplication().runWriteAction(new ExternalChangeAction() {
            public void run() {
              PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
              treeEvent.setParent(parentDir);
              treeEvent.setChild(psiFile);
              myManager.childRemoved(treeEvent);
            }
          });
        }
      }
      else {
        final PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
        if (psiDir != null) {
          removeInvalidFilesAndDirs(false);

          if (parentDir != null) {
            ApplicationManager.getApplication().runWriteAction(new ExternalChangeAction() {
              public void run() {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                treeEvent.setChild(psiDir);
                myManager.childRemoved(treeEvent);
              }
            });
          }
        }
      }
    }

    public void beforePropertyChange(final VirtualFilePropertyEvent event) {
      final VirtualFile vFile = event.getFile();
      final String propertyName = event.getPropertyName();

      VirtualFile parent = vFile.getParent();
      final PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);
      if (parentDir == null) return; // do not notifyListeners event if parent directory was never accessed via PSI

      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          public void run() {
            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            treeEvent.setParent(parentDir);

            if (VirtualFile.PROP_NAME.equals(propertyName)) {
              final String newName = (String)event.getNewValue();

              if (vFile.isDirectory()) {
                PsiDirectory psiDir = findDirectory(vFile);
                if (psiDir != null) {
                  if (!myFileTypeManager.isFileIgnored(newName)) {
                    treeEvent.setChild(psiDir);
                    treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
                    treeEvent.setOldValue(vFile.getName());
                    treeEvent.setNewValue(newName);
                    myManager.beforePropertyChange(treeEvent);
                  }
                  else {
                    treeEvent.setChild(psiDir);
                    myManager.beforeChildRemoval(treeEvent);
                  }
                }
                else {
                  if (!isExcludeRoot(vFile) && !myFileTypeManager.isFileIgnored(newName)) {
                    myManager.beforeChildAddition(treeEvent);
                  }
                }
              }
              else {
                final FileViewProvider viewProvider = findViewProvider(vFile);
                PsiFile psiFile = viewProvider.getPsi(viewProvider.getBaseLanguage());
                PsiFile psiFile1 = createFileCopyWithNewName(vFile, newName);

                if (psiFile != null) {
                  if (psiFile1 == null) {
                    treeEvent.setChild(psiFile);
                    myManager.beforeChildRemoval(treeEvent);
                  }
                  else if (!psiFile1.getClass().equals(psiFile.getClass())) {
                    treeEvent.setOldChild(psiFile);
                    myManager.beforeChildReplacement(treeEvent);
                  }
                  else {
                    treeEvent.setChild(psiFile);
                    treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_FILE_NAME);
                    treeEvent.setOldValue(vFile.getName());
                    treeEvent.setNewValue(newName);
                    myManager.beforePropertyChange(treeEvent);
                  }
                }
                else {
                  if (psiFile1 != null) {
                    myManager.beforeChildAddition(treeEvent);
                  }
                }
              }
            }
            else if (VirtualFile.PROP_WRITABLE.equals(propertyName)) {
              PsiFile psiFile = getCachedPsiFileInner(vFile);
              if (psiFile == null) return;

              treeEvent.setElement(psiFile);
              treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_WRITABLE);
              treeEvent.setOldValue(event.getOldValue());
              treeEvent.setNewValue(event.getNewValue());
              myManager.beforePropertyChange(treeEvent);
            }
          }
        }
      );
    }

    private boolean isExcludeRoot(VirtualFile file) {
      VirtualFile parent = file.getParent();
      if (parent == null) return false;

      Module module = myProjectRootManager.getFileIndex().getModuleForFile(parent);
      if (module == null) return false;
      VirtualFile[] excludeRoots = ModuleRootManager.getInstance(module).getExcludeRoots();
      for (VirtualFile root : excludeRoots) {
        if (root.equals(file)) return true;
      }
      return false;
    }

    public void propertyChanged(final VirtualFilePropertyEvent event) {
      final String propertyName = event.getPropertyName();
      final VirtualFile vFile = event.getFile();

      final FileViewProvider oldFileViewProvider = findViewProvider(vFile);
      final PsiFile oldPsiFile;
      if (oldFileViewProvider instanceof SingleRootFileViewProvider) {
        oldPsiFile = ((SingleRootFileViewProvider)oldFileViewProvider).getCachedPsi(oldFileViewProvider.getBaseLanguage());
      }
      else {
        oldPsiFile = null;
      }

      VirtualFile parent = vFile.getParent();
      final PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);
      if (parentDir == null) {
        boolean fire = VirtualFile.PROP_NAME.equals(propertyName) && vFile.isDirectory();
        if (fire) {
          PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
          fire = psiDir != null;
        }
        if (!fire) return; // do not fire event if parent directory was never accessed via PSI
      }

      if (oldPsiFile != null && oldPsiFile.isPhysical()) {
        SmartPointerManagerImpl.fastenBelts(oldPsiFile);
      }
      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          public void run() {
            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            treeEvent.setParent(parentDir);

            if (VirtualFile.PROP_NAME.equals(propertyName)) {
              if (vFile.isDirectory()) {
                PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
                if (psiDir != null) {
                  if (myFileTypeManager.isFileIgnored(vFile.getName())) {
                    removeFilesAndDirsRecursively(vFile);

                    treeEvent.setChild(psiDir);
                    myManager.childRemoved(treeEvent);
                  }
                  else {
                    treeEvent.setElement(psiDir);
                    treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
                    treeEvent.setOldValue(event.getOldValue());
                    treeEvent.setNewValue(event.getNewValue());
                    myManager.propertyChanged(treeEvent);
                  }
                }
                else {
                  PsiDirectory psiDir1 = findDirectory(vFile);
                  if (psiDir1 != null) {
                    treeEvent.setChild(psiDir1);
                    myManager.childAdded(treeEvent);
                  }
                }
              }
              else {
                final FileViewProvider fileViewProvider = createFileViewProvider(vFile, true);
                final PsiFile newPsiFile = fileViewProvider.getPsi(fileViewProvider.getBaseLanguage());
                if(oldPsiFile != null) {
                  if (newPsiFile == null) {
                    myVFileToViewProviderMap.remove(vFile);

                    treeEvent.setChild(oldPsiFile);
                    myManager.childRemoved(treeEvent);
                  }
                  else if (!newPsiFile.getClass().equals(oldPsiFile.getClass()) ||
                           newPsiFile.getFileType() != myFileTypeManager.getFileTypeByFileName((String)event.getOldValue()) ||
                           languageDialectChanged(newPsiFile, (String)event.getOldValue()) ||
                           !oldFileViewProvider.getLanguages().equals(fileViewProvider.getLanguages()) ||
                           FileContentUtil.FORCE_RELOAD_REQUESTOR.equals(event.getRequestor())
                    ) {
                    myVFileToViewProviderMap.put(vFile, fileViewProvider);

                    treeEvent.setOldChild(oldPsiFile);
                    treeEvent.setNewChild(newPsiFile);
                    myManager.childReplaced(treeEvent);
                  }
                  else {
                    treeEvent.setElement(oldPsiFile);
                    treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_FILE_NAME);
                    treeEvent.setOldValue(event.getOldValue());
                    treeEvent.setNewValue(event.getNewValue());
                    myManager.propertyChanged(treeEvent);
                  }
                }
                else if (newPsiFile != null) {
                  myVFileToViewProviderMap.put(vFile, fileViewProvider);
                  treeEvent.setChild(newPsiFile);
                  myManager.childAdded(treeEvent);
                }
              }
            }
            else if (VirtualFile.PROP_WRITABLE.equals(propertyName)) {
              if (oldPsiFile == null) return;

              treeEvent.setElement(oldPsiFile);
              treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_WRITABLE);
              treeEvent.setOldValue(event.getOldValue());
              treeEvent.setNewValue(event.getNewValue());
              myManager.propertyChanged(treeEvent);
            }
          }
        }
      );
    }

    public void beforeFileMovement(VirtualFileMoveEvent event) {
      final VirtualFile vFile = event.getFile();

      final PsiDirectory oldParentDir = findDirectory(event.getOldParent());
      final PsiDirectory newParentDir = findDirectory(event.getNewParent());
      if (oldParentDir == null && newParentDir == null) return;
      if (myFileTypeManager.isFileIgnored(vFile.getName())) return;

      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          public void run() {
            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);

            boolean isExcluded = vFile.isDirectory() && myProjectFileIndex.isIgnored(vFile);
            if (oldParentDir != null && !isExcluded) {
              if (newParentDir != null) {
                treeEvent.setOldParent(oldParentDir);
                treeEvent.setNewParent(newParentDir);
                if (vFile.isDirectory()) {
                  PsiDirectory psiDir = findDirectory(vFile);
                  treeEvent.setChild(psiDir);
                }
                else {
                  PsiFile psiFile = findFile(vFile);
                  treeEvent.setChild(psiFile);
                }
                myManager.beforeChildMovement(treeEvent);
              }
              else {
                treeEvent.setParent(oldParentDir);
                if (vFile.isDirectory()) {
                  PsiDirectory psiDir = findDirectory(vFile);
                  treeEvent.setChild(psiDir);
                }
                else {
                  PsiFile psiFile = findFile(vFile);
                  treeEvent.setChild(psiFile);
                }
                myManager.beforeChildRemoval(treeEvent);
              }
            }
            else {
              LOG.assertTrue(newParentDir != null); // checked above
              treeEvent.setParent(newParentDir);
              myManager.beforeChildAddition(treeEvent);
            }
          }
        }
      );
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      final VirtualFile vFile = event.getFile();

      final PsiDirectory oldParentDir = findDirectory(event.getOldParent());
      final PsiDirectory newParentDir = findDirectory(event.getNewParent());
      if (oldParentDir == null && newParentDir == null) return;

      final PsiElement oldElement = vFile.isDirectory() ? myVFileToPsiDirMap.get(vFile) : getCachedPsiFileInner(vFile);
      removeInvalidFilesAndDirs(true);
      final FileViewProvider viewProvider = findViewProvider(vFile);
      final PsiElement newElement;
      final FileViewProvider newViewProvider;
      if (!vFile.isDirectory()){
        newViewProvider = createFileViewProvider(vFile, true);
        newElement = newViewProvider.getPsi(viewProvider.getBaseLanguage());
      }
      else {
        newElement = findDirectory(vFile);
        newViewProvider = null;
      }

      if (oldElement == null && newElement == null) return;

      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          public void run() {
            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            if (oldElement != null) {
              if (newElement != null) {
                if (!oldElement.getClass().equals(oldElement.getClass())) {
                  myVFileToViewProviderMap.put(vFile, newViewProvider);
                  PsiTreeChangeEventImpl treeRemoveEvent = new PsiTreeChangeEventImpl(myManager);
                  treeRemoveEvent.setParent(oldParentDir);
                  treeRemoveEvent.setChild(oldElement);
                  myManager.childRemoved(treeRemoveEvent);
                  PsiTreeChangeEventImpl treeAddEvent = new PsiTreeChangeEventImpl(myManager);
                  treeAddEvent.setParent(newParentDir);
                  treeAddEvent.setChild(newElement);
                  myManager.childAdded(treeAddEvent);
                }
                else {
                  treeEvent.setOldParent(oldParentDir);
                  treeEvent.setNewParent(newParentDir);
                  treeEvent.setChild(newElement);
                  myManager.childMoved(treeEvent);
                }
              }
              else {
                myVFileToViewProviderMap.remove(vFile);
                treeEvent.setParent(oldParentDir);
                treeEvent.setChild(oldElement);
                myManager.childRemoved(treeEvent);
              }
            }
            else {
              myVFileToViewProviderMap.put(vFile, newViewProvider);
              LOG.assertTrue(newElement != null); // checked above
              treeEvent.setParent(newParentDir);
              treeEvent.setChild(newElement);
              myManager.childAdded(treeEvent);
            }
          }
        }
      );
    }

    private void removeFilesAndDirsRecursively(VirtualFile vFile) {
      if (vFile.isDirectory()) {
        myVFileToPsiDirMap.remove(vFile);

        VirtualFile[] children = vFile.getChildren();
        for (VirtualFile child : children) {
          removeFilesAndDirsRecursively(child);
        }
      }
      else myVFileToViewProviderMap.remove(vFile);
    }
  }

  // When file is renamed so that extension changes then language dialect might change and thus psiFile should be invalidated
  // We could detect it right now with checks of parser definition equivalence
  // The file name under passed psi file is "new" but parser def is from old name
  private static boolean languageDialectChanged(final PsiFile newPsiFile, String oldFileName) {
    return newPsiFile instanceof PsiFileBase
           && LanguageParserDefinitions.INSTANCE.forLanguage(newPsiFile.getLanguage()).getClass() == ((PsiFileBase)newPsiFile).getParserDefinition().getClass()
           && !FileUtil.getExtension(newPsiFile.getName()).equals(FileUtil.getExtension(oldFileName));
  }

  private class MyModuleRootListener implements ModuleRootListener {
    private VirtualFile[] myOldContentRoots = null;
    private volatile int depthCounter = 0;
    public void beforeRootsChange(final ModuleRootEvent event) {
      if (!myInitialized) return;
      if (event.isCausedByFileTypesChange()) return;
      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          public void run() {
            depthCounter++;
            if (depthCounter > 1) return;

            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_ROOTS);
            final VirtualFile[] contentRoots = myProjectRootManager.getContentRoots();
            LOG.assertTrue(myOldContentRoots == null);
            myOldContentRoots = contentRoots;
            treeEvent.setOldValue(contentRoots);
            myManager.beforePropertyChange(treeEvent);
          }
        }
      );
    }

    public void rootsChanged(final ModuleRootEvent event) {
      dispatchPendingEvents();

      if (!myInitialized) return;
      if (event.isCausedByFileTypesChange()) return;
      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          public void run() {
            depthCounter--;
            assert depthCounter >= 0 : depthCounter;
            if (depthCounter > 0) return;

            removeInvalidFilesAndDirs(true);

            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_ROOTS);
            final VirtualFile[] contentRoots = myProjectRootManager.getContentRoots();
            treeEvent.setNewValue(contentRoots);
            LOG.assertTrue(myOldContentRoots != null);
            treeEvent.setOldValue(myOldContentRoots);
            myOldContentRoots = null;
            myManager.propertyChanged(treeEvent);
          }
        }
      );
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void dumpFilesWithContentLoaded(Writer out) throws IOException {
    out.write("Files with content loaded cached in FileManagerImpl:\n");
    Set<VirtualFile> vFiles = myVFileToViewProviderMap.keySet();
    for (VirtualFile fileCacheEntry : vFiles) {
      final FileViewProvider view = myVFileToViewProviderMap.get(fileCacheEntry);
      PsiFile psiFile = view.getPsi(view.getBaseLanguage());
      if (psiFile instanceof PsiFileImpl && ((PsiFileImpl)psiFile).isContentsLoaded()) {
        out.write(fileCacheEntry.getPresentableUrl());
        out.write("\n");
      }
    }
  }

  private class MyFileDocumentManagerAdapter extends FileDocumentManagerAdapter {
    public void fileWithNoDocumentChanged(VirtualFile file) {
      final PsiFile psiFile = getCachedPsiFileInner(file);
      if (psiFile != null) {
        ApplicationManager.getApplication().runWriteAction(
          new ExternalChangeAction() {
            public void run() {
              reloadFromDisk(psiFile, true); // important to ignore document which might appear already!
            }
          }
        );
      }
    }
  }
}
