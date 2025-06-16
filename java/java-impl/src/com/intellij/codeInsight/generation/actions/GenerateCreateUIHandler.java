/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class GenerateCreateUIHandler implements CodeInsightActionHandler {
  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    final PsiElement element = PsiUtilBase.getElementAtCaret(editor);
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null) return;

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    String annotation = "";
    if (PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, psiFile)) {
      annotation = "@SuppressWarnings({\"MethodOverridesStaticMethodOfSuperclass\", \"UnusedDeclaration\"})";
    }
    final PsiMethod createUI = factory.createMethodFromText(annotation +
                                                            "\npublic static javax.swing.plaf.ComponentUI createUI(javax.swing.JComponent c) {" +
                                                        "\n  return new " + psiClass.getName() + "();\n}", psiClass);
    final PsiMethod newMethod = (PsiMethod)psiClass.add(CodeStyleManager.getInstance(project).reformat(createUI));
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newMethod);
    final PsiReturnStatement returnStatement = PsiTreeUtil.findChildOfType(newMethod, PsiReturnStatement.class);
    if (returnStatement != null) {
      final int offset = returnStatement.getTextRange().getEndOffset();
      editor.getCaretModel().moveToOffset(offset - 2);
    }
  }

}
