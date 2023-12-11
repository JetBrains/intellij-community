/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.initialization;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SplitDeclarationAndInitializationIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("split.declaration.and.initialization.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("split.declaration.and.initialization.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new SplitDeclarationAndInitializationPredicate();
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiElement element = findMatchingElement(context);
    if (element == null) return null;
    PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(context.findLeaf(), PsiCodeBlock.class);
    if (codeBlock != null && PsiTreeUtil.isAncestor(element, codeBlock, true)) return null;
    return Presentation.of(getTextForElement(element));
  }

  @Override
  public void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final PsiField field = (PsiField)element.getParent();
    final PsiExpression initializer = field.getInitializer();
    if (initializer == null) {
      return;
    }
    final String initializerText = CommonJavaRefactoringUtil.convertInitializerToNormalExpression(initializer, field.getType()).getText();
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) {
      return;
    }
    final boolean fieldIsStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiClassInitializer[] classInitializers = containingClass.getInitializers();
    PsiClassInitializer classInitializer = null;
    final int fieldOffset = field.getTextOffset();
    for (PsiClassInitializer existingClassInitializer : classInitializers) {
      final int initializerOffset = existingClassInitializer.getTextOffset();
      if (initializerOffset <= fieldOffset) {
        continue;
      }
      final boolean initializerIsStatic = existingClassInitializer.hasModifierProperty(PsiModifier.STATIC);
      if (initializerIsStatic == fieldIsStatic) {
        Condition<PsiReference> usedBeforeInitializer = ref -> {
          PsiElement refElement = ref.getElement();
          TextRange textRange = refElement.getTextRange();
          return textRange == null || textRange.getStartOffset() < initializerOffset;
        };
        if (!ContainerUtil
          .exists(ReferencesSearch.search(field, new LocalSearchScope(containingClass)).findAll(), usedBeforeInitializer)) {
          classInitializer = existingClassInitializer;
          break;
        }
      }
    }
    final PsiManager manager = field.getManager();
    final Project project = manager.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    if (classInitializer == null) {
      if (PsiUtil.isJavaToken(PsiTreeUtil.skipWhitespacesForward(field), JavaTokenType.COMMA)) {
        field.normalizeDeclaration();
      }
      classInitializer = (PsiClassInitializer)containingClass.addAfter(elementFactory.createClassInitializer(), field);

      // add some whitespace between the field and the class initializer
      final PsiElement whitespace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");
      containingClass.addAfter(whitespace, field);
    }
    final PsiCodeBlock body = classInitializer.getBody();
    @NonNls final String initializationStatementText = field.getName() + " = " + initializerText + ';';
    final PsiExpressionStatement statement = (PsiExpressionStatement)elementFactory.createStatementFromText(initializationStatementText, body);
    final PsiElement addedElement = body.addAfter(statement, null);
    if (fieldIsStatic) {
      final PsiModifierList modifierList = classInitializer.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
      }
    }
    initializer.delete();
    CodeStyleManager.getInstance(manager.getProject()).reformat(classInitializer);
    updater.highlight(addedElement);
  }
}