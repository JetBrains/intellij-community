/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.backwardRefs;

import com.intellij.ProjectTopics;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.view.DirtyScopeTestInfo;
import com.intellij.compiler.server.CustomBuilderMessageHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludedEntriesListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.backwardRefs.BackwardReferenceIndexBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DirtyScopeHolder extends UserDataHolderBase {
  private final CompilerReferenceServiceImpl myService;
  private final FileDocumentManager myFileDocManager;
  private final PsiDocumentManager myPsiDocManager;
  private final Object myLock = new Object();

  private final Set<Module> myVFSChangedModules = ContainerUtil.newHashSet(); // guarded by myLock
  private final Set<Module> myChangedModulesDuringCompilation = ContainerUtil.newHashSet(); // guarded by myLock
  private final List<ExcludeEntryDescription> myExcludedDescriptions = new SmartList<>(); // guarded by myLock
  private boolean myCompilationPhase; // guarded by myLock
  private volatile GlobalSearchScope myExcludedFilesScope; // calculated outside myLock
  private final Set<String> myCompilationAffectedModules = ContainerUtil.newConcurrentSet(); // used outside myLock
  private final FileTypeRegistry myFileTypeRegistry = FileTypeRegistry.getInstance();


  public DirtyScopeHolder(@NotNull CompilerReferenceServiceImpl service,
                          FileDocumentManager fileDocumentManager,
                          PsiDocumentManager psiDocumentManager){
    myService = service;
    myFileDocManager = fileDocumentManager;
    myPsiDocManager = psiDocumentManager;

    if (CompilerReferenceService.isEnabled()) {
      final MessageBusConnection connect = service.getProject().getMessageBus().connect();
      connect.subscribe(ExcludedEntriesListener.TOPIC, new ExcludedEntriesListener() {
        @Override
        public void onEntryAdded(@NotNull ExcludeEntryDescription description) {
          synchronized (myLock) {
            if (myCompilationPhase) {
              myExcludedDescriptions.add(description);
            }
          }
        }
      });

      connect.subscribe(CustomBuilderMessageHandler.TOPIC, (builderId, messageType, messageText) -> {
        if (BackwardReferenceIndexBuilder.BUILDER_ID.equals(builderId)) {
          myCompilationAffectedModules.add(messageText);
        }
      });

      connect.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        @Override
        public void beforeRootsChange(ModuleRootEvent event) {
          final Module[] modules = ModuleManager.getInstance(myService.getProject()).getModules();
          synchronized (myLock) {
            ContainerUtil.addAll(myVFSChangedModules, modules);
          }
        }
      });
    }
  }

  void compilerActivityStarted() {
    final ExcludeEntryDescription[] excludeEntryDescriptions =
      CompilerConfiguration.getInstance(myService.getProject()).getExcludedEntriesConfiguration().getExcludeEntryDescriptions();
    synchronized (myLock) {
      myCompilationPhase = true;
      Collections.addAll(myExcludedDescriptions, excludeEntryDescriptions);
      myExcludedFilesScope = null;
      myCompilationAffectedModules.clear();
    }
  }

  void upToDateChecked(boolean isUpToDate) {
    final Module[] modules = ReadAction.compute(() -> {
      final Project project = myService.getProject();
      if (project.isDisposed()) {
        return null;
      }
      return ModuleManager.getInstance(project).getModules();
    });
    if (modules == null) return;
    compilationFinished(() -> {
      if (!isUpToDate) {
        ContainerUtil.addAll(myVFSChangedModules, modules);
      }
    });
  }

  void compilerActivityFinished() {
    final List<Module> compiledModules = ReadAction.compute(() -> {
      final Project project = myService.getProject();
      if (project.isDisposed()) {
        return null;
      }
      final ModuleManager moduleManager = ModuleManager.getInstance(myService.getProject());
      return myCompilationAffectedModules.stream().map(moduleManager::findModuleByName).collect(Collectors.toList());
    });
    compilationFinished(() -> {
      if (compiledModules == null) return;
      myVFSChangedModules.removeAll(compiledModules);
    });
  }

  private void compilationFinished(Runnable action) {
    ExcludeEntryDescription[] descriptions;
    synchronized (myLock) {
      myCompilationPhase = false;
      action.run();
      myVFSChangedModules.addAll(myChangedModulesDuringCompilation);
      myChangedModulesDuringCompilation.clear();
      descriptions = myExcludedDescriptions.toArray(new ExcludeEntryDescription[myExcludedDescriptions.size()]);
      myExcludedDescriptions.clear();
    }
    myCompilationAffectedModules.clear();
    myExcludedFilesScope = ExcludedFromCompileFilesUtil.getExcludedFilesScope(descriptions, myService.getFileTypes(), myService.getProject(), myService.getFileIndex());
  }

  GlobalSearchScope getDirtyScope() {
    final Project project = myService.getProject();
    return ReadAction.compute(() -> {
      synchronized (myLock) {
        if (myCompilationPhase) {
          return GlobalSearchScope.allScope(project);
        }
        if (project.isDisposed()) throw new ProcessCanceledException();
        return CachedValuesManager.getManager(project).getCachedValue(this, () ->
          CachedValueProvider.Result
            .create(calculateDirtyScope(), PsiModificationTracker.MODIFICATION_COUNT, VirtualFileManager.getInstance(), myService));
      }
    });
  }

  private GlobalSearchScope calculateDirtyScope() {
    final Set<Module> dirtyModules = getAllDirtyModules();
    if (dirtyModules.isEmpty()) return myExcludedFilesScope;
    GlobalSearchScope dirtyModuleScope = GlobalSearchScope.union(dirtyModules
                                                                   .stream()
                                                                   .map(Module::getModuleWithDependentsScope)
                                                                   .toArray(GlobalSearchScope[]::new));
    return dirtyModuleScope.union(myExcludedFilesScope);
  }

  @NotNull
  Set<Module> getAllDirtyModules() {
    final Set<Module> dirtyModules = new THashSet<>(myVFSChangedModules);
    for (Document document : myFileDocManager.getUnsavedDocuments()) {
      final VirtualFile file = myFileDocManager.getFile(document);
      if (file == null) continue;
      final Module m = getModuleForSourceContentFile(file);
      if (m != null) dirtyModules.add(m);
    }
    for (Document document : myPsiDocManager.getUncommittedDocuments()) {
      final PsiFile psiFile = myPsiDocManager.getPsiFile(document);
      if (psiFile == null) continue;
      final VirtualFile file = psiFile.getVirtualFile();
      if (file == null) continue;
      final Module m = getModuleForSourceContentFile(file);
      if (m != null) dirtyModules.add(m);
    }
    return dirtyModules;
  }

  boolean contains(VirtualFile file) {
    return getDirtyScope().contains(file);
  }

  void installVFSListener() {
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        fileChanged(event.getFile());
      }

      @Override
      public void fileCopied(@NotNull VirtualFileCopyEvent event) {
        fileChanged(event.getFile());
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        fileChanged(event.getFile());
      }

      @Override
      public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName()) && event.getFile().isDirectory() && event.getFile().isInLocalFileSystem()) {
          final String path = event.getFile().getPath();
          for (Module module : ModuleManager.getInstance(myService.getProject()).getModules()) {
            if (FileUtil.isAncestor(path, module.getModuleFilePath(), true)) {
              addToDirtyModules(module);
            }
          }
        }
      }

      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName()) || VirtualFile.PROP_SYMLINK_TARGET.equals(event.getPropertyName())) {
          fileChanged(event.getFile());
        }
      }

      @Override
      public void beforeContentsChange(@NotNull VirtualFileEvent event) {
        fileChanged(event.getFile());
      }

      @Override
      public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
        fileChanged(event.getFile());
      }

      @Override
      public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
        fileChanged(event.getFile());
      }

      private void fileChanged(VirtualFile file) {
        final Module module = getModuleForSourceContentFile(file);
        if (module != null) {
          addToDirtyModules(module);
        }
      }

      private void addToDirtyModules(Module module) {
        synchronized (myLock) {
          if (myCompilationPhase) {
            myChangedModulesDuringCompilation.add(module);
          } else {
            myVFSChangedModules.add(module);
          }
        }
      }
    }, myService.getProject());
  }

  private Module getModuleForSourceContentFile(@NotNull VirtualFile file) {
    FileType fileType = myFileTypeRegistry.getFileTypeByFileName(file.getNameSequence());
    if (myService.getFileTypes().contains(fileType) && myService.getFileIndex().isInSourceContent(file)) {
      return myService.getFileIndex().getModuleForFile(file);
    }
    return null;
  }

  @TestOnly
  @NotNull
  public Set<Module> getAllDirtyModulesForTest() {
    synchronized (myLock) {
      return getAllDirtyModules();
    }
  }

  @SuppressWarnings("unchecked")
  @NotNull
  DirtyScopeTestInfo getState() {
    synchronized (myLock) {
      final Module[] vfsChangedModules = myVFSChangedModules.toArray(Module.EMPTY_ARRAY);
      final List<Module> unsavedChangedModuleList = new ArrayList<>(getAllDirtyModules());
      ContainerUtil.removeAll(unsavedChangedModuleList, vfsChangedModules);
      final Module[] unsavedChangedModules = unsavedChangedModuleList.toArray(Module.EMPTY_ARRAY);
      final List<VirtualFile> excludedFiles = myExcludedFilesScope instanceof Iterable ? ContainerUtil.newArrayList((Iterable<VirtualFile>)myExcludedFilesScope) : Collections.emptyList();
      return new DirtyScopeTestInfo(vfsChangedModules, unsavedChangedModules, excludedFiles.toArray(VirtualFile.EMPTY_ARRAY), getDirtyScope());
    }
  }
}
