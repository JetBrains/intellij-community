// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;

public final class FileFQPackage extends Macro {
  @Override
  public String expand(@NotNull DataContext dataContext) {
    PsiPackage aPackage = FilePackageMacro.getFilePackage(dataContext);
    if (aPackage == null) return null;
    return aPackage.getQualifiedName();
  }

  @Override
  public @NotNull String getDescription() {
    return JavaBundle.message("macro.file.fully.qualified.package");
  }

  @Override
  public @NotNull String getName() {
    return "FileFQPackage";
  }
}
