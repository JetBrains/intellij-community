// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiPackage;

public class FileFQPackage extends Macro {
  @Override
  public String expand(DataContext dataContext) {
    PsiPackage aPackage = FilePackageMacro.getFilePackage(dataContext);
    if (aPackage == null) return null;
    return aPackage.getQualifiedName();
  }

  @Override
  public String getDescription() {
    return IdeBundle.message("macro.file.fully.qualified.package");
  }

  @Override
  public String getName() {
    return "FileFQPackage";
  }
}
