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

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SuggestedReturnTypesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @NonNls private static final String BASE_PATH = "/refactoring/suggestedTypes/";

  @Test
  public void testOutputUsedAsObject() throws Exception {
    doTest("String", "Object", "Serializable", "Comparable<String>", "CharSequence");
  }

  @Test
  public void testOutputUsedAsCharSequence() throws Exception {
    doTest("String", "CharSequence");
  }

  @Test
  public void testFromExpression() throws Exception {
    doTest("String");
  }

  @Test
  public void testOutputUsedInPostfixExpr() throws Exception {
    doTest("Integer", "int");
  }

  @Test
  public void testOutputUsedInReturn() throws Exception {
    doTest("String");
  }

  private void doTest(String... types) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");

    myFixture.runInCommand(() -> {
      final Editor editor = myFixture.getEditor();
      final PsiFile file = myFixture.getFile();
      final Project project = myFixture.getProject();

      int startOffset = editor.getSelectionModel().getSelectionStart();
      int endOffset = editor.getSelectionModel().getSelectionEnd();
      PsiElement[] elements;
      PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
      if (expr != null) {
        elements = new PsiElement[]{expr};
      }
      else {
        elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      }
      assertTrue(elements.length > 0);

      final ExtractMethodProcessor processor =
        new ExtractMethodProcessor(project, editor, elements, null, "Extract Method", "newMethod", null);

      processor.prepare();

      final PsiExpression[] occurrences = processor.findOccurrences();
      final TypeSelectorManager manager = new TypeSelectorManagerImpl(project, processor.getReturnType(), occurrences, true);
      final JComponent component = manager.getTypeSelector().getComponent();
      if (types.length > 1) {
        assertInstanceOf(JComboBox.class, component, "One type suggested");
        final DefaultComboBoxModel model = (DefaultComboBoxModel)((JComboBox)component).getModel();
        assertEquals(types.length, model.getSize());
        for (int i = 0, typesLength = types.length; i < typesLength; i++) {
          String type = types[i];
          assertEquals(type, model.getElementAt(i).toString());
        }
      }
      else if (types.length == 1) {
        assertInstanceOf(JLabel.class, component, "Multiple types suggested");
        assertEquals(types[0], ((JLabel)component).getText());
      }
    });
  }
}