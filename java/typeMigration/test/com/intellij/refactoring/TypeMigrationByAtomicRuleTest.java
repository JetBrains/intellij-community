package com.intellij.refactoring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiTypes;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestFieldType;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class TypeMigrationByAtomicRuleTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_6);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, TypeMigrationTestUtil.getTestDataPath() + "/refactoring/typeMigrationByAtomic/");

  private void doTestDirectMigration() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "i", ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.concurrent.atomic.AtomicInteger", null)));
  }

  @Test
  public void testDirectIncrementDecrement() {
    doTestDirectMigration();
  }

  @Test
  public void testDirectAssignments() {
    doTestDirectMigration();
  }

  @Test
  public void testDirectConditions() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "b", ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.concurrent.atomic.AtomicBoolean", null)));
  }

  @Test
  public void testDirectConditionalExpression() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "s",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null)));
  }

  @Test
  public void testDirectByte() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "b",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.Byte>", null)));
  }

  @Test
  public void testDirectString() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "s",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null)));
  }

  @Test
  public void testDirectForeach() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "lst",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.util.List<java.lang.String>>", null)));
  }

  @Test
  public void testDirectStringArray() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "s",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(AtomicReferenceArray.class.getName() + "<java.lang.String>", null)));
  }

  @Test
  public void testDirectIntArray() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "a",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(AtomicIntegerArray.class.getName(), null)));
  }

  private void doTestReverseMigration() {
    doTestFieldType(multiFile.get(), "i", PsiTypes.intType());
  }

  @Test
  public void testReverseIncrementDecrement() {
    doTestReverseMigration();
  }

  @Test
  public void testReverseAssignments() {
    doTestReverseMigration();
  }

  @Test
  public void testReverseConditions() {
    doTestFieldType(multiFile.get(), "b", PsiTypes.booleanType());
  }

  @Test
  public void testReverseByte() {
    doTestFieldType(multiFile.get(), "b", PsiTypes.byteType());
  }

   @Test
   public void testReverseString() {
     LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "s",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null)));
  }

   @Test
   public void testReverseStringArray() {
     LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "s",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null).createArrayType()));
  }

  @Test
  public void testReverseIntArray() {
    doTestFieldType(multiFile.get(), "a",
                    PsiTypes.intType().createArrayType());
  }

  @Test
  public void testChainedInitialization() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "a", ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.concurrent.atomic.AtomicInteger", null)));
  }

  @Test
  public void testLiteralMigration() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "a", ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.concurrent.atomic.AtomicLong", null)));
  }
  
  @Test
  public void testFieldDeclaration() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "a", ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null)));
  }
}