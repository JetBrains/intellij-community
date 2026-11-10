// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.inheritance.ImplicitSubclassProvider;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class NonFinalUtilityClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private final DedicatedClassNameImplicitSubclassProvider myImplicitSubclassProvider =
    new DedicatedClassNameImplicitSubclassProvider("ConcreteNoUtilityClass");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/classlayout/non_final_utility_class");
    ImplicitSubclassProvider.EP_NAME.getPoint().registerExtension(myImplicitSubclassProvider, myFixture.getTestRootDisposable());
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new NonFinalUtilityClassInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testNonFinalUtilityClass() {
    doTest();
  }

  private static class DedicatedClassNameImplicitSubclassProvider extends ImplicitSubclassProvider {
    private final String classNameToSubclass;

    DedicatedClassNameImplicitSubclassProvider(String classNameToSubclass) {
      this.classNameToSubclass = classNameToSubclass;
    }

    @Override
    public boolean isApplicableTo(@NotNull PsiClass psiClass) {
      return Objects.equals(psiClass.getName(), classNameToSubclass);
    }

    @Nullable
    @Override
    public SubclassingInfo getSubclassingInfo(@NotNull PsiClass psiClass) {
      return new SubclassingInfo(classNameToSubclass + " is subclassed at runtime");
    }
  }
}
