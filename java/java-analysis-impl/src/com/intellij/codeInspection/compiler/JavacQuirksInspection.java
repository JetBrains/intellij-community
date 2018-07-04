// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.compiler;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class JavacQuirksInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.COMPILER_ISSUES;
  }

  @Nls @NotNull
  @Override
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.compiler.javac.quirks.name");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "JavacQuirks";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavacQuirksInspectionVisitor(holder);
  }
}
