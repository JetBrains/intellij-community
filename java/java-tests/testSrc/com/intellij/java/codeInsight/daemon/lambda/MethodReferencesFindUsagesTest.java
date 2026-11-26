// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class MethodReferencesFindUsagesTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/methodRef/findUsages/";
  
  @Test
  public void testConstructorUsages() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final String testName = myFixture.getTestName(false);
    daemonFixture.get().configureByFile(BASE_PATH + testName + ".java");

    final PsiClass aClass = ReadAction.computeBlocking(() -> myFixture.getJavaFacade().findClass(testName));
    assertNotNull(aClass);
    final PsiMethod[] constructors = ReadAction.computeBlocking(() -> aClass.getConstructors());
    assertEquals(1, constructors.length);
    Collection<PsiReference> references = ReadAction.computeBlocking(() -> MethodReferencesSearch.search(constructors[0]).findAll());
    assertEquals(1, references.size());
  }
}
