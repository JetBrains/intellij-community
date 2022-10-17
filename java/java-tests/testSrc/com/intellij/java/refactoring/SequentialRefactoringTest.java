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
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

/**
 * Is assumed to contain tests that include more than one refactoring into the processing.
 * 
 * @author Denis Zhdanov
 */
public class SequentialRefactoringTest extends LightJavaCodeInsightTestCase {

  public void testFormattingAfterInlineExtractMethod() throws PrepareFailedException {
    String text =
      """
        public class BrokenAlignment {

            public Object test() {
                if (System.currentTimeMillis() > 1) {
                    if (System.currentTimeMillis() > 2) {
                        getData();
                    }
                }
                return "hey";
            }

            private void getData() {
                String[] args = new String[]{};
                String result = "data: ";
                int i = 0;
                while (i < args.length) {
                    result += args[i];
                    if (i % 2 == 0) {
                        result += ", it's even!";
                    } else {
                        System.out.println("It's odd :(");
                        break;
                    }
                }
                int k = 1;
            }

        }""";
    configureFromFileText("test.java", text);
    
    // Perform inline.
    final PsiClass clazz = ((PsiClassOwner)getFile()).getClasses()[0];
    final PsiMethod[] methods = clazz.findMethodsByName("getData", false);
    final PsiReferenceExpression ref = (PsiReferenceExpression)getFile().findReferenceAt(text.indexOf("getData") + 1);
    final InlineMethodProcessor processor = new InlineMethodProcessor(getProject(), methods[0], ref, getEditor(), false);
    processor.run();
    
    // Perform extract.
    final String currentText = getEditor().getDocument().getText();
    int start = currentText.indexOf("String[] args");
    int end = currentText.indexOf("\n", currentText.indexOf("int k"));
    getEditor().getSelectionModel().setSelection(start, end);
    ExtractMethodTest.performExtractMethod(true, true, getEditor(), getFile(), getProject());
    
    checkResultByText(text.replace("getData", "newMethod"));
  }
}
