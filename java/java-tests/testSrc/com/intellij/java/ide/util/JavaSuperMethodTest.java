// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.util;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaSuperMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static String getBasePath() {
    return "/codeInsight/gotosuper/";
  }

  @Test
  public void testDoNotGoToSiblingInheritanceIfInLibrary() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "OverridingLibrary.java");

    ReadAction.runBlocking(() -> {
      PsiClass aThread = myFixture.getJavaFacade().findClass("java.lang.Thread");
      PsiMethod startMethod = aThread.findMethodsByName("start", false)[0];
      PsiMethod sibling = FindSuperElementsHelper.getSiblingInheritedViaSubClass(startMethod);
      assertNotNull(sibling);

      Collection<PsiMethod> superMethods = SuperMethodWarningUtil.getSuperMethods(startMethod, aThread, Collections.emptyList());
      assertTrue(superMethods.isEmpty(), superMethods.toString());
    });
  }
}
