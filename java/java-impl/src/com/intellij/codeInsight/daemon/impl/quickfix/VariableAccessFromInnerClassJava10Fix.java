// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Collections.emptyList;

public class VariableAccessFromInnerClassJava10Fix extends BaseIntentionAction implements HighPriorityAction {
  private final PsiElement myContext;

  public VariableAccessFromInnerClassJava10Fix(PsiElement context) {
    myContext = context;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Make final";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myContext.isValid()) return false;
    PsiReferenceExpression reference = tryCast(myContext, PsiReferenceExpression.class);
    if (reference == null) return false;
    PsiLocalVariable variable = tryCast(reference.resolve(), PsiLocalVariable.class);
    if (variable == null) return false;
    String name = variable.getName();
    if (name == null) return false;
    setText(QuickFixBundle.message("convert.variable.to.field.in.anonymous.class.fix.name", name));
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(myContext)) return;
    WriteCommandAction.runWriteCommandAction(project, () -> {
      if (myContext instanceof PsiReferenceExpression && myContext.isValid()) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression)myContext;
        PsiLocalVariable variable = tryCast(referenceExpression.resolve(), PsiLocalVariable.class);
        if (variable == null) return;
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiExpression initializer = variable.getInitializer();
        final String variableText = getFieldText(variable, factory, initializer);


        PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(myContext, PsiLambdaExpression.class);
        if (lambdaExpression == null) return;
        DeclarationInfo declarationInfo = DeclarationInfo.findExistingAnonymousClass(variable, lambdaExpression);
        if (declarationInfo != null) {
          replaceReferences(variable, factory, declarationInfo.name);
          declarationInfo.replace(variableText);
          variable.delete();
          return;
        }


        String boxName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("ref", variable, true);
        String boxDeclarationText = "var " +
                                    boxName +
                                    " = new Object() { " +
                                    variableText +
                                    " };";
        PsiStatement boxDeclaration = factory.createStatementFromText(boxDeclarationText, variable);
        replaceReferences(variable, factory, boxName);
        variable.replace(boxDeclaration);
      }
    });
  }

  private static void replaceReferences(PsiLocalVariable variable, PsiElementFactory factory, String boxName) {
    List<PsiReferenceExpression> references = findReferences(variable);
    PsiExpression expr = factory.createExpressionFromText(boxName + "." + variable.getName(), null);
    for (PsiReferenceExpression reference : references) {
      reference.replace(expr);
    }
  }

  private static String getFieldText(PsiLocalVariable variable, PsiElementFactory factory, PsiExpression initializer) {
    // var x is not allowed as field
    if (initializer != null && variable.getTypeElement().isInferredType() && initializer.getType() != null) {
      PsiLocalVariable copy = (PsiLocalVariable)variable.copy();
      copy.getTypeElement().replace(factory.createTypeElement(initializer.getType()));
      return copy.getText();
    }
    else {
      return variable.getText();
    }
  }

  private static class DeclarationInfo {
    final boolean isBefore;
    final @NotNull PsiStatement myStatementToReplace;
    final @NotNull PsiAnonymousClass myAnonymousClass;
    final @NotNull PsiNewExpression myNewExpression;
    final @NotNull PsiLocalVariable myVariable;
    final @NotNull String name;

    public DeclarationInfo(boolean isBefore,
                           @NotNull PsiStatement statementToReplace,
                           @NotNull PsiAnonymousClass anonymousClass,
                           @NotNull PsiNewExpression expression, @NotNull PsiLocalVariable variable, @NotNull String name) {
      this.isBefore = isBefore;
      myStatementToReplace = statementToReplace;
      myAnonymousClass = anonymousClass;
      myNewExpression = expression;
      myVariable = variable;
      this.name = name;
    }

    @Nullable
    static DeclarationInfo findExistingAnonymousClass(@NotNull PsiVariable variable, @NotNull PsiLambdaExpression lambdaExpression) {
      PsiElement varDeclarationStatement = RefactoringUtil.getParentStatement(variable, false);
      if (varDeclarationStatement == null) return null;
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(varDeclarationStatement, PsiStatement.class);
      PsiDeclarationStatement nextDeclarationStatement = tryCast(nextStatement, PsiDeclarationStatement.class);
      DeclarationInfo nextDeclaration = findExistingAnonymousClass(variable, lambdaExpression, nextDeclarationStatement, true);
      if (nextDeclaration != null) return nextDeclaration;
      PsiDeclarationStatement previousDeclarationStatement = PsiTreeUtil.getPrevSiblingOfType(varDeclarationStatement, PsiDeclarationStatement.class);
      return findExistingAnonymousClass(variable, lambdaExpression, previousDeclarationStatement, false);
    }

    void replace(@NotNull String variableText) {
      PsiLocalVariable localVariable = (PsiLocalVariable)myVariable.copy();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(localVariable.getProject());
      PsiElement lBrace = myAnonymousClass.getLBrace();
      PsiElement rBrace = myAnonymousClass.getRBrace();
      if (lBrace == null || rBrace == null) return;
      PsiElement rBracePrev = rBrace.getPrevSibling();
      if (rBracePrev == null) return;
      StringBuilder sb = new StringBuilder();
      for (PsiElement element : myAnonymousClass.getChildren()) {
        sb.append(element.getText());
        if (isBefore && element == lBrace || !isBefore && element == rBracePrev) {
          sb.append(variableText);
        }
      }
      localVariable.setInitializer(factory.createExpressionFromText("new " + sb.toString(), myVariable));
      myStatementToReplace.replace(factory.createStatementFromText(localVariable.getText() + ";", localVariable));
    }

    @Nullable
    private static DeclarationInfo findExistingAnonymousClass(@NotNull PsiVariable variable,
                                                              @NotNull PsiLambdaExpression lambdaExpression,
                                                              @Nullable PsiDeclarationStatement declarationStatement,
                                                              boolean isBefore) {
      if (declarationStatement == null) return null;
      PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) return null;
      PsiLocalVariable localVariable = tryCast(declaredElements[0], PsiLocalVariable.class);
      if (localVariable == null) return null;
      String name = localVariable.getName();
      if (name == null) return null;
      PsiNewExpression newExpression = tryCast(localVariable.getInitializer(), PsiNewExpression.class);
      if (newExpression == null) return null;
      PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      if (anonymousClass == null) return null;
      PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
      if (outerCodeBlock == null) return null;
      ReferenceUsageVisitor visitor = new ReferenceUsageVisitor(localVariable, lambdaExpression);
      outerCodeBlock.accept(visitor);
      if (visitor.foundOutsideOfLambdaUsage) return null;
      return new DeclarationInfo(isBefore, declarationStatement, anonymousClass, newExpression, localVariable, name);
    }

    private static class ReferenceUsageVisitor extends JavaRecursiveElementVisitor {
      private final PsiLocalVariable myLocalVariable;
      private final PsiLambdaExpression myLambdaExpression;
      private boolean foundOutsideOfLambdaUsage;

      public ReferenceUsageVisitor(PsiLocalVariable localVariable, PsiLambdaExpression lambdaExpression) {
        myLocalVariable = localVariable;
        myLambdaExpression = lambdaExpression;
        foundOutsideOfLambdaUsage = false;
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (foundOutsideOfLambdaUsage) return;
        if (ExpressionUtils.isReferenceTo(expression, myLocalVariable)) {
          if (!isParent(expression, myLambdaExpression)) {
            foundOutsideOfLambdaUsage = true;
          }
        }
      }
    }
  }

  private static boolean isParent(@NotNull PsiElement place, @NotNull PsiElement parent) {
    while (place != null) {
      if (place == parent) {
        return true;
      }
      place = place.getParent();
    }
    return false;
  }

  private static List<PsiReferenceExpression> findReferences(@NotNull PsiLocalVariable variable) {
    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    if (outerCodeBlock == null) return emptyList();
    List<PsiReferenceExpression> references = new SmartList<>();
    outerCodeBlock.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable) {
          references.add(expression);
        }
      }
    });
    return references;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
