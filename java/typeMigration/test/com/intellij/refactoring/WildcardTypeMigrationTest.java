/*
 * User: anna
 * Date: 19-Aug-2009
 */
package com.intellij.refactoring;

import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

public class WildcardTypeMigrationTest extends TypeMigrationTestBase{
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/wildcard/";
  }

  public void testProducerExtends() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? extends java.lang.Number>", null));
  }

  public void testProducerSuper() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? super java.lang.Integer>", null));
  }

  public void testProducerUnbounded() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));
  }

  public void testProducerCollectionChanged() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? super java.lang.Integer>", null));
  }

  public void testProducerExtendsCollectionChanged() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends java.lang.Object>", null));
  }

  public void testProducerStopAtWildcard() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.List<? super java.lang.Number>", null));
  }

  public void testProducerFailToStopAtWildcard() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.List<? super java.lang.Integer>", null));
  }

  public void testProducerExtendsFailToStopAtWildcard() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.List<? extends java.lang.Number>", null));
  }


  public void testConsumerExtends() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? extends java.lang.Number>", null));
  }

  public void testConsumerSuper() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? super java.lang.Number>", null));
  }

  public void testConsumerUnbounded() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));
  }

  // array -> list
  public void testAssignmentExtends() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? extends java.lang.Integer>", null));
  }

  public void testAssignmentSuper() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? super java.lang.Integer>", null));
  }

  public void testAssignmentUnbounded() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));
  }

  public void testGetExtends() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? extends java.lang.Integer>", null));
  }

  public void testGetSuper() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? super java.lang.Integer>", null));
  }

  public void testGetUnbounded() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));

  }

  public void testLengthSize() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));

  }

  //list -> array
  public void testGetAssignmentExtendsToType() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Number", null).createArrayType());
  }

  public void testGetAssignmentExtendsToSuperType() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null).createArrayType());
  }

  public void testGetAssignmentExtendsToChildType() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null).createArrayType());
  }

  // -> threadlocal with wildcard
  public void testThreadLocalProducerExtends() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.util.List<? extends String>>", null));
  }

  //List<? super String> is not assignable to List<String> though it is possible to pass string where ? super String was   
  public void _testThreadLocalProducerSuper() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.util.List<? super String>>", null));
  }

  public void testThreadLocalConsumerSuper() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<? super String>", null));
  }

  public void testThreadLocalConsumerExtends() throws Exception {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<? extends String>", null));
  }
}