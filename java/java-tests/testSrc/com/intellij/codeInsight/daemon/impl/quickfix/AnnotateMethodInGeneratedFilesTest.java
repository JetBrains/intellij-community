// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AnnotateMethodInGeneratedFilesTest extends LightJavaCodeInsightFixtureTestCase {
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
    GeneratedSourcesFilter.EP_NAME.getPoint().registerExtension(myGeneratedSourcesFilter, getTestRootDisposable());
  }

  public void testAnnotateOverriddenMethod() {
    doTest("Add missing nullability annotation");
  }

  public void testAnnotateOverriddenParameters() {
    doTest("Annotate overriding method parameters");
  }

  private void doTest(String quickFixName) {
    PsiClass generated =
      myFixture.addClass("public class GenMyTestClass implements MyTestClass {String implementMe(String arg) { return \"\"; } }");
    String generatedTextBefore = generated.getText();

    myFixture.configureByFile("before" + getTestName(false) + ".java");
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    IntentionAction action = ContainerUtil.find(intentions, i -> i.getText().startsWith(quickFixName));
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
    assertEquals(generatedTextBefore, generated.getText());
  }
}