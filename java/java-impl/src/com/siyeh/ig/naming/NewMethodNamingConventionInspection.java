// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.naming.AbstractNamingConventionInspection;
import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public final class NewMethodNamingConventionInspection extends AbstractNamingConventionInspection<PsiMethod> {
  public static final ExtensionPointName<NamingConvention<PsiMethod>> EP_NAME = ExtensionPointName.create("com.intellij.naming.convention.method");
  public NewMethodNamingConventionInspection() {
    super(EP_NAME.getExtensionList(), InstanceMethodNamingConvention.INSTANCE_METHOD_NAMING_CONVENTION);
    registerConventionsListener(EP_NAME);
  }

  @Override
  protected LocalQuickFix createRenameFix() {
    return new RenameFix();
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!(holder.getFile() instanceof PsiClassOwner)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        if (method.isConstructor()) {
          return;
        }

        if (!isOnTheFly && MethodUtils.hasSuper(method)) {
          return;
        }

        if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
          return;
        }

        String name = method.getName();
        checkName(method, name, holder);
      }
    };
  }

}
