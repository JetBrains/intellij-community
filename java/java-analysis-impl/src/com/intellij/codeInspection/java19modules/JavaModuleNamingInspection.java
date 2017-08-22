/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.SyntaxTraverser.psiTraverser;

public class JavaModuleNamingInspection extends BaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return !PsiUtil.isModuleFile(holder.getFile()) ? PsiElementVisitor.EMPTY_VISITOR : new JavaElementVisitor() {
      @Override
      public void visitModule(PsiJavaModule module) {
        super.visitModule(module);

        PsiJavaModuleReferenceElement name = module.getNameIdentifier();
        Ref<String> newName = Ref.create();
        psiTraverser().children(name).filter(PsiIdentifier.class).forEach(id -> {
          String text = id.getText();
          if (text.length() > 0 && Character.isDigit(text.charAt(text.length() - 1))) {
            String message = InspectionsBundle.message("inspection.java.module.naming.terminal.digits", text);
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