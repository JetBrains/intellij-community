// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.paths.WebReference;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaWebReferenceTest extends LightCodeInsightFixtureTestCase {

  public void testReferenceInComment() {
    List<WebReference> references = getReferences("// http://foo\n" +
                                                  "class Hi {}");
    assertEquals(1, references.size());
  }

  public void testReferenceInLiteral() {
    List<WebReference> references = getReferences("class Hi { String url=\"http://foo\"; }");
    assertEquals(1, references.size());
  }

  public void testHighlighting() {
    getReferences("class Hi { String url=\"<info descr=\"Open in browser (Ctrl+Click, Ctrl+B)\">http://foo</info>\"; }");
    myFixture.testHighlighting(true, true, true);
  }

  @NotNull
  private List<WebReference> getReferences(String s) {
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, s);
    return PlatformTestUtil.collectWebReferences(file);
  }
}
