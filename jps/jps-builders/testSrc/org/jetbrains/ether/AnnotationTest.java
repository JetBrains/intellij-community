package org.jetbrains.ether;

/**
 * @author: db
 * Date: 26.07.11
 */
public class AnnotationTest extends IncrementalTestCase {
  public AnnotationTest() throws Exception {
    super("annotations");
  }

  public void testAddAnnotationTarget() throws Exception {
    doTest();
  }

  public void testAddAnnotationTypeMemberWithDefaultValue() throws Exception {
    doTest();
  }

  public void testAddAnnotationTypeMemberWithDefaultValue2() throws Exception {
    doTest();
  }

  public void testAddAnnotationTypeMemberWithoutDefaultValue() throws Exception {
    doTest();
  }

  public void testAddDefaultToAnnotationMember() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy1() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy2() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy3() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy4() throws Exception {
    doTest();
  }

  public void testChangeAnnotationTypeMemberType() throws Exception {
    doTest();
  }

  public void testClassAsArgument() throws Exception {
    doTest();
  }

  /*  Discussion is needed --- unnecessarily conservative
 public void testMetaAnnotationChanged() throws Exception {
     doTest();
 }

 public void testMetaAnnotationChangedCascade() throws Exception {
     doTest();
 }

 public void testMetaAnnotationChangedCascade2() throws Exception {
     doTest();
 } */

  public void testRemoveAnnotationTarget() throws Exception {
    doTest();
  }

  public void testRemoveAnnotationTypeMember() throws Exception {
    doTest();
  }

  public void testRemoveAnnotationTypeMember1() throws Exception {
    doTest();
  }

  public void testRemoveDefaultFromAnnotationMember() throws Exception {
    doTest();
  }

  public void testConservativeNonIncremental() throws Exception {
    doTest();
  }

  public void testConservativeNonIncremental1() throws Exception {
    doTest();
  }
}
