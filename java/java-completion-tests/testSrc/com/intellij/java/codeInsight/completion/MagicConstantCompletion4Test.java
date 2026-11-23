// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import org.intellij.lang.annotations.MagicConstant;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
@IndexingModeTemplate
public class MagicConstantCompletion4Test {
  private static final DefaultLightProjectDescriptor projectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk14());
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(projectDescriptor);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_suppress_class_constants_in_MagicConstant_presence() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, () -> {
        assertNull(myFixture.getJavaFacade().findClass(MagicConstant.class.getName(), GlobalSearchScope.allScope(myFixture.getProject())));
      });

      myFixture.configureByText("a.java",
                                "import java.util.*;\nclass Bar {\n  static void foo(Calendar c) {\n    c.setFirstDayOfWeek(<caret>)\n  }\n}\n");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "FRIDAY", "MONDAY");
      assertFalse(myFixture.getLookupElementStrings().contains("JANUARY"));
      assertFalse(myFixture.getLookupElementStrings().contains("Calendar.JANUARY"));
    });
  }
}
