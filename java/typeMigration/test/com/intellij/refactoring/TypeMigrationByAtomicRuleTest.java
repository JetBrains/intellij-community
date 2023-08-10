package com.intellij.refactoring;

import com.intellij.psi.PsiTypes;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class TypeMigrationByAtomicRuleTest extends TypeMigrationTestBase{
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/typeMigrationByAtomic/";
  }

  private void doTestDirectMigration() {
    doTestFieldType("i", getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicInteger", null));
  }


  public void testDirectIncrementDecrement() {
    doTestDirectMigration();
  }

  public void testDirectAssignments() {
    doTestDirectMigration();
  }

  public void testDirectConditions() {
    doTestFieldType("b", getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicBoolean", null));
  }

  public void testDirectConditionalExpression() {
    doTestFieldType("s",
                    getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null));
  }


  public void testDirectByte() {
    doTestFieldType("b",
                    getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.Byte>", null));
  }

  public void testDirectString() {
    doTestFieldType("s",
                    getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null));
  }

  public void testDirectForeach() {
    doTestFieldType("lst",
                    getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.util.List<java.lang.String>>", null));
  }

  public void testDirectStringArray() {
    doTestFieldType("s",
                    getElementFactory().createTypeFromText(AtomicReferenceArray.class.getName() + "<java.lang.String>", null));
  }

  public void testDirectIntArray() {
    doTestFieldType("a",
                    getElementFactory().createTypeFromText(AtomicIntegerArray.class.getName(), null));
  }

  private void doTestReverseMigration() {
    doTestFieldType("i", PsiTypes.intType());
  }


  public void testReverseIncrementDecrement() {
    doTestReverseMigration();
  }

  public void testReverseAssignments() {
    doTestReverseMigration();
  }

  public void testReverseConditions() {
    doTestFieldType("b", PsiTypes.booleanType());
  }

  public void testReverseByte() {
    doTestFieldType("b", PsiTypes.byteType());
  }

   public void testReverseString() {
    doTestFieldType("s",
                    getElementFactory().createTypeFromText("java.lang.String", null));
  }

   public void testReverseStringArray() {
    doTestFieldType("s",
                    getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testReverseIntArray() {
    doTestFieldType("a",
                    PsiTypes.intType().createArrayType());
  }

  public void testChainedInitialization() {
    doTestFieldType("a", getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicInteger", null));
  }

  public void testLiteralMigration() {
    doTestFieldType("a", getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicLong", null));
  }
  
  public void testFieldDeclaration() {
    doTestFieldType("a", getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null));
  }
}