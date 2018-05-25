// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class AnnotateMethodInGeneratedFilesTest extends LightCodeInsightFixtureTestCase {
  private final GeneratedSourcesFilter myGeneratedSourcesFilter = new GeneratedSourcesFilter() {
    @Override
    public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
      return file.getName().startsWith("Gen");
    }
  };

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() +
           "/codeInsight/daemonCodeAnalyzer/quickFix/annotateMethodInGeneratedFiles";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get("idea.report.nullity.missing.in.generated.overriders").setValue(false, getTestRootDisposable());
    myFixture.enableInspections(NullableStuffInspection.class);
    Extensions.getRootArea().getExtensionPoint(GeneratedSourcesFilter.EP_NAME).registerExtension(myGeneratedSourcesFilter);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Extensions.getRootArea().getExtensionPoint(GeneratedSourcesFilter.EP_NAME).unregisterExtension(myGeneratedSourcesFilter);
    }
    finally {
      super.tearDown();
    }
  }

  public void testAnnotateOverriddenMethod() {
    doTest("Annotate overridden methods");
  }

  public void testAnnotateOverriddenParameters() {
    doTest("Annotate overridden method parameters");
  }

  private void doTest(String quickFixName) {
    PsiClass generated =
      myFixture.addClass("public class GenMyTestClass implements MyTestClass {String implementMe(String arg) { return \"\"; } }");
    String generatedTextBefore = generated.getText();

    myFixture.configureByFile("before" + getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention(quickFixName));
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
    assertEquals(generatedTextBefore, generated.getText());
  }
}