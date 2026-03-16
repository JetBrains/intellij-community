// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public final class ClassAwareRenameFileProvider implements RenameFileActionProvider {
  @Override
  public boolean enabledInProjectView(@NotNull PsiFile file) {
    if (file instanceof PsiJavaFile javaFile) {
      String name = StringUtil.trimEnd(javaFile.getName(), ".java");
      return ContainerUtil.exists(javaFile.getClasses(), aClass -> name.equals(aClass.getName()));
    }
    return file instanceof PsiClassOwner;
  }
}
