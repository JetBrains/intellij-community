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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.intellij.psi.search.GlobalSearchScope.EMPTY_SCOPE;

class DirtyModulesHolder extends UserDataHolderBase {
  private final CompilerReferenceServiceImpl myService;
  private final Set<Module> myChangedModules = ContainerUtil.newHashSet();
  private final Set<Module> myChangedModulesDuringCompilation = ContainerUtil.newHashSet();
  private final Object myLock = new Object();

  private boolean myCompilationPhase;

  public DirtyModulesHolder(@NotNull CompilerReferenceServiceImpl service){
    myService = service;
  }

  void compilerActivityStarted() {
    synchronized (myLock) {
      myCompilationPhase = true;
    }
  }

  void compilerActivityFinished(Module[] affectedModules, Module[] markAsDirty) {
    synchronized (myLock) {
      myCompilationPhase = false;

      ContainerUtil.removeAll(myChangedModules, affectedModules);
      Collections.addAll(myChangedModules, markAsDirty);
      myChangedModules.addAll(myChangedModulesDuringCompilation);
      myChangedModulesDuringCompilation.clear();
    }
  }

  GlobalSearchScope getDirtyScope() {
    return CachedValuesManager.getManager(myService.getProject()).getCachedValue(this, () -> {
      synchronized (myLock) {
        final GlobalSearchScope dirtyScope =
          myChangedModules.stream().map(Module::getModuleWithDependentsScope).reduce(EMPTY_SCOPE, (s1, s2) -> s1.union(s2));
        return CachedValueProvider.Result.create(dirtyScope, PsiModificationTracker.MODIFICATION_COUNT, myService);
      }
    });
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
        if (myService.getFileIndex().isInSourceContent(file) && myService.getFileTypes().contains(file.getFileType())) {
          final Module module = myService.getFileIndex().getModuleForFile(file);
          if (module != null) {
            synchronized (myLock) {
              if (myCompilationPhase) {
                myChangedModulesDuringCompilation.add(module);
              } else {
                myChangedModules.add(module);
              }
            }
          }
        }
      }
    }, myService.getProject());

  }
}
