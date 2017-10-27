// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
           !(myVariable instanceof PsiParameter);
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myVariable;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final LookupElement[] suggestedInitializers = suggestInitializer(myVariable);
    LOG.assertTrue(suggestedInitializers.length > 0);
    LOG.assertTrue(suggestedInitializers[0] instanceof ExpressionLookupItem);
    final PsiExpression initializer = (PsiExpression)suggestedInitializers[0].getObject();
    myVariable.setInitializer(initializer);
    Document document = Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(file));
    PsiDocumentManager.getInstance(initializer.getProject()).doPostponedOperationsAndUnblockDocument(document);
    runAssignmentTemplate(Collections.singletonList(myVariable.getInitializer()), suggestedInitializers, editor);
  }

  public static void runAssignmentTemplate(@NotNull final List<PsiExpression> initializers,
                                           @NotNull final LookupElement[] suggestedInitializers,
                                           @Nullable Editor editor) {
    if (editor == null) return;
    LOG.assertTrue(!initializers.isEmpty());
    final PsiExpression initializer = ObjectUtils.notNull(ContainerUtil.getFirstItem(initializers));
    PsiElement context = initializers.size() == 1 ? initializer : PsiTreeUtil.findCommonParent(initializers);
    final TemplateBuilderImpl builder = (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(context);
    for (PsiExpression e : initializers) {
      builder.replaceElement(e, new Expression() {
        @NotNull
        @Override
        public Result calculateResult(ExpressionContext context1) {
          return calculateQuickResult(context1);
        }

        @NotNull
        @Override
        public Result calculateQuickResult(ExpressionContext context1) {
          return new PsiElementResult(suggestedInitializers[0].getPsiElement());
        }

        @NotNull
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
