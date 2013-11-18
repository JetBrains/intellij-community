/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.util.FileContentUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiVFSListener extends VirtualFileAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.impl.PsiVFSListener");

  private final FileTypeManager myFileTypeManager;
  private final ProjectRootManager myProjectRootManager;
  private final ProjectFileIndex myProjectFileIndex;
  private final PsiManagerImpl myManager;
  private final FileManagerImpl myFileManager;
  private final MessageBusConnection myConnection;

  public PsiVFSListener(StartupManager startupManager,
                        FileTypeManager fileTypeManager,
                        PsiManager psiManager, 
                        ProjectRootManager projectRootManager) {
    myFileTypeManager = fileTypeManager;
    myProjectRootManager = projectRootManager;
    myProjectFileIndex = myProjectRootManager.getFileIndex();
    myManager = (PsiManagerImpl) psiManager;
    myFileManager = (FileManagerImpl) myManager.getFileManager();

    myConnection = psiManager.getProject().getMessageBus().connect(psiManager.getProject());

    startupManager.registerPreStartupActivity(new Runnable() {
      @Override
      public void run() {
        myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(PsiVFSListener.this));
        myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyModuleRootListener());
        myConnection.subscribe(FileTypeManager.TOPIC, new FileTypeListener.Adapter() {
          @Override
          public void fileTypesChanged(FileTypeEvent e) {
            myFileManager.processFileTypesChanged();
          }
        });
        myConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new MyFileDocumentManagerAdapter());
        myFileManager.markInitialized();
      }
    });
  }

  @Nullable
  private PsiDirectory getCachedDirectory(VirtualFile parent) {
    return parent == null ? null : myFileManager.getCachedDirectory(parent);
  }

  @Override
  public void fileCreated(VirtualFileEvent event) {
    final VirtualFile vFile = event.getFile();

    ApplicationManager.getApplication().runWriteAction(
      new ExternalChangeAction() {
        @Override
        public void run() {
          VirtualFile parent = vFile.getParent();
          PsiDirectory parentDir = getCachedDirectory(parent);
          if (parentDir == null) return; // do not notifyListeners event if parent directory was never accessed via PSI

          if (!vFile.isDirectory()) {
            PsiFile psiFile = myFileManager.findFile(vFile);
            if (psiFile != null && psiFile.getProject() == myManager.getProject()) {
              PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
              treeEvent.setParent(parentDir);
              myManager.beforeChildAddition(treeEvent);
              treeEvent.setChild(psiFile);
              myManager.childAdded(treeEvent);
            }
          }
          else {
            PsiDirectory psiDir = myFileManager.findDirectory(vFile);
            if (psiDir != null && psiDir.getProject() == myManager.getProject()) {
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

  @Override
  public void beforeFileDeletion(VirtualFileEvent event) {
    final VirtualFile vFile = event.getFile();

    VirtualFile parent = vFile.getParent();
    final PsiDirectory parentDir = getCachedDirectory(parent);
    if (parentDir == null) return; // do not notify listeners if parent directory was never accessed via PSI

    ApplicationManager.getApplication().runWriteAction(
      new ExternalChangeAction() {
        @Override
        public void run() {
          if (!vFile.isDirectory()) {
            PsiFile psiFile = myFileManager.getCachedPsiFile(vFile);
            if (psiFile != null) {
              PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
              treeEvent.setParent(parentDir);
              treeEvent.setChild(psiFile);
              myManager.beforeChildRemoval(treeEvent);
            }
          }
          else {
            PsiDirectory psiDir = myFileManager.findDirectory(vFile);
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

  @Override
  public void fileDeleted(final VirtualFileEvent event) {
    final VirtualFile vFile = event.getFile();

    VirtualFile parent = event.getParent();
    final PsiDirectory parentDir = getCachedDirectory(parent);

    final PsiFile psiFile = myFileManager.getCachedPsiFileInner(vFile);
    if (psiFile != null) {
      myFileManager.removeCachedViewProvider(vFile);

      if (parentDir != null) {
        ApplicationManager.getApplication().runWriteAction(new ExternalChangeAction() {
          @Override
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
      final PsiDirectory psiDir = myFileManager.getCachedDirectory(vFile);
      if (psiDir != null) {
        myFileManager.removeInvalidFilesAndDirs(false);

        if (parentDir != null) {
          ApplicationManager.getApplication().runWriteAction(new ExternalChangeAction() {
            @Override
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

  @Override
  public void beforePropertyChange(final VirtualFilePropertyEvent event) {
    final VirtualFile vFile = event.getFile();
    final String propertyName = event.getPropertyName();

    VirtualFile parent = vFile.getParent();
    final PsiDirectory parentDir = getCachedDirectory(parent);
    if (parent != null && parentDir == null) return; // do not notifyListeners event if parent directory was never accessed via PSI

    ApplicationManager.getApplication().runWriteAction(
      new ExternalChangeAction() {
        @Override
        public void run() {
          PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
          treeEvent.setParent(parentDir);

          if (VirtualFile.PROP_NAME.equals(propertyName)) {
            final String newName = (String)event.getNewValue();

            if (parentDir == null) return;

            if (vFile.isDirectory()) {
              PsiDirectory psiDir = myFileManager.findDirectory(vFile);
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
              final FileViewProvider viewProvider = myFileManager.findViewProvider(vFile);
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
            PsiFile psiFile = myFileManager.getCachedPsiFileInner(vFile);
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

  @Override
  public void propertyChanged(final VirtualFilePropertyEvent event) {
    final String propertyName = event.getPropertyName();
    final VirtualFile vFile = event.getFile();

    final FileViewProvider oldFileViewProvider = myFileManager.findCachedViewProvider(vFile);
    final PsiFile oldPsiFile;
    if (oldFileViewProvider instanceof SingleRootFileViewProvider) {
      oldPsiFile = ((SingleRootFileViewProvider)oldFileViewProvider).getCachedPsi(oldFileViewProvider.getBaseLanguage());
    }
    else {
      oldPsiFile = null;
    }

    VirtualFile parent = vFile.getParent();
    final PsiDirectory parentDir = getCachedDirectory(parent);

    if (FileContentUtilCore.FORCE_RELOAD_REQUESTOR.equals(event.getRequestor())) {
      FileViewProvider viewProvider = myFileManager.createFileViewProvider(vFile, true);
      myFileManager.setViewProvider(vFile, viewProvider);
      PsiFile newPsiFile = myManager.findFile(vFile);
      if (newPsiFile == null) {
        // psi file here can be null for any custom file with null parent
        // that is currently open in a file editor, e.g. db table editor
        //LOG.error("null psi file for "+vFile+"; provider: "+viewProvider);
        return;
      }
      if (!viewProvider.isPhysical()) {
        Document document = viewProvider.getDocument();
        if (document != null) {
          PsiDocumentManagerImpl.cachePsi(document, newPsiFile);
        }
      }
      if (parentDir != null) {
        PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
        treeEvent.setParent(parentDir);

        treeEvent.setOldChild(oldPsiFile);
        treeEvent.setNewChild(newPsiFile);
        myManager.childReplaced(treeEvent);
      }
      return;
    }

    // do not suppress reparse request for light files
    if (parentDir == null) {
      boolean fire = VirtualFile.PROP_NAME.equals(propertyName) && vFile.isDirectory();
      if (fire) {
        PsiDirectory psiDir = myFileManager.getCachedDirectory(vFile);
        fire = psiDir != null;
      }
      if (!fire) return; // do not fire event if parent directory was never accessed via PSI
    }

    if (oldPsiFile != null && oldPsiFile.isPhysical()) {
      ((SmartPointerManagerImpl)SmartPointerManager.getInstance(myManager.getProject())).fastenBelts(oldPsiFile, 0, null);
    }
    ApplicationManager.getApplication().runWriteAction(
      new ExternalChangeAction() {
        @Override
        public void run() {
          PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
          treeEvent.setParent(parentDir);

          if (VirtualFile.PROP_NAME.equals(propertyName)) {
            if (vFile.isDirectory()) {
              PsiDirectory psiDir = myFileManager.getCachedDirectory(vFile);
              if (psiDir != null) {
                if (myFileTypeManager.isFileIgnored(vFile)) {
                  myFileManager.removeFilesAndDirsRecursively(vFile);

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
                PsiDirectory psiDir1 = myFileManager.findDirectory(vFile);
                if (psiDir1 != null) {
                  treeEvent.setChild(psiDir1);
                  myManager.childAdded(treeEvent);
                }
              }
            }
            else {
              final FileViewProvider fileViewProvider = myFileManager.createFileViewProvider(vFile, true);
              final PsiFile newPsiFile = fileViewProvider.getPsi(fileViewProvider.getBaseLanguage());
              if(oldPsiFile != null) {
                if (newPsiFile == null) {
                  myFileManager.removeCachedViewProvider(vFile);

                  treeEvent.setChild(oldPsiFile);
                  myManager.childRemoved(treeEvent);
                }
                else if (!newPsiFile.getClass().equals(oldPsiFile.getClass()) ||
                         newPsiFile.getFileType() != myFileTypeManager.getFileTypeByFileName((String)event.getOldValue()) ||
                         languageDialectChanged(newPsiFile, (String)event.getOldValue()) ||
                         !oldFileViewProvider.getLanguages().equals(fileViewProvider.getLanguages())) {
                  myFileManager.cacheViewProvider(vFile, fileViewProvider);

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
                myFileManager.cacheViewProvider(vFile, fileViewProvider);
                if (parentDir != null) {
                  treeEvent.setChild(newPsiFile);
                  myManager.childAdded(treeEvent);
                }
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
          else if (VirtualFile.PROP_ENCODING.equals(propertyName)) {
            if (oldPsiFile == null) return;

            treeEvent.setElement(oldPsiFile);
            treeEvent.setPropertyName(VirtualFile.PROP_ENCODING);
            treeEvent.setOldValue(event.getOldValue());
            treeEvent.setNewValue(event.getNewValue());
            myManager.propertyChanged(treeEvent);
          }
        }
      }
    );
  }

  @Override
  public void beforeFileMovement(VirtualFileMoveEvent event) {
    final VirtualFile vFile = event.getFile();

    final PsiDirectory oldParentDir = myFileManager.findDirectory(event.getOldParent());
    final PsiDirectory newParentDir = myFileManager.findDirectory(event.getNewParent());
    if (oldParentDir == null && newParentDir == null) return;
    if (myFileTypeManager.isFileIgnored(vFile)) return;

    ApplicationManager.getApplication().runWriteAction(
      new ExternalChangeAction() {
        @Override
        public void run() {
          PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);

          boolean isExcluded = vFile.isDirectory() && myProjectFileIndex.isIgnored(vFile);
          if (oldParentDir != null && !isExcluded) {
            if (newParentDir != null) {
              treeEvent.setOldParent(oldParentDir);
              treeEvent.setNewParent(newParentDir);
              if (vFile.isDirectory()) {
                PsiDirectory psiDir = myFileManager.findDirectory(vFile);
                treeEvent.setChild(psiDir);
              }
              else {
                PsiFile psiFile = myFileManager.findFile(vFile);
                treeEvent.setChild(psiFile);
              }
              myManager.beforeChildMovement(treeEvent);
            }
            else {
              treeEvent.setParent(oldParentDir);
              if (vFile.isDirectory()) {
                PsiDirectory psiDir = myFileManager.findDirectory(vFile);
                treeEvent.setChild(psiDir);
              }
              else {
                PsiFile psiFile = myFileManager.findFile(vFile);
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

  @Override
  public void fileMoved(VirtualFileMoveEvent event) {
    final VirtualFile vFile = event.getFile();

    final PsiDirectory oldParentDir = myFileManager.findDirectory(event.getOldParent());
    final PsiDirectory newParentDir = myFileManager.findDirectory(event.getNewParent());
    if (oldParentDir == null && newParentDir == null) return;

    final PsiElement oldElement = vFile.isDirectory()
                                  ? myFileManager.getCachedDirectory(vFile)
                                  : myFileManager.getCachedPsiFileInner(vFile);
    myFileManager.removeInvalidFilesAndDirs(true);
    final PsiElement newElement;
    final FileViewProvider newViewProvider;
    if (!vFile.isDirectory()){
      newViewProvider = myFileManager.createFileViewProvider(vFile, true);
      newElement = newViewProvider.getPsi(myFileManager.findViewProvider(vFile).getBaseLanguage());
    }
    else {
      newElement = myFileManager.findDirectory(vFile);
      newViewProvider = null;
    }

    if (oldElement == null && newElement == null) return;

    ApplicationManager.getApplication().runWriteAction(
      new ExternalChangeAction() {
        @Override
        public void run() {
          PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
          if (oldElement == null) {
            myFileManager.cacheViewProvider(vFile, newViewProvider);
            treeEvent.setParent(newParentDir);
            treeEvent.setChild(newElement);
            myManager.childAdded(treeEvent);
          }
          else {
            if (newElement == null) {
              myFileManager.removeCachedViewProvider(vFile);
              treeEvent.setParent(oldParentDir);
              treeEvent.setChild(oldElement);
              myManager.childRemoved(treeEvent);
            }
            else {
              if (oldElement.getClass().equals(newElement.getClass())) {
                treeEvent.setOldParent(oldParentDir);
                treeEvent.setNewParent(newParentDir);
                treeEvent.setChild(newElement);
                myManager.childMoved(treeEvent);
              }
              else {
                myFileManager.cacheViewProvider(vFile, newViewProvider);
                PsiTreeChangeEventImpl treeRemoveEvent = new PsiTreeChangeEventImpl(myManager);
                treeRemoveEvent.setParent(oldParentDir);
                treeRemoveEvent.setChild(oldElement);
                myManager.childRemoved(treeRemoveEvent);
                PsiTreeChangeEventImpl treeAddEvent = new PsiTreeChangeEventImpl(myManager);
                treeAddEvent.setParent(newParentDir);
                treeAddEvent.setChild(newElement);
                myManager.childAdded(treeAddEvent);
              }
            }
          }
        }
      }
    );
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

  // When file is renamed so that extension changes then language dialect might change and thus psiFile should be invalidated
  private static boolean languageDialectChanged(final PsiFile newPsiFile, String oldFileName) {
    return newPsiFile != null && !FileUtilRt.extensionEquals(oldFileName, FileUtilRt.getExtension(newPsiFile.getName()));
  }

  private class MyModuleRootListener implements ModuleRootListener {
    private VirtualFile[] myOldContentRoots = null;
    private volatile int depthCounter = 0;
    @Override
    public void beforeRootsChange(final ModuleRootEvent event) {
      if (!myFileManager.isInitialized()) return;
      if (event.isCausedByFileTypesChange()) return;
      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          @Override
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

    @Override
    public void rootsChanged(final ModuleRootEvent event) {
      myFileManager.dispatchPendingEvents();

      if (!myFileManager.isInitialized()) return;
      if (event.isCausedByFileTypesChange()) return;
      ApplicationManager.getApplication().runWriteAction(
        new ExternalChangeAction() {
          @Override
          public void run() {
            depthCounter--;
            assert depthCounter >= 0 : depthCounter;
            if (depthCounter > 0) return;

            myFileManager.removeInvalidFilesAndDirs(true);

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

  private class MyFileDocumentManagerAdapter extends FileDocumentManagerAdapter {
    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
      final PsiFile psiFile = myFileManager.getCachedPsiFileInner(file);
      if (psiFile != null) {
        ApplicationManager.getApplication().runWriteAction(
          new ExternalChangeAction() {
            @Override
            public void run() {
              myFileManager.reloadFromDisk(psiFile, true); // important to ignore document which might appear already!
            }
          }
        );
      }
    }
  }
}
