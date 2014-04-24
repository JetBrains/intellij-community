/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwitchStatementPostfixTemplate extends StatementPostfixTemplateBase {
  public SwitchStatementPostfixTemplate() {
    super("switch", "Produces switch over integral/enum/string values", "switch (expr)");
  }

  private static boolean isSwitchCompatibleType(@Nullable PsiType type, @NotNull PsiElement context) {
    if (type == null) return false;
    if (PsiType.INT.isAssignableFrom(type)) return true;

    if (type instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null && psiClass.isEnum()) return true;
    }

    if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return true; // todo: mock jdk 6 and 7
      PsiFile containingFile = context.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        LanguageLevel level = ((PsiJavaFile)containingFile).getLanguageLevel();
        if (level.isAtLeast(LanguageLevel.JDK_1_7)) return true;
      }
    }

    return false;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expr = PostfixTemplatesUtils.getTopmostExpression(context);
    return expr != null && isSwitchCompatibleType(expr.getType(), context);
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    surroundWith(context, editor, "switch");
  }
}
