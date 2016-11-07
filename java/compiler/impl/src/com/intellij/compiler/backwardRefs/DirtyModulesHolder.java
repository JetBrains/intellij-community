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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.Set;

public class DirtyModulesHolder extends UserDataHolderBase {
  private final CompilerReferenceServiceImpl myService;
  private final FileDocumentManager myFileDocManager;
  private final PsiDocumentManager myPsiDocManager;
  private final Set<Module> myVFSChangedModules = ContainerUtil.newHashSet();
  private final Set<Module> myChangedModulesDuringCompilation = ContainerUtil.newHashSet();
  private final Object myLock = new Object();

  private boolean myCompilationPhase;

  public DirtyModulesHolder(@NotNull CompilerReferenceServiceImpl service,
                            FileDocumentManager fileDocumentManager,
                            PsiDocumentManager psiDocumentManager){
    myService = service;
    myFileDocManager = fileDocumentManager;
    myPsiDocManager = psiDocumentManager;
  }

  void compilerActivityStarted() {
    synchronized (myLock) {
      myCompilationPhase = true;
    }
  }

  void compilerActivityFinished(Module[] affectedModules, Module[] markAsDirty) {
    synchronized (myLock) {
      myCompilationPhase = false;

      ContainerUtil.removeAll(myVFSChangedModules, affectedModules);
      Collections.addAll(myVFSChangedModules, markAsDirty);
      myVFSChangedModules.addAll(myChangedModulesDuringCompilation);
      myChangedModulesDuringCompilation.clear();
    }
  }

  GlobalSearchScope getDirtyScope() {
    final Project project = myService.getProject();
    synchronized (myLock) {
      if (myCompilationPhase) {
        return GlobalSearchScope.allScope(project);
      }
      return ReadAction.compute(() -> {
        if (project.isDisposed()) {
          return GlobalSearchScope.allScope(project);
        }
        return CachedValuesManager.getManager(project).getCachedValue(this, () ->
          CachedValueProvider.Result.create(calculateDirtyModules(), PsiModificationTracker.MODIFICATION_COUNT, VirtualFileManager.getInstance(), myService));
      });
    }
  }

  private GlobalSearchScope calculateDirtyModules() {
    return getAllDirtyModules().stream().map(Module::getModuleWithDependentsScope).reduce(GlobalSearchScope.EMPTY_SCOPE, (s1, s2) -> s1.union(s2));
  }

  @NotNull
  private Set<Module> getAllDirtyModules() {
    final Set<Module> dirtyModules = new THashSet<>(myVFSChangedModules);
    for (Document document : myFileDocManager.getUnsavedDocuments()) {
      final Module m = getModuleForSourceContentFile(myFileDocManager.getFile(document));
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

  private Module getModuleForSourceContentFile(@Nullable VirtualFile file) {
    if (file != null &&
        myService.getFileIndex().isInSourceContent(file) &&
        myService.getFileTypes().contains(file.getFileType())) {
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
}
