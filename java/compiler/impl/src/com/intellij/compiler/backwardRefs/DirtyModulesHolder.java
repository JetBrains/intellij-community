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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    PsiManager.getInstance(myService.getProject()).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
        psiChanged(event.getFile(), event.getParent());
      }

      @Override
      public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
        psiChanged(event.getFile(), event.getParent());
      }

      @Override
      public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
        psiChanged(event.getFile(), event.getParent());
      }

      @Override
      public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
        final PsiFile file = event.getFile();
        if (file != null) {
          psiChanged(file, null);
        }
        else {
          psiChanged(null, event.getOldParent());
          psiChanged(null, event.getNewParent());
        }
      }

      @Override
      public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
        psiChanged(event.getFile(), event.getParent());
      }

      @Override
      public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
        if (PsiTreeChangeEvent.PROP_UNLOADED_PSI.equals(event.getPropertyName()) ||
            PsiTreeChangeEvent.PROP_WRITABLE.equals(event.getPropertyName())) return;
        psiChanged(event.getFile(), event.getParent());
      }

      private void psiChanged(@Nullable PsiFile psiFile, @Nullable PsiElement parent) {
        final VirtualFile file;
        if (psiFile != null) {
          file = psiFile.getVirtualFile();
        }
        else if (parent instanceof PsiFileSystemItem) {
          file = ((PsiFileSystemItem)parent).getVirtualFile();
        }
        else {
          return;
        }
        if (myService.getFileIndex().isInSourceContent(file) && myService.getFileTypes().contains(file.getFileType())) {
          final Module module = myService.getFileIndex().getModuleForFile(file);
          if (module != null) {
            synchronized (myLock) {
              if (myCompilationPhase) {
                myChangedModulesDuringCompilation.add(module);
              }
              else {
                myChangedModules.add(module);
              }
            }
          }
        }
      }
    });
  }
}
