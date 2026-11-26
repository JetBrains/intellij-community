// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

/**
 * Is assumed to contain tests that include more than one refactoring into the processing.
 */
@TestApplication
public class SequentialRefactoringTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testFormattingAfterInlineExtractMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    myFixture.configureByText("test.java", text);

    // Perform inline.
    myFixture.runInCommand(() -> {
      final PsiClass clazz = ((PsiClassOwner)myFixture.getFile()).getClasses()[0];
      final PsiMethod[] methods = clazz.findMethodsByName("getData", false);
      final PsiReferenceExpression ref = (PsiReferenceExpression)myFixture.getFile().findReferenceAt(text.indexOf("getData") + 1);
      final InlineMethodProcessor processor = new InlineMethodProcessor(myFixture.getProject(), methods[0], ref, myFixture.getEditor(), false);
      processor.run();

      // Perform extract.
      final String currentText = myFixture.getEditor().getDocument().getText();
      int start = currentText.indexOf("String[] args");
      int end = currentText.indexOf("\n", currentText.indexOf("int k"));
      myFixture.getEditor().getSelectionModel().setSelection(start, end);
      ExtractMethodTest.performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject());

      myFixture.checkResult(text.replace("getData", "newMethod"));
    });
  }
}
