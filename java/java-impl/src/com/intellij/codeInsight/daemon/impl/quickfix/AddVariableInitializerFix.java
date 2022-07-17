// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.PsiElementResult;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AddVariableInitializerFix extends LocalQuickFixAndIntentionActionOnPsiElement 
  implements IntentionActionWithFixAllOption {
  private static final Logger LOG = Logger.getInstance(AddReturnFix.class);

  public AddVariableInitializerFix(@NotNull PsiVariable variable) {
    super(variable);
  }

  @Override
  @NotNull
  public String getText() {
    PsiVariable variable = ObjectUtils.tryCast(myStartElement.getElement(), PsiVariable.class);
    return variable == null ? getFamilyName() : JavaBundle.message("quickfix.add.variable.text", variable.getName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("quickfix.add.variable.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    PsiVariable variable = ObjectUtils.tryCast(startElement, PsiVariable.class);
    return variable != null && variable.isValid() &&
           BaseIntentionAction.canModify(variable) &&
           !variable.hasInitializer() &&
           !(variable instanceof PsiParameter);
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return file;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiVariable variable = ObjectUtils.tryCast(startElement, PsiVariable.class);
    if (variable == null) return;
    final LookupElement[] suggestedInitializers = suggestInitializer(variable);
    LOG.assertTrue(suggestedInitializers.length > 0);
    LOG.assertTrue(suggestedInitializers[0] instanceof ExpressionLookupItem);
    final PsiExpression initializer = (PsiExpression)suggestedInitializers[0].getObject();
    variable.setInitializer(initializer);
    Document document = Objects.requireNonNull(file.getViewProvider().getDocument());
    PsiDocumentManager.getInstance(initializer.getProject()).doPostponedOperationsAndUnblockDocument(document);
    runAssignmentTemplate(Collections.singletonList(variable.getInitializer()), suggestedInitializers, editor);
  }

  static void runAssignmentTemplate(@NotNull final List<? extends PsiExpression> initializers,
                                    final LookupElement @NotNull [] suggestedInitializers,
                                    @Nullable Editor editor) {
    if (editor == null) return;
    LOG.assertTrue(!initializers.isEmpty());
    final PsiExpression initializer = Objects.requireNonNull(ContainerUtil.getFirstItem(initializers));
    PsiElement context = initializers.size() == 1 ? initializer : PsiTreeUtil.findCommonParent(initializers);
    if (context == null) return;
    final TemplateBuilderImpl builder = (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(context);
    for (PsiExpression e : initializers) {
      builder.replaceElement(e, new ConstantNode(new PsiElementResult(suggestedInitializers[0].getPsiElement())).withLookupItems(suggestedInitializers));
    }
    builder.run(editor, false);
  }

  static LookupElement @NotNull [] suggestInitializer(final PsiVariable variable) {
    PsiType type = variable.getType();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(variable.getProject());

    final List<LookupElement> result = new SmartList<>();
    final String defaultValue = PsiTypesUtil.getDefaultValueOfType(type);
    final ExpressionLookupItem defaultExpression = new ExpressionLookupItem(elementFactory.createExpressionFromText(defaultValue, variable));
    result.add(defaultExpression);
    if (type instanceof PsiClassType) {
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        result.add(new ExpressionLookupItem(elementFactory.createExpressionFromText("\"\"", variable)));
      }
      final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
      if (aClass != null && !aClass.hasModifierProperty(PsiModifier.ABSTRACT) && PsiUtil.hasDefaultConstructor(aClass)) {
        String typeText = type.getCanonicalText(false);
        if (aClass.getTypeParameters().length > 0 && PsiUtil.isLanguageLevel7OrHigher(variable)) {
          if (!PsiDiamondTypeImpl.haveConstructorsGenericsParameters(aClass)) {
            typeText = TypeConversionUtil.erasure(type).getCanonicalText(false) + "<>";
          }
        }
        final String expressionText = PsiKeyword.NEW + " " + typeText + "()";
        PsiExpression initializer = elementFactory.createExpressionFromText(expressionText, variable);
        String variableName = variable.getName();
        LOG.assertTrue(variableName != null);
        PsiDeclarationStatement statement = elementFactory.createVariableDeclarationStatement(variableName, variable.getType(), initializer, variable);
        ExpressionLookupItem newExpression = new ExpressionLookupItem(((PsiLocalVariable)statement.getDeclaredElements()[0]).getInitializer());
        result.add(newExpression);
      }
    }
    return result.toArray(LookupElement.EMPTY_ARRAY);
  }
}
