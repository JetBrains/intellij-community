/*
 * User: anna
 * Date: 19-Aug-2009
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class TypeMigrationByAtomicRuleTest extends TypeMigrationTestBase{
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/typeMigrationByAtomic/";
  }

  private void doTestDirectMigration() throws Exception {
    doTestFieldType("i", myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicInteger", null));
  }


  public void testDirectIncrementDecrement() throws Exception {
    doTestDirectMigration();
  }

  public void testDirectAssignments() throws Exception {
    doTestDirectMigration();
  }

  public void testDirectConditions() throws Exception {
    doTestFieldType("b", myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicBoolean", null));
  }

  public void testDirectConditionalExpression() throws Exception {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null));
  }


  public void testDirectByte() throws Exception {
    doTestFieldType("b",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.Byte>", null));
  }

  public void testDirectString() throws Exception {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.lang.String>", null));
  }

  public void testDirectForeach() throws Exception {
    doTestFieldType("lst",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.concurrent.atomic.AtomicReference<java.util.List<java.lang.String>>", null));
  }

  public void testDirectStringArray() throws Exception {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText(AtomicReferenceArray.class.getName() + "<java.lang.String>", null));
  }

  public void testDirectIntArray() throws Exception {
    doTestFieldType("a",
                    myJavaFacade.getElementFactory().createTypeFromText(AtomicIntegerArray.class.getName(), null));
  }

  private void doTestReverseMigration() throws Exception {
    doTestFieldType("i", PsiType.INT);
  }


  public void testReverseIncrementDecrement() throws Exception {
    doTestReverseMigration();
  }

  public void testReverseAssignments() throws Exception {
    doTestReverseMigration();
  }

  public void testReverseConditions() throws Exception {
    doTestFieldType("b", PsiType.BOOLEAN);
  }

  public void testReverseByte() throws Exception {
    doTestFieldType("b", PsiType.BYTE);
  }

   public void testReverseString() throws Exception {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

   public void testReverseStringArray() throws Exception {
    doTestFieldType("s",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null).createArrayType());
  }

  public void testReverseIntArray() throws Exception {
    doTestFieldType("a",
                    PsiType.INT.createArrayType());
  }
}