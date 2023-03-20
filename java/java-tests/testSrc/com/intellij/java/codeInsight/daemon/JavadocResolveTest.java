// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.navigation.NavigationRequest;
import com.intellij.navigation.impl.SourceNavigationRequest;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiSnippetAttribute;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.ExecutionException;

public class JavadocResolveTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/javaDoc/resolve";
  private final JavaDocReferenceInspection myJavaDocReferenceInspection = new JavaDocReferenceInspection();

  public void testSee0() { doTest(); }
  public void testSee1() { doTest(); }
  public void testSee2() { doTest(); }
  public void testSee3() { doTest(); }
  public void testPackageInfo() { doTest("/pkg/package-info.java"); }
  public void testBrokenPackageInfo() { doTest("/pkg1/package-info.java"); }
  public void testModuleInfo() { setLanguageLevel(LanguageLevel.JDK_1_9); doTest("/module-info.java"); }
  public void testOtherPackageLocal() {
    myJavaDocReferenceInspection.REPORT_INACCESSIBLE = false;
    doTest();
  }
  public void testSnippetRefs() throws ExecutionException, InterruptedException {
    doTest();
    PsiSnippetAttribute attribute = SyntaxTraverser.psiTraverser(myFile).filter(PsiSnippetAttribute.class)
      .filter(attr -> attr.getName().equals(PsiSnippetAttribute.REGION_ATTRIBUTE))
      .first();
    assertNotNull(attribute);
    PsiReference ref = attribute.getValue().getReference();
    assertNotNull(ref);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof Navigatable);
    assertEquals("@start region=reg", resolved.getText());
    assertEquals("region", LanguageFindUsages.getType(resolved));
    NavigationRequest request = ReadAction
      .nonBlocking(() -> ((Navigatable)resolved).navigationRequest()).submit(AppExecutorUtil.getAppExecutorService())
      .get();
    assertTrue(request instanceof SourceNavigationRequest);
    SourceNavigationRequest snr = (SourceNavigationRequest)request;
    VirtualFile file = snr.getFile();
    assertEquals(file.getName(), "Test.java");
    PsiFile snippetFile = PsiManager.getInstance(myProject).findFile(file);
    assertTrue(snippetFile.getText().startsWith("@start region=reg", snr.getOffset()));
  }

  private void doTest() {
    doTest("/pkg/" + getTestName(false) + ".java");
  }

  private void doTest(String testFileName) {
    enableInspectionTools(new JavadocDeclarationInspection(), myJavaDocReferenceInspection);
    try {
      doTest(BASE_PATH + testFileName, BASE_PATH, false, false);
    }
    catch (Exception e) { throw new RuntimeException(e); }
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }
}