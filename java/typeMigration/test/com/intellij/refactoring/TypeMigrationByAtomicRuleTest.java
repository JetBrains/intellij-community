package com.intellij.refactoring;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class TypeMigrationByAtomicRuleTest extends TypeMigrationTestBase{
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/typeMigrationByAtomic/";
  }

  private void doTestDirectMigration() {
    doTestFieldType("i", myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicInteger", null));
  }


  public void testDirectIncrementDecrement() {
    doTestDirectMigration();
  }

  public void testDirectAssignments() {
    doTestDirectMigration();
  }

  public void testDirectConditions() {
    doTestFieldType("b", myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicBoolean", null));
  }

  public void testDirectConditionalExpression() {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null));
  }


  public void testDirectByte() {
    doTestFieldType("b",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.Byte>", null));
  }

  public void testDirectString() {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null));
  }

  public void testDirectForeach() {
    doTestFieldType("lst",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.util.List<java.lang.String>>", null));
  }

  public void testDirectStringArray() {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText(AtomicReferenceArray.class.getName() + "<java.lang.String>", null));
  }

  public void testDirectIntArray() {
    doTestFieldType("a",
                    myJavaFacade.getElementFactory().createTypeFromText(AtomicIntegerArray.class.getName(), null));
  }

  private void doTestReverseMigration() {
    doTestFieldType("i", PsiType.INT);
  }


  public void testReverseIncrementDecrement() {
    doTestReverseMigration();
  }

  public void testReverseAssignments() {
    doTestReverseMigration();
  }

  public void testReverseConditions() {
    doTestFieldType("b", PsiType.BOOLEAN);
  }

  public void testReverseByte() {
    doTestFieldType("b", PsiType.BYTE);
  }

   public void testReverseString() {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

   public void testReverseStringArray() {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testReverseIntArray() {
    doTestFieldType("a",
                    PsiType.INT.createArrayType());
  }

  public void testChainedInitialization() {
    doTestFieldType("a", myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicInteger", null));
  }

  public void testLiteralMigration() {
    doTestFieldType("a", myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicLong", null));
  }
}