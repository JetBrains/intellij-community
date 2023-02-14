// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

public class MagicConstantCompletion4Test extends LightJavaCodeInsightFixtureTestCase {
  @NeedsIndex.ForStandardLibrary
  public void test_suppress_class_constants_in_MagicConstant_presence() {
    FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, () -> {
      assertNull(myFixture.getJavaFacade().findClass(MagicConstant.class.getName(), GlobalSearchScope.allScope(myFixture.getProject())));
    });

    myFixture.configureByText("a.java",
                              "import java.util.*;\nclass Bar {\n  static void foo(Calendar c) {\n    c.setFirstDayOfWeek(<caret>)\n  }\n}\n");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "FRIDAY", "MONDAY");
    assertFalse(myFixture.getLookupElementStrings().contains("JANUARY"));
    assertFalse(myFixture.getLookupElementStrings().contains("Calendar.JANUARY"));
  }

  @Override
  public @NotNull DefaultLightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private static final DefaultLightProjectDescriptor projectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk14());
    }
  };
}
