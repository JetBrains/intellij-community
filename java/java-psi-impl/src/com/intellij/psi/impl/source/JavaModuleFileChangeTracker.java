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
package com.intellij.psi.impl.source;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaModuleFileChangeTracker implements ModificationTracker {
  @NotNull
  public static ModificationTracker getInstance(@NotNull final Project p) {
    return CachedValuesManager.getManager(p).getCachedValue(p, new CachedValueProvider<JavaModuleFileChangeTracker>() {
      @Nullable
      @Override
      public Result<JavaModuleFileChangeTracker> compute() {
        return Result.create(new JavaModuleFileChangeTracker(p), NEVER_CHANGED);
      }
    });
  }

  private volatile long myCount = 0;

  private JavaModuleFileChangeTracker(Project project) {
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override public void childAdded(@NotNull PsiTreeChangeEvent event) { process(event.getFile()); }
      @Override public void childRemoved(@NotNull PsiTreeChangeEvent event) { process(event.getFile()); }
      @Override public void childReplaced(@NotNull PsiTreeChangeEvent event) { process(event.getFile()); }
      @Override public void childMoved(@NotNull PsiTreeChangeEvent event) { process(event.getFile()); }
      @Override public void childrenChanged(@NotNull PsiTreeChangeEvent event) { process(event.getFile()); }
      @Override public void propertyChanged(@NotNull PsiTreeChangeEvent event) { process(event.getFile()); }

      private void process(PsiFile file) {
        if (file != null && PsiJavaModule.MODULE_INFO_FILE.equals(file.getName())) {
          myCount++;
        }
      }
    }, project);
  }

  @Override
  public long getModificationCount() {
    return myCount;
  }
}