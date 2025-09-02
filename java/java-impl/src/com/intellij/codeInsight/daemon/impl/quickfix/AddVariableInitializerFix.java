// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.PsiElementResult;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class AddVariableInitializerFix extends PsiUpdateModCommandAction<PsiVariable> {
  private static final Logger LOG = Logger.getInstance(AddVariableInitializerFix.class);

  public AddVariableInitializerFix(@NotNull PsiVariable variable) {
    super(variable);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("quickfix.add.variable.family.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiVariable variable) {
    if (variable.hasInitializer() || variable instanceof PsiParameter) return null;
    return Presentation.of(JavaBundle.message("quickfix.add.variable.text", variable.getName())).withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiVariable variable, @NotNull ModPsiUpdater updater) {
    final LookupElement[] suggestedInitializers = suggestInitializer(variable);
    LOG.assertTrue(suggestedInitializers.length > 0);
    LookupElement firstLookupElement = suggestedInitializers[0];
    LOG.assertTrue(firstLookupElement instanceof ExpressionLookupItem);
    final PsiExpression initializer = (PsiExpression)firstLookupElement.getObject();
    variable.setInitializer(initializer);
    ModTemplateBuilder builder = updater.templateBuilder();
    builder.field(Objects.requireNonNull(variable.getInitializer()),
                  new ConstantNode(new PsiElementResult(firstLookupElement.getPsiElement())).withLookupItems(
                    suggestedInitializers));
  }

  static LookupElement @NotNull [] suggestInitializer(final PsiVariable variable) {
    PsiType type = variable.getType();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(variable.getProject());

    List<LookupElement> result = new SmartList<>();
    String defaultValue = PsiTypesUtil.getDefaultValueOfType(type);
    String customDefaultValue = PsiTypesUtil.getDefaultValueOfType(type, true);
    if (!customDefaultValue.equals(defaultValue)) {
      PsiExpression customDef = elementFactory.createExpressionFromText(customDefaultValue, variable);
      result.add(new ExpressionLookupItem((PsiExpression)JavaCodeStyleManager.getInstance(variable.getProject()).shortenClassReferences(customDef)));
    }
    result.add(new ExpressionLookupItem(elementFactory.createExpressionFromText(defaultValue, variable)));
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass != null) {
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT) && PsiUtil.hasDefaultConstructor(aClass)) {
        String typeText = type.getCanonicalText(false);
        if (aClass.getTypeParameters().length > 0 && PsiUtil.isAvailable(JavaFeature.DIAMOND_TYPES, variable)) {
          if (!PsiDiamondTypeImpl.haveConstructorsGenericsParameters(aClass)) {
            typeText = TypeConversionUtil.erasure(type).getCanonicalText(false) + "<>";
          }
        }
        final String expressionText = JavaKeywords.NEW + " " + typeText + "()";
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
