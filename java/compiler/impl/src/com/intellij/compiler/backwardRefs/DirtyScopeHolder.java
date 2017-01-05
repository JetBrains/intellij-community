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
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.backwardRefs.view.DirtyScopeTestInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludedEntriesListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

  public DirtyScopeHolder(@NotNull CompilerReferenceServiceImpl service,
                          FileDocumentManager fileDocumentManager,
                          PsiDocumentManager psiDocumentManager){
    myService = service;
    myFileDocManager = fileDocumentManager;
    myPsiDocManager = psiDocumentManager;

    service.getProject().getMessageBus().connect().subscribe(ExcludedEntriesListener.TOPIC, new ExcludedEntriesListener() {
      @Override
      public void onEntryAdded(@NotNull ExcludeEntryDescription description) {
        synchronized (myLock) {
          if (myCompilationPhase) {
            myExcludedDescriptions.add(description);
          }
        }
      }
    });
  }

  void compilerActivityStarted() {
    synchronized (myLock) {
      myCompilationPhase = true;
      Collections.addAll(myExcludedDescriptions, CompilerConfiguration.getInstance(myService.getProject()).getExcludedEntriesConfiguration().getExcludeEntryDescriptions());
      myExcludedFilesScope = null;
    }
  }

  void compilerActivityFinished(Module[] affectedModules, Module[] markAsDirty) {
    ExcludeEntryDescription[] descriptions;
    synchronized (myLock) {
      myCompilationPhase = false;

      ContainerUtil.removeAll(myVFSChangedModules, affectedModules);
      Collections.addAll(myVFSChangedModules, markAsDirty);
      myVFSChangedModules.addAll(myChangedModulesDuringCompilation);
      myChangedModulesDuringCompilation.clear();
      descriptions = myExcludedDescriptions.toArray(new ExcludeEntryDescription[myExcludedDescriptions.size()]);
      myExcludedDescriptions.clear();
    }
    myExcludedFilesScope = ExcludedFromCompileFilesUtil.getExcludedFilesScope(descriptions, myService.getFileTypes(), myService.getProject(), myService.getFileIndex());
  }

  GlobalSearchScope getDirtyScope() {
    final Project project = myService.getProject();
    synchronized (myLock) {
      if (myCompilationPhase) {
        return GlobalSearchScope.allScope(project);
      }
      return ReadAction.compute(() -> {
        if (project.isDisposed()) throw new ProcessCanceledException();
        return CachedValuesManager.getManager(project).getCachedValue(this, () ->
          CachedValueProvider.Result.create(calculateDirtyScope(), PsiModificationTracker.MODIFICATION_COUNT, VirtualFileManager.getInstance(), myService));
      });
    }
  }

  private GlobalSearchScope calculateDirtyScope() {
    final GlobalSearchScope dirtyModuleScope = getAllDirtyModules()
      .stream()
      .map(Module::getModuleWithDependentsScope)
      .reduce(GlobalSearchScope.EMPTY_SCOPE, (s1, s2) -> s1.union(s2));

    return dirtyModuleScope.union(myExcludedFilesScope);
  }

  @NotNull
  private Set<Module> getAllDirtyModules() {
    final Set<Module> dirtyModules = new THashSet<>(myVFSChangedModules);
    for (Document document : myFileDocManager.getUnsavedDocuments()) {
      final VirtualFile file = myFileDocManager.getFile(document);
      if (file == null) continue;
      final Module m = getModuleForSourceContentFile(file);
      if (m != null) dirtyModules.add(m);
    }
    for (Document document : myPsiDocManager.getUncommittedDocuments()) {
      final Module m = getModuleForSourceContentFile(ObjectUtils.notNull(myPsiDocManager.getPsiFile(document)).getVirtualFile());
      if (m != null) dirtyModules.add(m);
    }
    return dirtyModules;
  }

  boolean contains(VirtualFile file) {
    return getDirtyScope().contains(file);
  }

  void installVFSListener() {
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        processChange(event.getFile());
      }

      @Override
      public void fileCopied(@NotNull VirtualFileCopyEvent event) {
        processChange(event.getFile());
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        processChange(event.getFile());
      }

      @Override
      public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName()) || VirtualFile.PROP_SYMLINK_TARGET.equals(event.getPropertyName())) {
          processChange(event.getFile());
        }
      }

      @Override
      public void beforeContentsChange(@NotNull VirtualFileEvent event) {
        processChange(event.getFile());
      }

      @Override
      public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
        processChange(event.getFile());
      }

      @Override
      public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
        processChange(event.getFile());
      }

      private void processChange(VirtualFile file) {
        fileChanged(file);
      }

      void fileChanged(VirtualFile file) {
        final Module module = getModuleForSourceContentFile(file);
        if (module != null) {
          synchronized (myLock) {
            if (myCompilationPhase) {
              myChangedModulesDuringCompilation.add(module);
            } else {
              myVFSChangedModules.add(module);
            }
          }
        }
      }
    }, myService.getProject());
  }

  private Module getModuleForSourceContentFile(@NotNull VirtualFile file) {
    if (myService.getFileIndex().isInSourceContent(file) && myService.getFileTypes().contains(file.getFileType())) {
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
