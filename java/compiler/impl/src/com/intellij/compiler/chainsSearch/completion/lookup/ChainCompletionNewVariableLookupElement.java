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
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.stream.Stream;

/**
 * @author Dmitry Batkovich
 */
public class ChainCompletionNewVariableLookupElement extends LookupElement {
  @NotNull
  private final PsiClass myQualifierClass;
  @NotNull
  private final String myNewVarName;

  public ChainCompletionNewVariableLookupElement(@NotNull final PsiClass qualifierClass) {
    Project project = qualifierClass.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    myNewVarName = Stream
      .of(codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, elementFactory.createType(qualifierClass)).names)
      .sorted(Comparator.comparing(String::length).reversed())
      .findFirst()
      .orElseThrow(IllegalStateException::new);
    myQualifierClass = qualifierClass;
  }

  @Override
  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
  }

  @Override
  public void handleInsert(final InsertionContext context) {
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

    statement.getParent().addBefore(newVarDeclarationTemplate, statement);
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myNewVarName;
  }
}
