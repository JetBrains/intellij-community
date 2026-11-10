// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.generation.ui;

import com.intellij.codeInsight.generation.ui.GenerateEqualsWizard;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GenerateEqualsWizardTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNotNullInit() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      import java.util.*;
      
      class Test {
        private final Collection<String> filters = new ArrayList<>();
        private Collection<String> filters2 = new ArrayList<>();
      }
      """);
    PsiClass aClass = ReadAction.computeBlocking(() -> ((PsiJavaFile)myFixture.getFile()).getClasses()[0]);
    EdtTestUtil.runInEdtAndWait(() -> {
      GenerateEqualsWizard wizard = new GenerateEqualsWizard(myFixture.getProject(), aClass, true, true);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      try {
        Set<Map.Entry<PsiMember, MemberInfo>> entries = wizard.getFieldsToNonNull();
        assertEquals(2, entries.size());
        for (Map.Entry<PsiMember, MemberInfo> entry : entries) {
          PsiMember member = entry.getKey();
          MemberInfo info = entry.getValue();
          assertEquals(member.getName().equals("filters"), info.isChecked());
        }
      }
      finally {
        wizard.close(0, true);
      }
    });
  }
}
