// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.ui;

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.Map;
import java.util.Set;

public class GenerateEqualsWizardTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNotNullInit() {
    myFixture.configureByText("Test.java", """
      import java.util.*;
      
      class Test {
        private final Collection<String> filters = new ArrayList<>();
        private Collection<String> filters2 = new ArrayList<>();
      }
      """);
    PsiClass aClass = ((PsiJavaFile)myFixture.getFile()).getClasses()[0];
    GenerateEqualsWizard wizard = new GenerateEqualsWizard(getProject(), aClass, true, true);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    try {
      Set<Map.Entry<PsiMember, MemberInfo>> entries = wizard.myFieldsToNonNull.entrySet();
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
  }
}
