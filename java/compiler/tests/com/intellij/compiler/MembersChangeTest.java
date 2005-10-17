/**
 * created at Jan 25, 2002
 * @author Jeka
 */
package com.intellij.compiler;

public class MembersChangeTest extends CompilerTestCase {
  public MembersChangeTest() {
    super("membersChange");
  }

  public void testAddMethodWithIncompatibleReturnType() throws Exception {doTest();}

  public void testAddMoreAccessibleMethodToBase() throws Exception {doTest();}

  public void testThrowsListDiffersInBaseAndDerived() throws Exception {doTest();}

  public void testAddStaticFieldToDerived() throws Exception {doTest();}

  public void testAddLessAccessibleFieldToDerived() throws Exception {doTest();}

  public void testAddFieldToDerived() throws Exception {doTest();}

  public void testAddFieldToBaseClass() throws Exception {doTest();}

  public void testAddFieldToInterface() throws Exception {doTest();}

  public void testAddFieldToInterface2() throws Exception {doTest();}

  public void testAddAbstractMethod() throws Exception  {doTest();}

  public void testAddConstructorParameter() throws Exception  {doTest();}

  public void testHierarchy() throws Exception {doTest();}

  public void testHierarchy2() throws Exception {doTest();}

  public void testRemoveBaseImplementation() throws Exception {doTest();}

  public void testRenameMethod() throws Exception {doTest();}

  public void testDeleteConstructor() throws Exception {doTest();}

  public void testDeleteMethod() throws Exception {doTest();}

  public void testDeleteMethodImplementation() throws Exception {doTest();}

  public void testDeleteMethodImplementation2() throws Exception {doTest();}

  public void testDeleteMethodImplementation3() throws Exception {doTest();}

  public void testDeleteMethodImplementation4() throws Exception {doTest();}

  public void testDeleteMethodImplementation5() throws Exception {doTest();}

  public void testDeleteMethodImplementation6() throws Exception {doTest();}

  public void testDeleteMethodImplementation7() throws Exception {doTest();}

  public void testAddMoreSpecific() throws Exception  {doTest();}

  public void testAddMoreSpecific1() throws Exception  {doTest();}

  public void testAddMoreSpecific2() throws Exception  {doTest();}

  public void testAddInterfaceMethod() throws Exception  {doTest();}

  public void testAddInterfaceMethod2() throws Exception  {doTest();}

  public void testDeleteInner() throws Exception  {doTest();}

  public void testChangeStaticMethodSignature() throws Exception  {doTest();}
}
