/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class PsiModifierListTest extends LightCodeInsightFixtureTestCase {
  // todo[r.sh] drop this after transition period finished
  public void testDefaultModifier() throws Exception {
    final PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE,
                                             "class C {\n" +
                                             "    default void m() default {\n" +
                                             "    }\n" +
                                             "}");
    final PsiMethod method = ((PsiJavaFile)file).getClasses()[0].getMethods()[0];
    assertEquals("default void m() default {\n    }", method.getText());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, false);
        CodeStyleManager.getInstance(getProject()).reformat(method);
      }
    });
    assertEquals("void m() {\n    }", method.getText());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, true);
      }
    });
    assertEquals("default void m() {\n    }", method.getText());
  }
}
