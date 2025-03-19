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
package com.intellij.java.psi;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractReparseTestCase;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

public class JavaReparsePerformanceTest extends AbstractReparseTestCase {
  public void testChangingVeryDeepTreePerformance() {
    String call1 = "a('b').";
    String call2 = "c(new Some()).";
    String suffix = "x(); } }";
    PsiFile file = myFixture.addFileToProject("a.java", "class Foo { { u." + StringUtil.repeat(call1 + call2, 500) + suffix);

    PsiDocumentManager pdm = PsiDocumentManager.getInstance(getProject());
    Document document = pdm.getDocument(file);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      Benchmark.newBenchmark("deep reparse", () -> {
        document.insertString(document.getTextLength() - suffix.length(), call1);
        pdm.commitDocument(document);

        document.insertString(document.getTextLength() - suffix.length(), call2);
        pdm.commitDocument(document);

        document.insertString(document.getTextLength() - suffix.length(), "\n");
        pdm.commitDocument(document);
      }).start();

      PsiTestUtil.checkFileStructure(file);
    });
  }
}
