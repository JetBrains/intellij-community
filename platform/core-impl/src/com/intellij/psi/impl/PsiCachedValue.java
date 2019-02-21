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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.CachedValueBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiCachedValue<T> extends CachedValueBase<T> {
  private final PsiManager myManager;
  private volatile long myLastPsiTimeStamp = -1;

  PsiCachedValue(@NotNull PsiManager manager, boolean trackValue) {
    super(trackValue);
    myManager = manager;
  }

  @Override
  protected void valueUpdated(@NotNull Object[] dependencies) {
    myLastPsiTimeStamp = hasOnlyPhysicalPsiDependencies(dependencies) ? myManager.getModificationTracker().getModificationCount() : -1;
  }

  private static boolean hasOnlyPhysicalPsiDependencies(@NotNull Object[] dependencies) {
    return dependencies.length > 0 && ContainerUtil.and(dependencies, PsiCachedValue::anyChangeImpliesPsiCounterChange);
  }

  private static boolean anyChangeImpliesPsiCounterChange(@NotNull Object dependency) {
    return dependency instanceof PsiElement && ((PsiElement)dependency).isValid() && ((PsiElement)dependency).isPhysical() ||
           dependency instanceof ProjectRootModificationTracker ||
           dependency instanceof PsiModificationTracker ||
           dependency == PsiModificationTracker.MODIFICATION_COUNT ||
           dependency == PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT ||
           dependency == PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT;
  }

  @Override
  protected boolean isUpToDate(@NotNull Data data) {
    return !myManager.isDisposed() && super.isUpToDate(data);
  }

  @Override
  protected boolean isDependencyOutOfDate(@NotNull Object dependency, long oldTimeStamp) {
    if (myLastPsiTimeStamp != -1 && myLastPsiTimeStamp == myManager.getModificationTracker().getModificationCount()) {
      return false;
    }

    return super.isDependencyOutOfDate(dependency, oldTimeStamp);
  }

  @Override
  protected long getTimeStamp(@NotNull Object dependency) {
    if (dependency instanceof PsiDirectory) {
      return myManager.getModificationTracker().getOutOfCodeBlockModificationCount();
    }

    if (dependency instanceof PsiElement) {
      PsiElement element = (PsiElement)dependency;
      if (!element.isValid()) return -1;
      PsiFile containingFile = element.getContainingFile();
      if (containingFile != null) return containingFile.getModificationStamp();
    }

    if (dependency == PsiModificationTracker.MODIFICATION_COUNT) {
      return myManager.getModificationTracker().getModificationCount();
    }
    if (dependency == PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT) {
      return myManager.getModificationTracker().getOutOfCodeBlockModificationCount();
    }
    if (dependency == PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT) {
      return myManager.getModificationTracker().getJavaStructureModificationCount();
    }

    return super.getTimeStamp(dependency);
  }

  @Override
  public boolean isFromMyProject(@NotNull Project project) {
    return myManager.getProject() == project;
  }
}
