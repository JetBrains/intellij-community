// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolService;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class JavaWebReferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testReferenceInComment() {
    List<? extends PsiSymbolReference> references = getReferences("// http://foo\n" +
                                                                  "class Hi {}");
    assertEquals(1, references.size());
  }

  @Test
  public void testReferenceInLiteral() {
    List<? extends PsiSymbolReference> references = getReferences("class Hi { String url=\"http://foo\"; }");
    assertEquals(1, references.size());
  }

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    getReferences("class Hi { String url=\"<info descr=\"Open in browser (" +
                  (SystemInfoRt.isMac ? "⌘Click, ⌘B" : "Ctrl+Click, Ctrl+B") + ")\">http://foo</info>\"; }");
    myFixture.testHighlighting(true, true, true);
  }

  @NotNull
  private List<? extends PsiSymbolReference> getReferences(String s) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, s);
    if (Registry.is("ide.symbol.url.references")) {
      return PlatformTestUtil.collectUrlReferences(file);
    }
    else {
      return ReadAction.computeBlocking(() -> ContainerUtil.map(PlatformTestUtil.collectWebReferences(file), PsiSymbolService.getInstance()::asSymbolReference));
    }
  }
}
