// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Collections.emptyList;

public class VariableAccessFromInnerClassJava10Fix extends BaseIntentionAction {
  @NonNls private final static String[] NAMES = {
    "ref",
    "lambdaContext",
    "context",
    "rContext"
  };

  private final PsiElement myContext;

  public VariableAccessFromInnerClassJava10Fix(PsiElement context) {
    myContext = context;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.variable.access.from.inner.class");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!PsiUtil.isLanguageLevel10OrHigher(file)) return false;
    if (!myContext.isValid()) return false;
    PsiReferenceExpression reference = tryCast(myContext, PsiReferenceExpression.class);
    if (reference == null) return false;
    PsiLocalVariable variable = tryCast(reference.resolve(), PsiLocalVariable.class);
    if (variable == null) return false;
    PsiDeclarationStatement declarationStatement = tryCast(variable.getParent(), PsiDeclarationStatement.class);
    if (declarationStatement == null) return false;
    if (declarationStatement.getDeclaredElements().length != 1) return false;
    String name = variable.getName();

    PsiType type = variable.getType();
    if (!PsiTypesUtil.isDenotableType(type, variable)) {
      return false;
    }
    setText(QuickFixBundle.message("convert.variable.to.field.in.anonymous.class.fix.name", name));
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!(myContext instanceof PsiReferenceExpression) || !myContext.isValid()) return;
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(myContext)) return;

    PsiReferenceExpression referenceExpression = (PsiReferenceExpression)myContext;
    PsiLocalVariable variable = tryCast(referenceExpression.resolve(), PsiLocalVariable.class);
    if (variable == null) return;
    final String variableText = getFieldText(variable);

    PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(myContext, PsiLambdaExpression.class);
    if (lambdaExpression == null) return;
    DeclarationInfo declarationInfo = DeclarationInfo.findExistingAnonymousClass(variable);

    if (declarationInfo != null) {
      replaceReferences(variable, declarationInfo.myName);
      declarationInfo.replace(variableText);
      variable.delete();
      return;
    }


    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    String boxName = codeStyleManager.suggestUniqueVariableName(NAMES[0], variable, true);
    String boxDeclarationText = "var " +
                                boxName +
                                " = new Object(){" +
                                variableText +
                                "};";
    PsiStatement boxDeclaration = JavaPsiFacade.getElementFactory(project).createStatementFromText(boxDeclarationText, variable);
    replaceReferences(variable, boxName);
    if (editor == null) {
      variable.replace(boxDeclaration);
      return;
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
    if (statement == null) return;
    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement.replace(boxDeclaration);
    PsiLocalVariable localVariable = (PsiLocalVariable)declarationStatement.getDeclaredElements()[0];
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    SmartPsiElementPointer<PsiLocalVariable> pointer = smartPointerManager.createSmartPsiElementPointer(localVariable);
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    PsiLocalVariable varToChange = pointer.getElement();
    if (varToChange == null) return;
    editor.getCaretModel().moveToOffset(varToChange.getTextOffset());
    editor.getSelectionModel().removeSelection();
    LinkedHashSet<String> suggestions = Arrays.stream(NAMES)
      .map(suggestion -> codeStyleManager.suggestUniqueVariableName(suggestion, varToChange, var -> var == varToChange))
      .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    new MemberInplaceRenamer(varToChange, varToChange, editor).performInplaceRefactoring(suggestions);
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

  private static class DeclarationInfo {
    private final boolean myIsBefore;
    private final @NotNull PsiLocalVariable myVariable;
    private final @NotNull String myName;

    DeclarationInfo(boolean isBefore, @NotNull PsiLocalVariable variable, @NotNull String name) {
      myIsBefore = isBefore;
      myVariable = variable;
      myName = name;
    }

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
      PsiNewExpression newExpression = (PsiNewExpression)myVariable.getInitializer();
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
        if (!myIsBefore && child == rBrace) expressionText.append(variableText);
        expressionText.append(child.getText());
        if (myIsBefore && child == lBrace) expressionText.append(variableText);
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myVariable.getProject());
      myVariable.setInitializer(factory.createExpressionFromText(expressionText.toString(), myVariable));
      PsiTypeElement typeElement = myVariable.getTypeElement();
      if (!typeElement.isInferredType()) {
        typeElement.replace(factory.createTypeElementFromText("var", myVariable));
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
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (ExpressionUtils.isReferenceTo(expression, variable)) {
          references.add(expression);
        }
      }
    });
    return references;
  }
}
