package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.IOException;

public class ConstantValuesTest extends PsiTestCase{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.ConstantValuesTest");

  private PsiClass myClass;

  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            String rootPath = PathManagerEx.getTestDataPath() + "/psi/constantValues";
            VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootPath, myFilesToDelete, true);

            ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
            ModifiableRootModel rootModel = rootManager.getModifiableModel();
            Library lib = rootModel.getModuleLibraryTable().createLibrary("test");
            Library.ModifiableModel libModel = lib.getModifiableModel();
            libModel.addRoot(root, OrderRootType.CLASSES);
            libModel.commit();
            rootModel.commit();
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );

    myClass = myJavaFacade.findClass("ClassWithConstants");
    assertNotNull(myClass);
  }

  protected void invokeTestRunnable(Runnable runnable) throws Exception {
    super.invokeTestRunnable(runnable);
    final PsiJavaFile file = (PsiJavaFile)myClass.getContainingFile();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try {
            file.getVirtualFile().setBinaryContent(file.getVirtualFile().contentsToByteArray());
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    );

    LOG.assertTrue(file.isValid());
    myClass = file.getClasses()[0];

    LOG.assertTrue(myClass.isValid());
    super.invokeTestRunnable(runnable);
  }

  public void testInt1(){
    PsiField field = myClass.findFieldByName("INT_CONST1", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    assertEquals(new Integer(1), initializer.getValue());
    assertEquals("1", initializer.getText());

    assertEquals(new Integer(1), field.computeConstantValue());
  }

  public void testInt2(){
    PsiField field = myClass.findFieldByName("INT_CONST2", false);
    assertNotNull(field);
    PsiPrefixExpression initializer = (PsiPrefixExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    PsiLiteralExpression operand = (PsiLiteralExpression)initializer.getOperand();
    assertEquals(new Integer(1), operand.getValue());
    assertEquals("-1", initializer.getText());

    assertEquals(new Integer(-1), field.computeConstantValue());
  }

  public void testInt3(){
    PsiField field = myClass.findFieldByName("INT_CONST3", false);
    assertNotNull(field);
    PsiPrefixExpression initializer = (PsiPrefixExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    int value = -1 << 31;
    assertEquals(Integer.toString(value), initializer.getText());

    assertEquals(new Integer(value), field.computeConstantValue());
  }

  public void testLong1(){
    PsiField field = myClass.findFieldByName("LONG_CONST1", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    assertEquals(new Integer(2), initializer.getValue());
    assertEquals("2", initializer.getText());

    assertEquals(new Long(2), field.computeConstantValue());
  }

  public void testLong2(){
    PsiField field = myClass.findFieldByName("LONG_CONST2", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.LONG, initializer.getType());
    assertEquals(new Long(1000000000000L), initializer.getValue());
    assertEquals("1000000000000L", initializer.getText());

    assertEquals(new Long(1000000000000L), field.computeConstantValue());
  }

  public void testLong3(){
    PsiField field = myClass.findFieldByName("LONG_CONST3", false);
    assertNotNull(field);
    PsiPrefixExpression initializer = (PsiPrefixExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.LONG, initializer.getType());
    long value = -1L << 63;
    assertEquals(value + "L", initializer.getText());

    assertEquals(new Long(value), field.computeConstantValue());
  }

  public void testShort(){
    PsiField field = myClass.findFieldByName("SHORT_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    assertEquals(new Integer(3), initializer.getValue());
    assertEquals("3", initializer.getText());

    assertEquals(new Short((short) 3), field.computeConstantValue());
  }

  public void testByte(){
    PsiField field = myClass.findFieldByName("BYTE_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    assertEquals(new Integer(4), initializer.getValue());
    assertEquals("4", initializer.getText());

    assertEquals(new Byte((byte) 4), field.computeConstantValue());
  }

  public void testChar(){
    PsiField field = myClass.findFieldByName("CHAR_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.CHAR, initializer.getType());
    assertEquals(new Character('5'), initializer.getValue());
    assertEquals("'5'", initializer.getText());

    assertEquals(new Character('5'), field.computeConstantValue());
  }

  public void testBoolean(){
    PsiField field = myClass.findFieldByName("BOOL_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.BOOLEAN, initializer.getType());
    assertEquals(Boolean.TRUE, initializer.getValue());
    assertEquals("true", initializer.getText());

    assertEquals(Boolean.TRUE, field.computeConstantValue());
  }

  public void testFloat(){
    PsiField field = myClass.findFieldByName("FLOAT_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.FLOAT, initializer.getType());
    assertEquals(new Float(1.234f), initializer.getValue());
    assertEquals("1.234f", initializer.getText());

    assertEquals(new Float(1.234f), field.computeConstantValue());
  }

  public void testDouble(){
    PsiField field = myClass.findFieldByName("DOUBLE_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.DOUBLE, initializer.getType());
    assertEquals(new Double(3.456), initializer.getValue());
    assertEquals("3.456", initializer.getText());

    assertEquals(new Double(3.456), field.computeConstantValue());
  }

  public void testString(){
    PsiField field = myClass.findFieldByName("STRING_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertTrue(initializer.getType().equalsToText("java.lang.String"));
    assertEquals("a\r\n\"bcd", initializer.getValue());
    assertEquals("\"a\\r\\n\\\"bcd\"", initializer.getText());

    assertEquals("a\r\n\"bcd", field.computeConstantValue());
  }

  public void testInfinity(){
    PsiField field1 = myClass.findFieldByName("d1", false);
    assertNotNull(field1);
    PsiReferenceExpression initializer1 = (PsiReferenceExpression)field1.getInitializer();
    assertNotNull(initializer1);
    assertEquals(PsiType.DOUBLE, initializer1.getType());
    assertEquals("Double.POSITIVE_INFINITY", initializer1.getText());
    assertEquals(new Double(Double.POSITIVE_INFINITY), field1.computeConstantValue());

    PsiField field2 = myClass.findFieldByName("d2", false);
    assertNotNull(field2);
    PsiReferenceExpression initializer2 = (PsiReferenceExpression)field2.getInitializer();
    assertNotNull(initializer2);
    assertEquals(PsiType.DOUBLE, initializer2.getType());
    assertEquals("Double.NEGATIVE_INFINITY", initializer2.getText());
    assertEquals(new Double(Double.NEGATIVE_INFINITY), field2.computeConstantValue());

    PsiField field3 = myClass.findFieldByName("d3", false);
    assertNotNull(field3);
    PsiReferenceExpression initializer3 = (PsiReferenceExpression)field3.getInitializer();
    assertNotNull(initializer3);
    assertEquals(PsiType.DOUBLE, initializer3.getType());
    assertEquals("Double.NaN", initializer3.getText());
    assertEquals(new Double(Double.NaN), field3.computeConstantValue());
  }
}
