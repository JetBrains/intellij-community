// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public String getDescription() {
    return JavaBundle.message("macro.file.fully.qualified.package");
  }

  @NotNull
  @Override
  public String getName() {
    return "FileFQPackage";
  }
}
