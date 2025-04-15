// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * @author Dmitry Batkovich
 */
public final class DataPointHolderConversionIntention extends PsiUpdateModCommandAction<PsiIdentifier> {
  private static final String THEORIES_PACKAGE = "org.junit.experimental.theories";
  private static final String DATA_POINT_FQN = THEORIES_PACKAGE + ".DataPoint";
  private static final String DATA_POINTS_FQN = THEORIES_PACKAGE + ".DataPoints";
  
  public DataPointHolderConversionIntention() {
    super(PsiIdentifier.class);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiIdentifier element, @NotNull ModPsiUpdater updater) {
    final PsiElement holder = element.getParent();
    PsiModifierListOwner createdElement =
      holder instanceof PsiField ? convertToMethod((PsiField)holder) : convertToField((PsiMethod)holder);

    final PsiModifierListOwner oldElement = (PsiModifierListOwner)holder;
    final PsiAnnotation psiAnnotation = AnnotationUtil.findAnnotation(oldElement, DATA_POINT_FQN, DATA_POINTS_FQN);
    assert psiAnnotation != null;
    final String annotation = psiAnnotation.getQualifiedName();
    assert annotation != null;
    final PsiModifierList modifierList = createdElement.getModifierList();
    assert modifierList != null;
    modifierList.addAnnotation(annotation);
    modifierList.setModifierProperty(PsiModifier.STATIC, true);
    modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
    createdElement = (PsiModifierListOwner)oldElement.replace(createdElement);

    final PsiNameIdentifierOwner asNameIdOwner = (PsiNameIdentifierOwner)createdElement;
    updater.rename(asNameIdOwner, List.of(requireNonNull(asNameIdOwner.getName())));
  }

  private static PsiField convertToField(final PsiMethod method) {
    final Project project = method.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    final String fieldName = codeStyleManager.propertyNameToVariableName(method.getName(), VariableKind.STATIC_FIELD);
    final PsiType returnType = method.getReturnType();
    assert returnType != null;
    final PsiField field = elementFactory.createField(fieldName, returnType);
    final PsiStatement returnStatement = PsiTreeUtil.findChildOfType(method, PsiStatement.class);
    if (returnStatement != null) {
      field.setInitializer(((PsiReturnStatement)returnStatement).getReturnValue());
    }
    return field;
  }

  private static PsiMethod convertToMethod(final PsiField field) {
    final Project project = field.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    final PsiExpression fieldInitializer = field.getInitializer();

    final PsiMethod method =
      elementFactory.createMethod(codeStyleManager.variableNameToPropertyName(field.getName(), VariableKind.STATIC_FIELD), field.getType());
    PsiCodeBlock body = method.getBody();
    assert body != null;

    final PsiStatement methodCode =
      elementFactory.createStatementFromText(JavaKeywords.RETURN + " " + requireNonNull(fieldInitializer).getText() + ";", null);
    body.add(methodCode);
    return method;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiIdentifier element) {
    final DataPointsHolder dataPointsHolder = extractDataPointsHolder(element);
    if (dataPointsHolder == null || !isConvertible(dataPointsHolder.holder())) return null;
    final String annotation = requireNonNull(dataPointsHolder.annotation().getNameReferenceElement()).getReferenceName();
    return Presentation.of(IntentionPowerPackBundle.message("intention.name.replace.field.or.method", annotation,
                                                            dataPointsHolder.holder() instanceof PsiMethod ? 0 : 1));
  }
  
  

  private static DataPointsHolder extractDataPointsHolder(final @NotNull PsiIdentifier element) {
    final PsiElement maybeHolder = element.getParent();
    if (!(maybeHolder instanceof PsiMethod || maybeHolder instanceof PsiField)) {
      return null;
    }
    final PsiMember holder = (PsiMember)maybeHolder;
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(holder, DATA_POINT_FQN, DATA_POINTS_FQN);
    return annotation == null ? null : new DataPointsHolder(holder, annotation);
  }

  private record DataPointsHolder(PsiMember holder, PsiAnnotation annotation) {
  }

  private static boolean isConvertible(final @NotNull PsiMember member) {
    if (!(member instanceof PsiMethod method)) {
      return ((PsiField)member).getInitializer() != null;
    }
    final PsiType returnType = method.getReturnType();
    if (returnType == null || returnType.equals(PsiTypes.voidType()) || !method.getParameterList().isEmpty()) {
      return false;
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return false;
    }
    final PsiStatement[] methodStatements = body.getStatements();
    return switch (methodStatements.length) {
      case 1 -> methodStatements[0] instanceof PsiReturnStatement;
      case 0 -> true;
      default -> false;
    };
  }

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("convert.datapoints.fix.family.name");
  }
}
