// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CachedValueBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class PsiCachedValue<T> extends CachedValueBase<T> {
  private static final Key<?> PSI_MOD_COUNT_OPTIMIZATION = Key.create("PSI_MOD_COUNT_OPTIMIZATION");
  private final PsiManager myManager;

  PsiCachedValue(@NotNull PsiManager manager) {
    myManager = manager;
  }

  @Override
  protected Object @NotNull [] normalizeDependencies(@Nullable T value, Object @NotNull [] dependencyItems) {
    Object[] dependencies = super.normalizeDependencies(value, dependencyItems);
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
    // be inexplicably injected into a non-physical element,
    // in this case PSI_MODIFICATION_COUNT doesn't change and thus can't be relied upon
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myManager.getProject());
    PsiFile topLevelFile = manager.getTopLevelFile(dependency);
    return topLevelFile != null && topLevelFile.isPhysical();
  }

  @Override
  protected boolean isUpToDate(@NotNull Data<T> data) {
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
  protected @NotNull String getIdempotenceFailureContext() {
    Project project = myManager.getProject();
    DumbService dumbService = DumbService.getInstance(project);
    boolean dumb = dumbService.isDumb();
    return "Dumb mode: " + dumb + "\nAlternative resolve: " + dumbService.isAlternativeResolveEnabled();
  }

  @Override
  public boolean isFromMyProject(@NotNull Project project) {
    return myManager.getProject() == project;
  }
}
