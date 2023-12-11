// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.SyntaxTraverser.psiTraverser;

public final class JavaModuleNamingInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return !PsiUtil.isModuleFile(holder.getFile()) ? PsiElementVisitor.EMPTY_VISITOR : new JavaElementVisitor() {
      @Override
      public void visitModule(@NotNull PsiJavaModule module) {
        super.visitModule(module);

        PsiJavaModuleReferenceElement name = module.getNameIdentifier();
        Ref<String> newName = Ref.create();
        psiTraverser().children(name).filter(PsiIdentifier.class).forEach(id -> {
          String text = id.getText();
          if (text.length() > 0 && Character.isDigit(text.charAt(text.length() - 1))) {
            String message = JavaAnalysisBundle.message("inspection.java.module.naming.terminal.digits", text);
            if (newName.isNull()) {
              newName.set(StringUtil.join(psiTraverser().children(name).filter(PsiIdentifier.class).map(i -> trimDigits(i.getText())), "."));
            }
            holder.registerProblem(id, message, QuickFixFactory.getInstance().createRenameElementFix(module, newName.get()));
          }
        });
      }
    };
  }

  private static String trimDigits(String text) {
    int p = text.length();
    while (p > 0 && Character.isDigit(text.charAt(p - 1))) p--;
    return text.substring(0, p);
  }
}