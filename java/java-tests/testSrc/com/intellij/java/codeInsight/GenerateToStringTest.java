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
package com.intellij.java.codeInsight;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.java.generate.GenerateToStringWorker;
import org.jetbrains.java.generate.config.ReplacePolicy;
import org.jetbrains.java.generate.template.TemplateResource;

import java.util.Collections;

public class GenerateToStringTest extends LightCodeInsightTestCase {

  public void testAnnotationOnMethod() {
    doTest(new TemplateResource("a.java", "@NotNull() public String toString() {return null;}", false));
  }

  public void testJavadocOnMethod() {
    doTest(new TemplateResource("a.java", "/** my comment*/ public String toString() {return null;}", false));
  }

  private void doTest(final TemplateResource templateResource) {
    configureByFile("/codeInsight/generateToString/before" + getTestName(false) + ".java");

    final Editor editor = getEditor();
    final PsiElement elementAt = getFile().findElementAt(editor.getCaretModel().getOffset());
    final PsiClass clazz = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    assertNotNull(clazz);
    WriteAction.run(() -> {
      final GenerateToStringWorker worker = new GenerateToStringWorker(clazz, editor, false);
      worker.execute(Collections.emptyList(), templateResource, ReplacePolicy.getInstance());
    });
    checkResultByFile("/codeInsight/generateToString/after" + getTestName(false) + ".java");
  }
}
