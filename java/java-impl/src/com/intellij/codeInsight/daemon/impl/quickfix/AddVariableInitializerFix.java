/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.completion.JavaInheritorsGetter;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.StatisticsWeigher;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.JavaMembersGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.sorted;

public class AddVariableInitializerFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddReturnFix");
  private final PsiVariable myVariable;

  public AddVariableInitializerFix(@NotNull PsiVariable variable) {
    myVariable = variable;
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("quickfix.add.variable.text", myVariable.getName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("quickfix.add.variable.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myVariable.isValid() &&
           myVariable.getManager().isInProject(myVariable) &&
           !myVariable.hasInitializer() &&
           !(myVariable instanceof PsiParameter)
        ;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile())) return;
    final LookupElement[] suggestedInitializers = suggestInitializer(myVariable);
    LOG.assertTrue(suggestedInitializers.length > 0);
    LOG.assertTrue(suggestedInitializers[0] instanceof ExpressionLookupItem);
    final PsiExpression initializer = (PsiExpression)suggestedInitializers[0].getObject();
    if (myVariable instanceof PsiLocalVariable) {
      ((PsiLocalVariable)myVariable).setInitializer(initializer);
    }
    else if (myVariable instanceof PsiField) {
      ((PsiField)myVariable).setInitializer(initializer);
    }
    else {
      LOG.error("Unknown variable type: " + myVariable);
    }
    runAssignmentTemplate(Collections.singletonList(myVariable.getInitializer()), suggestedInitializers, editor);
  }

  public static void runAssignmentTemplate(@NotNull final List<PsiExpression> initializers,
                                           @NotNull final LookupElement[] suggestedInitializers,
                                           @Nullable Editor editor) {
    if (editor == null) return;
    LOG.assertTrue(!initializers.isEmpty());
    final PsiExpression initializer = ContainerUtil.getFirstItem(initializers);
    PsiElement context = initializers.size() == 1 ? initializer : PsiTreeUtil.findCommonParent(initializers);
    PsiDocumentManager.getInstance(initializer.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    final TemplateBuilderImpl builder = (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(context);
    for (PsiExpression e : initializers) {
      builder.replaceElement(e, new Expression() {
        @Nullable
        @Override
        public Result calculateResult(ExpressionContext context1) {
          return calculateQuickResult(context1);
        }

        @Nullable
        @Override
        public Result calculateQuickResult(ExpressionContext context1) {
          return new PsiElementResult(suggestedInitializers[0].getPsiElement());
        }

        @Nullable
        @Override
        public LookupElement[] calculateLookupItems(ExpressionContext context1) {
          return suggestedInitializers;
        }
      });
    }
    builder.run(editor, false);
  }

  @NotNull
  public static LookupElement[] suggestInitializer(final PsiVariable variable) {
    PsiType type = variable.getType();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(variable.getProject());

    final List<LookupElement> result = new SmartList<>();
    final String defaultValue = PsiTypesUtil.getDefaultValueOfType(type);
    final ExpressionLookupItem defaultExpression = new ExpressionLookupItem(elementFactory.createExpressionFromText(defaultValue, variable));
    result.add(defaultExpression);
    if (type instanceof PsiClassType) {
      final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
      if (aClass != null && PsiUtil.hasDefaultConstructor(aClass)) {
        final String expressionText = PsiKeyword.NEW + " " + type.getCanonicalText(false) + "()";
        ExpressionLookupItem newExpression = new ExpressionLookupItem(elementFactory.createExpressionFromText(expressionText, variable));
        result.add(newExpression);
      }
    }
    return result.toArray(new LookupElement[result.size()]);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
