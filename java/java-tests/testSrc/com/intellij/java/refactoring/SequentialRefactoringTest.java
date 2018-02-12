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
package com.intellij.java.refactoring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * Is assumed to contain tests that include more than one refactoring into the processing.
 * 
 * @author Denis Zhdanov
 * @since 1/12/12 2:35 PM
 */
public class SequentialRefactoringTest extends LightCodeInsightTestCase {

  public void testFormattingAfterInlineExtractMethod() throws PrepareFailedException {
    String text =
      "public class BrokenAlignment {\n" +
      "\n" +
      "    public Object test() {\n" +
      "        if (System.currentTimeMillis() > 1) {\n" +
      "            if (System.currentTimeMillis() > 2) {\n" +
      "                getData();\n" +
      "            }\n" +
      "        }\n" +
      "        return \"hey\";\n" +
      "    }\n" +
      "\n" +
      "    private void getData() {\n" +
      "        String[] args = new String[]{};\n" +
      "        String result = \"data: \";\n" +
      "        int i = 0;\n" +
      "        while (i < args.length) {\n" +
      "            result += args[i];\n" +
      "            if (i % 2 == 0) {\n" +
      "                result += \", it's even!\";\n" +
      "            } else {\n" +
      "                System.out.println(\"It's odd :(\");\n" +
      "                break;\n" +
      "            }\n" +
      "        }\n" +
      "        int k = 1;\n" +
      "    }\n" +
      "\n" +
      "}";
    configureFromFileText("test.java", text);
    
    // Perform inline.
    final PsiClass clazz = ((PsiClassOwner)myFile).getClasses()[0];
    final PsiMethod[] methods = clazz.findMethodsByName("getData", false);
    final PsiReferenceExpression ref = (PsiReferenceExpression)myFile.findReferenceAt(text.indexOf("getData") + 1);
    final InlineMethodProcessor processor = new InlineMethodProcessor(getProject(), methods[0], ref, myEditor, false);
    processor.run();
    
    // Perform extract.
    final String currentText = myEditor.getDocument().getText();
    int start = currentText.indexOf("String[] args");
    int end = currentText.indexOf("\n", currentText.indexOf("int k"));
    myEditor.getSelectionModel().setSelection(start, end);
    ExtractMethodTest.performExtractMethod(true, true, myEditor, myFile, getProject());
    
    checkResultByText(text.replace("getData", "newMethod"));
  }
}
