/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ChainCompletionNewVariableLookupElement extends LookupElementDecorator<LookupElement> {
  private final static Logger log = Logger.getInstance(ChainCompletionNewVariableLookupElement.class);

  private final PsiClass myPsiClass;
  private final String myNewVarName;

  public ChainCompletionNewVariableLookupElement(final PsiClass psiClass, final String newVarName, final LookupElement calledMethods) {
    super(calledMethods);
    myNewVarName = newVarName;
    myPsiClass = psiClass;
  }

  public static ChainCompletionNewVariableLookupElement create(final PsiClass psiClass, final LookupElement calledMethods) {
    final Project project = psiClass.getProject();
    final String newVarName = chooseLongestName(JavaCodeStyleManager.getInstance(project).
        suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, JavaPsiFacade.getElementFactory(project).createType(psiClass)));
    return new ChainCompletionNewVariableLookupElement(psiClass, newVarName, calledMethods);
  }

  @Override
  public void handleInsert(final InsertionContext context) {
    final RangeMarker rangeMarker = context.getDocument().createRangeMarker(context.getStartOffset(), context.getStartOffset());
    getDelegate().handleInsert(context);
    final PsiFile file = context.getFile();
    ((PsiJavaFile)file).importClass(myPsiClass);
    final PsiElement caretElement = file.findElementAt(context.getEditor().getCaretModel().getOffset());
    if (caretElement == null) {
      log.error("element on caret position MUST BE not null");
      return;
    }
    final PsiStatement statement = (PsiStatement) caretElement.getPrevSibling();
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
    if (codeBlock == null) {
      log.error("code block MUST BE not null");
      return;
    }
    final Project project = context.getProject();
    final Ref<PsiElement> insertedStatementRef = Ref.create();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    context.commitDocument();
    new WriteCommandAction.Simple(project, file) {
      @Override
      protected void run() throws Throwable {
        final PsiStatement statementFromText = elementFactory.createStatementFromText(String.format("%s %s = null;", myPsiClass.getName(), myNewVarName), null);
        insertedStatementRef.set(codeBlock.addBefore(statementFromText, statement));
      }
    }.execute();
    final PsiLiteralExpression nullKeyword = findNull(insertedStatementRef.get());
    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());
    context.getDocument().insertString(rangeMarker.getStartOffset(), myNewVarName + ".");
    context.commitDocument();
    final int offset = nullKeyword.getTextOffset();
    final int endOffset = offset + nullKeyword.getTextLength();
    context.getEditor().getSelectionModel().setSelection(offset, endOffset);
    context.getEditor().getCaretModel().moveToOffset(offset);
  }

  @NotNull
  @Override
  public String getLookupString() {
    return getDelegate().getLookupString();
  }

  @Override
  public void renderElement(final LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setItemText(myNewVarName + "." + presentation.getItemText());
  }

  private static PsiLiteralExpression findNull(final PsiElement psiElement) {
    final Collection<PsiLiteralExpression> literalExpressions = PsiTreeUtil.findChildrenOfType(psiElement, PsiLiteralExpression.class);
    for (final PsiLiteralExpression literalExpression : literalExpressions) {
      if (PsiKeyword.NULL.equals(literalExpression.getText())) {
        return literalExpression;
      }
    }
    throw new IllegalArgumentException();
  }

  private static String chooseLongestName(final SuggestedNameInfo suggestedNameInfo) {
    final String[] names = suggestedNameInfo.names;
    String longestWord = names[0];
    int maxLength = longestWord.length();
    for (int i = 1; i < names.length; i++) {
      final int length = names[i].length();
      if (length > maxLength) {
        maxLength = length;
        longestWord = names[i];
      }
    }
    return longestWord;
  }
}
