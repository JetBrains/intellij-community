// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class AnnotateMethodInGeneratedFilesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private final GeneratedSourcesFilter myGeneratedSourcesFilter = new GeneratedSourcesFilter() {
    @Override
    public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
      return file.getName().startsWith("Gen");
    }
  };

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() +
           "/codeInsight/daemonCodeAnalyzer/quickFix/annotateMethodInGeneratedFiles");
    Registry.get("idea.report.nullity.missing.in.generated.overriders").setValue(false, myFixture.getTestRootDisposable());
    myFixture.enableInspections(NullableStuffInspection.class);
    GeneratedSourcesFilter.EP_NAME.getPoint().registerExtension(myGeneratedSourcesFilter, myFixture.getTestRootDisposable());
  }

  @Test
  public void testAnnotateOverriddenMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_6, myFixture.getTestRootDisposable());
    doTest("Add missing nullability annotations");
  }

  @Test
  public void testAnnotateOverriddenParameters() {
    doTest("Annotate overriding method parameters");
  }

  private void doTest(String quickFixName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass generated =
      myFixture.addClass("public class GenMyTestClass implements MyTestClass {String implementMe(String arg) { return \"\"; } }");
    String generatedTextBefore = ReadAction.computeBlocking(() -> generated.getText());

    myFixture.configureByFile("before" + myFixture.getTestName(false) + ".java");
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    IntentionAction action = ContainerUtil.find(intentions, i -> i.getText().startsWith(quickFixName));
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> assertEquals(generatedTextBefore, generated.getText()));
  }
}
