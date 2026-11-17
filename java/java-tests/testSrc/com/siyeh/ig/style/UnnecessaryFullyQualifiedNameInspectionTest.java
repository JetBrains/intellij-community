// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class UnnecessaryFullyQualifiedNameInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/ig");

  private static final String BASE_DIR = "com/siyeh/igtest/style/";

  @Test
  public void testFqnInJavadoc_Unnecessary_WhenFullyQualifyIfNotImported() {
    doTestWithFqnInJavadocSetting(BASE_DIR + "unnecessary_fqn_javadoc_fully_qualify_if_not_imported", JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED);
  }

  @Test
  public void testFqnInJavadoc_Unnecessary_WhenShortNamesAlways() {
    Project project = javaInspection.get().getFixture().getProject();
    final PsiClass aClass =
      ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(project).findClass("java.lang.Appendable", GlobalSearchScope.allScope(project)));
    assertNotNull(aClass); // test needs this class to be present
    doTestWithFqnInJavadocSetting(BASE_DIR + "unnecessary_fully_qualified_name/", JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT);
  }

  @Test
  public void testAcceptFqnInJavadoc() {
    doTestWithFqnInJavadocSetting(BASE_DIR + "unnecessary_fully_qualified_name_accept_in_javadoc/", JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS);
  }

  @Test
  public void testConflictWithTypeParameter() {
    javaInspection.get().doTest(BASE_DIR + "unnecessary_fqn_type_parameter_conflict", new UnnecessaryFullyQualifiedNameInspection());
  }

  @Test
  public void testSkipWarningIfThereIsSameNames() {
    javaInspection.get().doTest(BASE_DIR + "unnecessary_fqn_skip_warn_if_confusing", new UnnecessaryFullyQualifiedNameInspection());
  }

  private void doTestWithFqnInJavadocSetting(String dirPath, int classNamesInJavadoc) {
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(javaInspection.get().getFixture().getProject());

    javaSettings.CLASS_NAMES_IN_JAVADOC = classNamesInJavadoc;
    javaInspection.get().doTest(dirPath, new UnnecessaryFullyQualifiedNameInspection());
  }
}
