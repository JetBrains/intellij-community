// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CreateRecordFromNewFix extends CreateClassFromNewFix {
  public CreateRecordFromNewFix(PsiNewExpression newExpression) {
    super(newExpression);
  }

  @NotNull
  @Override
  protected CreateClassKind getKind() {
    return CreateClassKind.RECORD;
  }

  @NotNull
  @Override
  TemplateBuilderImpl createConstructorTemplate(PsiClass aClass, PsiNewExpression newExpression, PsiExpressionList argList) {
    TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(aClass);
    PsiRecordHeader header = aClass.getRecordHeader();
    setupRecordComponents(header, templateBuilder, argList, getTargetSubstitutor(newExpression));
    return templateBuilder;
  }

  static void setupRecordComponents(@Nullable PsiRecordHeader header, @NotNull TemplateBuilder builder,
                                    @NotNull PsiExpressionList argumentList, @NotNull PsiSubstitutor substitutor)
    throws IncorrectOperationException {
    if (header == null) return;
    PsiExpression[] args = argumentList.getExpressions();
    final PsiManager psiManager = header.getManager();
    final Project project = psiManager.getProject();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    GlobalSearchScope resolveScope = header.getResolveScope();
    GuessTypeParameters guesser = new GuessTypeParameters(project, JavaPsiFacade.getElementFactory(project), builder, substitutor);

    final PsiClass containingClass = header.getContainingClass();
    if (containingClass == null) return;
    class ComponentData {
      final PsiType myType;
      final String[] myNames;

      ComponentData(PsiType type, String[] names) {
        myType = type;
        myNames = names;
      }

      @Override
      public String toString() {
        return myType.getCanonicalText() + " " + myNames[0];
      }
    }
    List<ComponentData> components = new ArrayList<>();
    //255 is the maximum number of record components
    for (int i = 0; i < Math.min(args.length, 255); i++) {
      PsiExpression exp = args[i];

      PsiType argType = CommonJavaRefactoringUtil.getTypeByExpression(exp);
      SuggestedNameInfo suggestedInfo = JavaCodeStyleManager.getInstance(project).suggestVariableName(
        VariableKind.PARAMETER, null, exp, argType);
      @NonNls String[] names = suggestedInfo.names;

      if (names.length == 0) {
        names = new String[]{"c" + i};
      }

      argType = CreateFromUsageUtils.getParameterTypeByArgumentType(argType, psiManager, resolveScope);
      components.add(new ComponentData(argType, names));
    }
    PsiRecordHeader newHeader = factory.createRecordHeaderFromText(StringUtil.join(components, ", "), containingClass);
    PsiRecordHeader replacedHeader = (PsiRecordHeader)header.replace(newHeader);
    PsiRecordComponent[] recordComponents = replacedHeader.getRecordComponents();
    assert recordComponents.length == components.size();
    for (int i = 0; i < recordComponents.length; i++) {
      PsiRecordComponent component = recordComponents[i];
      ComponentData data = components.get(i);

      ExpectedTypeInfo info = ExpectedTypesProvider.createInfo(data.myType, ExpectedTypeInfo.TYPE_OR_SUPERTYPE, data.myType, TailType.NONE);

      PsiElement context = PsiTreeUtil.getParentOfType(argumentList, PsiClass.class, PsiMethod.class);
      guesser.setupTypeElement(Objects.requireNonNull(component.getTypeElement()), new ExpectedTypeInfo[]{info}, context, containingClass);

      Expression expression = new CreateFromUsageUtils.ParameterNameExpression(data.myNames);
      builder.replaceElement(Objects.requireNonNull(component.getNameIdentifier()), expression);
    }
  }
}
