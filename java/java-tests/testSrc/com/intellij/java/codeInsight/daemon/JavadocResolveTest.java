// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.navigation.NavigationRequest;
import com.intellij.platform.backend.navigation.NavigationTarget;
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.javadoc.PsiSnippetAttribute;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

import static com.intellij.java.codeInsight.daemon.JdkDaemonAnalyzerFixture.jdkDaemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavadocResolveTest {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/javaDoc/resolve";
  private final JavaDocReferenceInspection myJavaDocReferenceInspection = new JavaDocReferenceInspection();

  private final TestFixture<JdkDaemonAnalyzerFixture> daemonFixture =
    jdkDaemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath(), () -> IdeaTestUtil.getMockJdk18());

  @Test public void testSee0() { doTest(); }
  @Test public void testSee1() { doTest(); }
  @Test public void testSee2() { doTest(); }
  @Test public void testSee3() { doTest(); }
  @Test public void testPackageInfo() { doTest("/pkg/package-info.java"); }
  @Test public void testBrokenPackageInfo() { doTest("/pkg1/package-info.java"); }
  @Test public void testModuleInfo() {
    IdeaTestUtil.setProjectLanguageLevel(daemonFixture.get().getProject(), LanguageLevel.JDK_1_9);
    doTest("/module-info.java");
  }
  @Test public void testOtherPackageLocal() {
    myJavaDocReferenceInspection.REPORT_INACCESSIBLE = false;
    doTest();
  }
  @Test public void testSnippetRefs() throws ExecutionException, InterruptedException {
    doTest();
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    PsiFile myFile = fixture.getFile();
    PsiSymbolReference ref = ReadAction.computeBlocking(() -> {
      PsiSnippetAttribute attribute = SyntaxTraverser.psiTraverser(myFile).filter(PsiSnippetAttribute.class)
        .filter(attr -> attr.getName().equals(PsiSnippetAttribute.REGION_ATTRIBUTE))
        .first();
      assertNotNull(attribute);
      Collection<? extends @NotNull PsiSymbolReference> refs = attribute.getValue().getOwnReferences();
      assertEquals(1, refs.size());
      return refs.iterator().next();
    });
    NavigationTarget target = ReadAction.computeBlocking(() -> {
      Collection<? extends Symbol> symbols = ref.resolveReference();
      assertEquals(1, symbols.size());
      Symbol symbol = symbols.iterator().next();
      return assertInstanceOf(NavigationTarget.class, symbol);
    });
    TargetPresentation presentation = ReadAction.computeBlocking(() -> target.computePresentation());
    assertEquals("@start region=reg", presentation.getPresentableText());
    assertEquals("Test.java", presentation.getLocationText());
    NavigationRequest request = ReadAction
      .nonBlocking(() -> target.navigationRequest()).submit(AppExecutorUtil.getAppExecutorService())
      .get();
    SourceNavigationRequest snr = assertInstanceOf(SourceNavigationRequest.class, request);
    VirtualFile file = snr.getFile();
    assertEquals("Test.java", file.getName());
    ReadAction.runBlocking(() -> {
      PsiFile snippetFile = PsiManager.getInstance(fixture.getProject()).findFile(file);
      assertTrue(snippetFile.getText().startsWith("@start region=reg", snr.getOffsetMarker().getStartOffset()));
    });
  }

  private void doTest() {
    doTest("/pkg/" + daemonFixture.get().getTestName(false) + ".java");
  }

  private void doTest(String testFileName) {
    JdkDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.enableInspectionTools(new JavadocDeclarationInspection(), myJavaDocReferenceInspection);
    fixture.doTest(BASE_PATH + testFileName, BASE_PATH, false, false);
  }
}
