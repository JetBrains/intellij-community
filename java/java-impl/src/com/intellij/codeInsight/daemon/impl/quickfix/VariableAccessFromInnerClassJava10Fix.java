// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Collections.emptyList;

public final class VariableAccessFromInnerClassJava10Fix extends PsiUpdateModCommandAction<PsiElement> {
  @NonNls private final static String[] NAMES = {
    "ref",
    "lambdaContext",
    "context",
    "rContext"
  };

  public VariableAccessFromInnerClassJava10Fix(PsiElement context) {
    super(context);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.variable.access.from.inner.class");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!HighlightingFeature.LVTI.isAvailable(context.file())) return null;
    PsiReferenceExpression reference = tryCast(element, PsiReferenceExpression.class);
    if (reference == null) return null;
    PsiLocalVariable variable = tryCast(reference.resolve(), PsiLocalVariable.class);
    if (variable == null) return null;
    PsiDeclarationStatement declarationStatement = tryCast(variable.getParent(), PsiDeclarationStatement.class);
    if (declarationStatement == null) return null;
    if (declarationStatement.getDeclaredElements().length != 1) return null;
    String name = variable.getName();

    PsiType type = variable.getType();
    if (!PsiTypesUtil.isDenotableType(type, variable)) {
      return null;
    }
    return Presentation.of(QuickFixBundle.message("convert.variable.to.field.in.anonymous.class.fix.name", name));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PsiReferenceExpression referenceExpression)) return;

    PsiLocalVariable variable = tryCast(referenceExpression.resolve(), PsiLocalVariable.class);
    if (variable == null) return;
    final String variableText = getFieldText(variable);

    DeclarationInfo declarationInfo = DeclarationInfo.findExistingAnonymousClass(variable);

    if (declarationInfo != null) {
      replaceReferences(variable, declarationInfo.name);
      declarationInfo.replace(variableText);
      variable.delete();
      return;
    }

    Project project = context.project();
    List<String> suggestions = new VariableNameGenerator(variable, VariableKind.LOCAL_VARIABLE)
      .byName(NAMES).generateAll(true);
    String boxName = suggestions.get(0);
    String boxDeclarationText = "var " +
                                boxName +
                                " = new Object(){" +
                                variableText +
                                "};";
    PsiStatement boxDeclaration = JavaPsiFacade.getElementFactory(project).createStatementFromText(boxDeclarationText, variable);
    replaceReferences(variable, boxName);
    PsiStatement statement = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
    if (statement == null) return;
    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement.replace(boxDeclaration);
    PsiLocalVariable localVariable = (PsiLocalVariable)declarationStatement.getDeclaredElements()[0];
    updater.rename(localVariable, suggestions);
  }

  private static void replaceReferences(PsiLocalVariable variable, String boxName) {
    List<PsiReferenceExpression> references = findReferences(variable);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
    PsiExpression expr = factory.createExpressionFromText(boxName + "." + variable.getName(), null);
    for (PsiReferenceExpression reference : references) {
      reference.replace(expr);
    }
  }

  private static String getFieldText(PsiLocalVariable variable) {
    // var x is not allowed as field
    if (variable.getTypeElement().isInferredType()) {
      PsiLocalVariable copy = (PsiLocalVariable)variable.copy();
      PsiTypesUtil.replaceWithExplicitType(copy.getTypeElement());
      return copy.getText();
    }
    else {
      return variable.getText();
    }
  }

  private record DeclarationInfo(boolean isBefore, @NotNull PsiLocalVariable variable, @NotNull String name) {

    @Nullable
    static DeclarationInfo findExistingAnonymousClass(@NotNull PsiVariable variable) {
      PsiElement varDeclarationStatement = CommonJavaRefactoringUtil.getParentStatement(variable, false);
      if (varDeclarationStatement == null) return null;
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(varDeclarationStatement, PsiStatement.class);
      PsiDeclarationStatement nextDeclarationStatement = tryCast(nextStatement, PsiDeclarationStatement.class);
      DeclarationInfo nextDeclaration = findExistingAnonymousClass(variable, nextDeclarationStatement, true);
      if (nextDeclaration != null) return nextDeclaration;
      PsiDeclarationStatement previousDeclarationStatement =
        PsiTreeUtil.getPrevSiblingOfType(varDeclarationStatement, PsiDeclarationStatement.class);
      return findExistingAnonymousClass(variable, previousDeclarationStatement, false);
    }

    void replace(@NotNull String variableText) {
      PsiNewExpression newExpression = (PsiNewExpression)variable.getInitializer();
      assert newExpression != null;
      PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      assert anonymousClass != null;
      PsiElement lBrace = anonymousClass.getLBrace();
      PsiElement rBrace = anonymousClass.getRBrace();
      if (lBrace == null || rBrace == null) return;
      StringBuilder expressionText = new StringBuilder();
      for (PsiElement child = newExpression.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child == anonymousClass) break;
        expressionText.append(child.getText());
      }
      for (PsiElement child = anonymousClass.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (!isBefore && child == rBrace) expressionText.append(variableText);
        expressionText.append(child.getText());
        if (isBefore && child == lBrace) expressionText.append(variableText);
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
      variable.setInitializer(factory.createExpressionFromText(expressionText.toString(), variable));
      PsiTypeElement typeElement = variable.getTypeElement();
      if (!typeElement.isInferredType()) {
        typeElement.replace(factory.createTypeElementFromText("var", variable));
      }
    }

    @Nullable
    private static DeclarationInfo findExistingAnonymousClass(@NotNull PsiVariable variable,
                                                              @Nullable PsiDeclarationStatement declarationStatement,
                                                              boolean isBefore) {
      if (declarationStatement == null) return null;
      PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) return null;
      PsiLocalVariable localVariable = tryCast(declaredElements[0], PsiLocalVariable.class);
      if (localVariable == null) return null;
      String boxName = localVariable.getName();
      PsiNewExpression newExpression = tryCast(localVariable.getInitializer(), PsiNewExpression.class);
      if (newExpression == null) return null;
      PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      if (anonymousClass == null) return null;
      String variableName = variable.getName();
      if (variableName == null) return null;
      if (!TypeUtils.isJavaLangObject(anonymousClass.getBaseClassType())) return null;
      if (Arrays.stream(anonymousClass.getFields())
        .map(field -> field.getName())
        .anyMatch(name -> name.equals(variableName))) {
        return null;
      }
      return new DeclarationInfo(isBefore, localVariable, boxName);
    }
  }

  private static List<PsiReferenceExpression> findReferences(@NotNull PsiLocalVariable variable) {
    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    if (outerCodeBlock == null) return emptyList();
    List<PsiReferenceExpression> references = new SmartList<>();
    outerCodeBlock.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (ExpressionUtils.isReferenceTo(expression, variable)) {
          references.add(expression);
        }
      }
    });
    return references;
  }
}
