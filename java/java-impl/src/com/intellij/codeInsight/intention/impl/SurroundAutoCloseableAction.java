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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class SurroundAutoCloseableAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;
    if (!PsiUtil.getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_7)) return false;

    final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (variable == null) return false;
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return false;
    final PsiElement declaration = variable.getParent();
    if (!(declaration instanceof PsiDeclarationStatement)) return false;
    final PsiElement codeBlock = declaration.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return false;

    final PsiType type = variable.getType();
    if (!(type instanceof PsiClassType)) return false;
    final PsiClass aClass = ((PsiClassType)type).resolve();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass autoCloseable = facade.findClass(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, ProjectScope.getLibrariesScope(project));
    if (!InheritanceUtil.isInheritorOrSelf(aClass, autoCloseable, true)) return false;

    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
    final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (variable == null) return;
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return;
    final PsiElement declaration = variable.getParent();
    if (!(declaration instanceof PsiDeclarationStatement)) return;
    final PsiElement codeBlock = declaration.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return;

    PsiElement firstStatement = declaration.getNextSibling(), lastUsage = null;
    final Collection<PsiReference> references = ReferencesSearch.search(variable, new LocalSearchScope(codeBlock)).findAll();
    for (PsiReference reference : references) {
      final PsiElement statement = PsiTreeUtil.findPrevParent(codeBlock, reference.getElement());
      if ((lastUsage == null || statement.getTextOffset() > lastUsage.getTextOffset())) {
        lastUsage = statement;
      }
    }

    final String text = "try (" + variable.getTypeElement().getText() + " " + variable.getName() + " = " + initializer.getText() + ") {}";
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiStatement armStatement = factory.createStatementFromText(text, codeBlock);
    final PsiElement newElement = declaration.replace(armStatement);

    if (firstStatement != null && lastUsage != null) {
      final PsiCodeBlock tryBlock = ((PsiTryStatement)newElement).getTryBlock();
      assert tryBlock != null : newElement.getText();
      final PsiJavaToken rBrace = tryBlock.getRBrace();
      assert rBrace != null : newElement.getText();

      tryBlock.addRangeBefore(firstStatement, lastUsage, rBrace);
      codeBlock.deleteChildRange(firstStatement, lastUsage);
    }

    final PsiElement formattedElement = CodeStyleManager.getInstance(project).reformat(newElement);

    if (lastUsage == null) {
      final PsiCodeBlock tryBlock = ((PsiTryStatement)formattedElement).getTryBlock();
      if (tryBlock != null) {
        final PsiJavaToken brace = tryBlock.getLBrace();
        if (brace != null) {
          editor.getCaretModel().moveToOffset(brace.getTextOffset() + 1);
        }
      }
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.surround.resource.with.ARM.block");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
