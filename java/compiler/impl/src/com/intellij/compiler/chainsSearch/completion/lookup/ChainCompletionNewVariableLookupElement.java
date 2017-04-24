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
package com.intellij.compiler.chainsSearch.completion.lookup;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class ChainCompletionNewVariableLookupElement extends LookupElementDecorator<LookupElement> {
  private static final Logger LOG = Logger.getInstance(ChainCompletionNewVariableLookupElement.class);

  @NotNull
  private final PsiClass myQualifierClass;
  @NotNull
  private final String myNewVarName;

  public ChainCompletionNewVariableLookupElement(@NotNull final PsiClass qualifierClass, final LookupElement calledMethods) {
    super(calledMethods);
    myNewVarName = StringUtil.decapitalize(ObjectUtils.notNull(qualifierClass.getName()));
    myQualifierClass = qualifierClass;
  }

  @Override
  public void handleInsert(final InsertionContext context) {
    final RangeMarker rangeMarker = context.getDocument().createRangeMarker(context.getStartOffset(), context.getStartOffset());
    getDelegate().handleInsert(context);
    context.getDocument().insertString(rangeMarker.getStartOffset(), myNewVarName + ".");
    context.commitDocument();
    final PsiFile file = context.getFile();
    ((PsiJavaFile)file).importClass(myQualifierClass);
    final PsiElement caretElement = ObjectUtils.notNull(file.findElementAt(context.getEditor().getCaretModel().getOffset()));

    PsiElement prevSibling = caretElement.getPrevSibling();
    final PsiStatement statement;
    if (prevSibling instanceof PsiStatement) {
      statement = (PsiStatement)prevSibling;
    } else {
      statement = PsiTreeUtil.getParentOfType(prevSibling, PsiStatement.class);
    }
    final Project project = context.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    context.commitDocument();
    final PsiStatement newVarDeclarationTemplate = elementFactory.createVariableDeclarationStatement(myNewVarName,
                                                                                                     elementFactory.createType(myQualifierClass),
                                                                                                     elementFactory.createExpressionFromText(PsiKeyword.NULL, null));

    PsiStatement newVarDeclaration = (PsiStatement)statement.getParent().addBefore(newVarDeclarationTemplate, statement);
    final PsiLiteralExpression nullKeyword = findNullElement(newVarDeclaration);
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

  private static PsiLiteralExpression findNullElement(final PsiElement psiElement) {
    final Collection<PsiLiteralExpression> literalExpressions = PsiTreeUtil.findChildrenOfType(psiElement, PsiLiteralExpression.class);
    for (final PsiLiteralExpression literalExpression : literalExpressions) {
      if (PsiKeyword.NULL.equals(literalExpression.getText())) {
        return literalExpression;
      }
    }
    throw new IllegalArgumentException();
  }
}
