// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class IntroduceEmptyVariableHandlerImpl implements JavaIntroduceEmptyVariableHandlerBase {
  private static final String VARIABLE_NAME = "IntroducedVariable";
  private static final String TYPE_NAME = "Type";

  @Override
  public IntentionPreviewInfo generatePreview(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiType type) {
    Project project = file.getProject();
    int offset = editor.getCaretModel().getOffset();
    PsiElement at = file.findElementAt(offset);
    PsiElement anchorStatement = CommonJavaRefactoringUtil.getParentStatement(at, false);
    if (anchorStatement == null) return IntentionPreviewInfo.EMPTY;
    SuggestedNameInfo suggestedNameInfo = getSuggestedVariableNames(project, type, at);
    assert suggestedNameInfo.names.length > 0;
    Document document = editor.getDocument();
    String initialVariableName = insertArgument(project, document, offset, suggestedNameInfo);
    PsiLocalVariable localVariable = addLocalVariable(project, anchorStatement, type, initialVariableName);
    PsiExpression initializer = localVariable.getInitializer();
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document); // reformat before deleting initializer
    if (initializer != null) {
      TextRange initRange = initializer.getTextRange();
      document.deleteString(initRange.getStartOffset(), initRange.getEndOffset());
    }
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public void invoke(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiType type) {
    Project project = file.getProject();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    int offset = editor.getCaretModel().getOffset();
    PsiElement at = file.findElementAt(offset);
    PsiElement anchorStatement = CommonJavaRefactoringUtil.getParentStatement(at, false);
    if (anchorStatement == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(
        JavaRefactoringBundle.message("invalid.expression.context")),
                                          RefactoringBundle.message("introduce.variable.title"), HelpID.INTRODUCE_VARIABLE);
      return;
    }

    SuggestedNameInfo suggestedNameInfo = getSuggestedVariableNames(project, type, at);
    assert suggestedNameInfo.names.length > 0;

    ApplicationManager.getApplication().runWriteAction(() -> {
      Document document = editor.getDocument();
      String initialVariableName = insertArgument(project, document, offset, suggestedNameInfo);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      PsiLocalVariable localVariable = addLocalVariable(project, anchorStatement, type, initialVariableName);
      PsiElement context = PsiTreeUtil.getParentOfType(localVariable, PsiCodeBlock.class);
      if (context == null) return;
      PsiTypeElement typeElement = localVariable.getTypeElement();
      PsiReference reference = ReferencesSearch.search(localVariable, new LocalSearchScope(context)).findFirst();
      assert reference != null;
      PsiElement variableReference = reference.getElement();
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
      TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(context);
      templateBuilder.replaceElement(typeElement, TYPE_NAME, new ConstantNode(typeElement.getText()), true, true);
      templateBuilder.replaceElement(localVariable.getNameIdentifier(), VARIABLE_NAME,
                                     new ConstantNode(suggestedNameInfo.names[0]).withLookupStrings(suggestedNameInfo.names), true);
      templateBuilder.replaceElement(variableReference, VARIABLE_NAME, (String)null, false);
      templateBuilder.replaceElement(Objects.requireNonNull(localVariable.getInitializer()), "");
      templateBuilder.setEndVariableAfter(variableReference);
      Template template = templateBuilder.buildInlineTemplate();
      editor.getCaretModel().moveToOffset(context.getTextOffset());
      TemplateManager.getInstance(project).startTemplate(editor, template);
    });
  }

  private static String insertArgument(Project project, Document document, int offset, SuggestedNameInfo suggestedNameInfo) {
    String initialVariableName = suggestedNameInfo.names[0];
    document.insertString(offset, initialVariableName);
    PsiDocumentManager.getInstance(project).commitDocument(document);
    return initialVariableName;
  }

  private static PsiLocalVariable addLocalVariable(
    Project project,
    PsiElement anchorStatement,
    PsiType type,
    String initialVariableName
  ) {
    PsiElement context = anchorStatement.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    String value = PsiTypesUtil.getDefaultValueOfType(type);
    PsiExpression initializer = factory.createExpressionFromText(value, anchorStatement);
    PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(initialVariableName, type, initializer, context);
    if (CommonJavaRefactoringUtil.isLoopOrIf(context)) {
      declaration = (PsiDeclarationStatement)CommonJavaRefactoringUtil.putStatementInLoopBody(declaration, context, anchorStatement);
    } else {
      declaration = (PsiDeclarationStatement)context.addBefore(declaration, anchorStatement);
    }
    return (PsiLocalVariable)declaration.getDeclaredElements()[0];
  }

  private static SuggestedNameInfo getSuggestedVariableNames(@NotNull Project project, PsiType type, PsiElement at) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type);
    String[] strings = JavaCompletionUtil.completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo);
    SuggestedNameInfo.Delegate delegate = new SuggestedNameInfo.Delegate(strings, nameInfo);
    return codeStyleManager.suggestUniqueVariableName(delegate, at, true);
  }

}
