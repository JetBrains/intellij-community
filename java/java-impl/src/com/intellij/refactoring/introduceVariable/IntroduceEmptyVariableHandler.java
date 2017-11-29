/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntroduceEmptyVariableHandler {
  private static final String VARIABLE_NAME = "IntroducedVariable";
  private static final String TYPE_NAME = "Type";

  public void invoke(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiType type) {
    Project project = file.getProject();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    int offset = editor.getCaretModel().getOffset();
    PsiElement at = file.findElementAt(offset);
    PsiElement anchorStatement = RefactoringUtil.getParentStatement(at, false);
    if (anchorStatement == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("invalid.expression.context")),
                                          RefactoringBundle.message("introduce.variable.title"), HelpID.INTRODUCE_VARIABLE);
      return;
    }

    SuggestedNameInfo suggestedNameInfo = getSuggestedVariableNames(project, type, at);
    assert suggestedNameInfo.names.length > 0;
    
    ApplicationManager.getApplication().runWriteAction(() -> {
      Document document = editor.getDocument();
      String initialVariableName = suggestedNameInfo.names[0];
      document.insertString(offset, initialVariableName);
      PsiDocumentManager.getInstance(project).commitDocument(document);

      PsiElement context = anchorStatement.getParent();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
      String value = PsiTypesUtil.getDefaultValueOfType(type);
      PsiExpression initializer = elementFactory.createExpressionFromText(value, anchorStatement);
      PsiDeclarationStatement declaration = elementFactory
        .createVariableDeclarationStatement(initialVariableName, type, initializer, context);
      if (RefactoringUtil.isLoopOrIf(context)) {
        declaration = (PsiDeclarationStatement)RefactoringUtil.putStatementInLoopBody(declaration, context, anchorStatement);
        context = declaration.getParent();
      } else {
        declaration = (PsiDeclarationStatement)context.addBefore(declaration, anchorStatement);
      }
      PsiLocalVariable localVariable = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      PsiTypeElement typeElement = localVariable.getTypeElement();
      PsiReference reference = ReferencesSearch.search(localVariable, new LocalSearchScope(context)).findFirst();
      assert reference != null;
      PsiElement variableReference = reference.getElement();
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);

      TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(context);
      templateBuilder.replaceElement(typeElement, TYPE_NAME, new ConstantNode(typeElement.getText()), true, true);
      templateBuilder.replaceElement(localVariable.getNameIdentifier(), VARIABLE_NAME, new MyExpression(suggestedNameInfo), true);
      templateBuilder.replaceElement(variableReference, VARIABLE_NAME, (String)null, false);
      templateBuilder.replaceElement(ObjectUtils.assertNotNull(localVariable.getInitializer()), "");
      templateBuilder.setEndVariableAfter(variableReference);
      Template template = templateBuilder.buildInlineTemplate();
      
      editor.getCaretModel().moveToOffset(context.getTextOffset());
      TemplateManager.getInstance(project).startTemplate(editor, template);
    });
  }

  private static SuggestedNameInfo getSuggestedVariableNames(@NotNull Project project, PsiType type, PsiElement at) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type);
    String[] strings = JavaCompletionUtil.completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo);
    SuggestedNameInfo.Delegate delegate = new SuggestedNameInfo.Delegate(strings, nameInfo);
    return codeStyleManager.suggestUniqueVariableName(delegate, at, true);
  }

  private static class MyExpression extends Expression {
    private final SuggestedNameInfo myNameInfo;

    private MyExpression(SuggestedNameInfo info) {
      myNameInfo = info;
    }

    @Nullable
    @Override
    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myNameInfo.names[0]);
    }

    @Nullable
    @Override
    public Result calculateQuickResult(ExpressionContext context) {
      return calculateResult(context);
    }

    @Nullable
    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      LookupElement[] elements = new LookupElement[myNameInfo.names.length];
      for (int i = 0; i < myNameInfo.names.length; i++) {
        String name = myNameInfo.names[i];
        elements[i] = LookupElementBuilder.create(name);
      }
      return elements;
    }
  }
}
