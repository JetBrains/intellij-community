// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.naming.AbstractNamingConventionInspection;
import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FieldNamingConventionInspection extends AbstractNamingConventionInspection<PsiField> {
  public static final ExtensionPointName<NamingConvention<PsiField>> EP_NAME = ExtensionPointName.create("com.intellij.naming.convention.field");
  public FieldNamingConventionInspection() {
    super(EP_NAME.getExtensionList(), null);
    registerConventionsListener(EP_NAME);
  }

  @Override
  protected @Nullable LocalQuickFix createRenameFix() {
    return new RenameFix();
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitField(@NotNull PsiField field) {
        String name = field.getName();
        checkName(field, name, holder);
      }
    };
  }

  static class FieldNamingConventionBean extends NamingConventionBean {
    FieldNamingConventionBean(@NonNls String regex, int minLength, int maxLength) {
      super(regex, minLength, maxLength, HardcodedMethodConstants.SERIAL_VERSION_UID);
    }
  }
}
