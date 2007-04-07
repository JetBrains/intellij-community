/**
 * created at Jan 28, 2002
 * @author Jeka
 */
package com.intellij.compiler;

public class GenericsTest extends Jdk15CompilerTestCase {

  public GenericsTest() {
    super("generics");
  }

  public void testReturnType() throws Exception {doTest();}
  
  public void testFieldTypeChange() throws Exception {doTest();}

  public void testChangeExtends() throws Exception {doTest();}

  public void testChangeExtends1() throws Exception {doTest();}

  public void testChangeExtends2() throws Exception {doTest();}

  public void testChangeImplements() throws Exception {doTest();}

  public void testCovariance() throws Exception {doTest();}

  public void testCovariance1() throws Exception {doTest();}

  public void testCovariance2() throws Exception {doTest();}

  public void testCovarianceNoChanges() throws Exception {doTest();}

  public void testChangeToCovariantMethodInBase() throws Exception {doTest();}
  
  public void testChangeToCovariantMethodInBase2() throws Exception {doTest();}
  
  public void testChangeToCovariantMethodInBase3() throws Exception {doTest();}

  public void testAddParameterizedMethodToBase() throws Exception {doTest();}

  public void testAddMethodToBase() throws Exception {doTest();}

  public void testChangeInterfaceTypeParameter() throws Exception {doTest();}

  public void testChangeBound() throws Exception {doTest();}

  public void testChangeBound1() throws Exception {doTest();}

  public void testChangeBoundedClass() throws Exception {doTest();}

  public void testChangeBoundClass1() throws Exception {doTest();}

  // commented because of Sun's bug
  //public void testChangeBoundInterface1() throws Exception {doTest();}

  public void testDegenerify() throws Exception {doTest();}

  public void testDegenerify1() throws Exception {doTest();}
}
