/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.util.PsiTreeUtil;

public class JavaPsiFormattingTest extends AbstractJavaFormatterTest {
  
  public void testReferenceExpressionSpaceInsertion() {
    String text = "if(x&y);";
    PsiFile file = createFile("A.java", "class C{{\n" + text + "\n}}");
    PsiElement element = file.findElementAt(file.getText().indexOf(text));
    PsiIfStatement statement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    assertNotNull(statement);

    Ref<PsiElement> result = Ref.create();
    CommandProcessor.getInstance().executeCommand(getProject(), () -> WriteAction.run(
      () -> result.set(CodeStyleManager.getInstance(getProject()).reformat(statement))
    ), "", null);

    PsiExpression expr = ((PsiIfStatement)result.get()).getCondition();
    assertEquals("PsiBinaryExpression:x & y\n" +
                 "  PsiReferenceExpression:x\n" +
                 "    PsiReferenceParameterList\n" +
                 "      <empty list>\n" +
                 "    PsiIdentifier:x('x')\n" +
                 "  PsiWhiteSpace(' ')\n" +
                 "  PsiJavaToken:AND('&')\n" +
                 "  PsiWhiteSpace(' ')\n" +
                 "  PsiReferenceExpression:y\n" +
                 "    PsiReferenceParameterList\n" +
                 "      <empty list>\n" +
                 "    PsiIdentifier:y('y')\n",
                 DebugUtil.psiToString(expr, false));
  }
  
}
