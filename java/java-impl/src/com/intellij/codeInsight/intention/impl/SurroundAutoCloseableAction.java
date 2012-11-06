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
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

    final LocalSearchScope scope = new LocalSearchScope(codeBlock);
    PsiElement last = null;
    for (PsiReference reference : ReferencesSearch.search(variable, scope).findAll()) {
      final PsiElement usage = PsiTreeUtil.findPrevParent(codeBlock, reference.getElement());
      if ((last == null || usage.getTextOffset() > last.getTextOffset())) {
        last = usage;
      }
    }

    final String text = "try (" + variable.getTypeElement().getText() + " " + variable.getName() + " = " + initializer.getText() + ") {}";
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiTryStatement armStatement = (PsiTryStatement)declaration.replace(factory.createStatementFromText(text, codeBlock));

    List<PsiElement> toFormat = null;
    if (last != null) {
      final PsiElement first = armStatement.getNextSibling();
      if (first != null) {
        toFormat = moveStatements(first, last, armStatement);
      }
    }

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final PsiElement formattedElement = codeStyleManager.reformat(armStatement);
    if (toFormat != null) {
      for (PsiElement psiElement : toFormat) {
        codeStyleManager.reformat(psiElement);
      }
    }

    if (last == null) {
      final PsiCodeBlock tryBlock = ((PsiTryStatement)formattedElement).getTryBlock();
      if (tryBlock != null) {
        final PsiJavaToken brace = tryBlock.getLBrace();
        if (brace != null) {
          editor.getCaretModel().moveToOffset(brace.getTextOffset() + 1);
        }
      }
    }
  }

  @Nullable
  private static List<PsiElement> moveStatements(@NotNull final PsiElement first, final PsiElement last, final PsiTryStatement statement) {
    final PsiCodeBlock tryBlock = statement.getTryBlock();
    assert tryBlock != null : statement.getText();
    final PsiJavaToken rBrace = tryBlock.getRBrace();
    assert rBrace != null : statement.getText();

    final PsiElement parent = statement.getParent();
    final LocalSearchScope scope = new LocalSearchScope(parent);
    List<PsiElement> toFormat = null, toDelete = null;

    final PsiElement stopAt = last.getNextSibling();
    for (PsiElement child = first; child != null && child != stopAt; child = child.getNextSibling()) {
      if (!(child instanceof PsiDeclarationStatement)) continue;

      final PsiElement[] declaredElements = ((PsiDeclarationStatement)child).getDeclaredElements();
      int varsProcessed = 0;
      for (PsiElement declared : declaredElements) {
        if (!(declared instanceof PsiLocalVariable)) continue;

        final boolean contained = ReferencesSearch.search(declared, scope).forEach(new Processor<PsiReference>() {
          @Override
          public boolean process(PsiReference reference) {
            return reference.getElement().getTextOffset() <= last.getTextRange().getEndOffset();
          }
        });

        if (!contained) {
          final PsiLocalVariable var = (PsiLocalVariable)declared;
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());
          final String name = var.getName();
          assert name != null : child.getText();

          toFormat = plus(toFormat, parent.addBefore(factory.createVariableDeclarationStatement(name, var.getType(), null), statement));

          final PsiExpression varInit = var.getInitializer();
          if (varInit != null) {
            final String varAssignText = name + " = " + varInit.getText() + ";";
            parent.addBefore(factory.createStatementFromText(varAssignText, parent), child.getNextSibling());
          }

          ++varsProcessed;
          toDelete = plus(toDelete, declared);
          declared.delete();
        }
      }

      if (varsProcessed == declaredElements.length) {
        toDelete = plus(toDelete, child);
      }
    }

    if (toDelete != null) {
      for (PsiElement element : toDelete) {
        if (element.isValid()) {
          element.delete();
        }
      }
    }

    tryBlock.addRangeBefore(first, last, rBrace);
    parent.deleteChildRange(first, last);

    return toFormat;
  }

  private static List<PsiElement> plus(@Nullable List<PsiElement> list, PsiElement element) {
    if (list == null) list = ContainerUtil.newArrayList();
    list.add(element);
    return list;
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
