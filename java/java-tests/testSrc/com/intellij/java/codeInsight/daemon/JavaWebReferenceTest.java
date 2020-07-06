// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaWebReferenceTest extends LightJavaCodeInsightFixtureTestCase {

  public void testReferenceInComment() {
    List<? extends PsiSymbolReference> references = getReferences("// http://foo\n" +
                                                                  "class Hi {}");
    assertEquals(1, references.size());
  }

  public void testReferenceInLiteral() {
    List<? extends PsiSymbolReference> references = getReferences("class Hi { String url=\"http://foo\"; }");
    assertEquals(1, references.size());
  }

  public void testHighlighting() {
    getReferences("class Hi { String url=\"<info descr=\"Open in browser (Ctrl+Click, Ctrl+B)\">http://foo</info>\"; }");
    myFixture.testHighlighting(true, true, true);
  }

  @NotNull
  private List<? extends PsiSymbolReference> getReferences(String s) {
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, s);
    if (Registry.is("ide.symbol.url.references")) {
      return PlatformTestUtil.collectUrlReferences(file);
    }
    else {
      return PlatformTestUtil.collectWebReferences(file);
    }
  }
}
