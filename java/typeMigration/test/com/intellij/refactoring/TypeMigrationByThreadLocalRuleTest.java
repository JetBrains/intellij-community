package com.intellij.refactoring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestFieldType;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class TypeMigrationByThreadLocalRuleTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_6);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, TypeMigrationTestUtil.getTestDataPath() + "/refactoring/typeMigrationByThreadLocal/");

  @Test
  public void testDirectInt() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "i", ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.ThreadLocal<java.lang.Integer>", null)));
  }
  
  @Test
  public void testDirectByte() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "i", ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.ThreadLocal<java.lang.Byte>", null)));
  }

  @Test
  public void testDirectString() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myS", ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.ThreadLocal<java.lang.String>", null)));
  }

  @Test
  public void testLanguageLevel() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_3, () -> doTestFieldType(multiFile.get(), "i", ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.ThreadLocal", null))));
  }
}