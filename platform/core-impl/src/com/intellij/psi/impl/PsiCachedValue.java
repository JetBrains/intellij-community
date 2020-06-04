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

package com.intellij.psi.impl;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.model.ModelBranch;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CachedValueBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiCachedValue<T> extends CachedValueBase<T> {
  private static final Key<?> PSI_MOD_COUNT_OPTIMIZATION = Key.create("PSI_MOD_COUNT_OPTIMIZATION");
  private final PsiManager myManager;

  PsiCachedValue(@NotNull PsiManager manager, boolean trackValue) {
    super(trackValue);
    myManager = manager;
  }

  @Override
  protected Object @NotNull [] normalizeDependencies(@NotNull CachedValueProvider.Result<T> result) {
    Object[] dependencies = super.normalizeDependencies(result);
    if (ContainerUtil.exists(dependencies, PsiCachedValue::isPsiModificationCount)) {
      for (Object dependency : dependencies) {
        if (dependency instanceof PsiElement) {
          ModelBranch branch = ModelBranch.getPsiBranch((PsiElement)dependency);
          if (branch != null) {
            return ArrayUtil.prepend((ModificationTracker)() -> branch.getBranchedPsiModificationCount(), dependencies);
          }
        }
      }
    }
    if (dependencies.length == 1 && isPsiModificationCount(dependencies[0])) {
      return dependencies;
    }
    if (dependencies.length > 0 && ContainerUtil.and(dependencies, this::anyChangeImpliesPsiCounterChange)) {
      return ArrayUtil.prepend(PSI_MOD_COUNT_OPTIMIZATION, dependencies);
    }
    return dependencies;
  }

  private boolean anyChangeImpliesPsiCounterChange(@NotNull Object dependency) {
    return dependency instanceof PsiElement && isVeryPhysical((PsiElement)dependency) ||
           dependency instanceof ProjectRootModificationTracker ||
           isPsiModificationCount(dependency);
  }

  private static boolean isPsiModificationCount(@NotNull Object dependency) {
    return dependency instanceof PsiModificationTracker ||
           dependency == PsiModificationTracker.MODIFICATION_COUNT;
  }

  private boolean isVeryPhysical(@NotNull PsiElement dependency) {
    if (!dependency.isValid()) {
      return false;
    }
    if (!dependency.isPhysical()) {
      return false;
    }
    // injected files are physical but can sometimes (look at you, completion)
    // be inexplicably injected into non-physical element, in which case PSI_MODIFICATION_COUNT doesn't change and thus can't be relied upon
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myManager.getProject());
    PsiFile topLevelFile = manager.getTopLevelFile(dependency);
    return topLevelFile != null && topLevelFile.isPhysical();
  }

  @Override
  protected boolean isUpToDate(@NotNull Data data) {
    if (myManager.isDisposed()) return false;

    Object[] dependencies = data.getDependencies();
    if (dependencies.length > 0 &&
        dependencies[0] == PSI_MOD_COUNT_OPTIMIZATION &&
        data.getTimeStamps()[0] == myManager.getModificationTracker().getModificationCount()) {
      return true;
    }

    return super.isUpToDate(data);
  }

  @Override
  protected boolean isDependencyOutOfDate(@NotNull Object dependency, long oldTimeStamp) {
    if (dependency == PSI_MOD_COUNT_OPTIMIZATION) return false;
    return super.isDependencyOutOfDate(dependency, oldTimeStamp);
  }

  @Override
  protected long getTimeStamp(@NotNull Object dependency) {
    if (dependency instanceof PsiDirectory) {
      return myManager.getModificationTracker().getModificationCount();
    }

    if (dependency instanceof PsiElement) {
      PsiElement element = (PsiElement)dependency;
      if (!element.isValid()) return -1;
      PsiFile containingFile = element.getContainingFile();
      if (containingFile != null) return containingFile.getModificationStamp();
    }

    if (dependency == PsiModificationTracker.MODIFICATION_COUNT || dependency == PSI_MOD_COUNT_OPTIMIZATION) {
      return myManager.getModificationTracker().getModificationCount();
    }

    return super.getTimeStamp(dependency);
  }

  @Override
  public boolean isFromMyProject(@NotNull Project project) {
    return myManager.getProject() == project;
  }
}
