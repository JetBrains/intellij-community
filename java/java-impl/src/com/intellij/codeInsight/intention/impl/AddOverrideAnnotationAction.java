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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class AddOverrideAnnotationAction implements IntentionAction {

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.add.override.annotation");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.override.annotation.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!PsiUtil.isLanguageLevel5OrHigher(file)) return false;
    if (!file.getManager().isInProject(file)) return false;
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return false;
    if (method.getModifierList().findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE) != null) return false;
    PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)
          && new AddAnnotationFix(CommonClassNames.JAVA_LANG_OVERRIDE, method).isAvailable(project, editor, file)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method != null) {
      new AddAnnotationFix(CommonClassNames.JAVA_LANG_OVERRIDE, method).invoke(project, editor, file);
    }
  }

  private static PsiMethod findMethod(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    PsiMethod res = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (res == null) return null;

    //Not available in method's body
    PsiCodeBlock body = res.getBody();
    if (body == null) return null;
    TextRange textRange = body.getTextRange();
    if (textRange == null || textRange.getStartOffset() <= offset) return null;

    return res;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
