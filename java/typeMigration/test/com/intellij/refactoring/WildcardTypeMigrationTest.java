package com.intellij.refactoring;

import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

public class WildcardTypeMigrationTest extends TypeMigrationTestBase{
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/wildcard/";
  }

  public void testProducerExtends() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? extends java.lang.Number>", null));
  }

  public void testProducerSuper() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? super java.lang.Integer>", null));
  }

  public void testProducerUnbounded() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));
  }

  public void testProducerCollectionChanged() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? super java.lang.Integer>", null));
  }

  public void testProducerExtendsCollectionChanged() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends java.lang.Object>", null));
  }

  public void testProducerStopAtWildcard() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.List<? super java.lang.Number>", null));
  }

  public void testProducerFailToStopAtWildcard() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.List<? super java.lang.Integer>", null));
  }

  public void testProducerExtendsFailToStopAtWildcard() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.List<? extends java.lang.Number>", null));
  }


  public void testConsumerExtends() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? extends java.lang.Number>", null));
  }

  public void testConsumerSuper() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? super java.lang.Number>", null));
  }

  public void testConsumerUnbounded() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));
  }

  // array -> list
  public void testAssignmentExtends() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? extends java.lang.Integer>", null));
  }

  public void testAssignmentSuper() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? super java.lang.Integer>", null));
  }

  public void testAssignmentUnbounded() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));
  }

  public void testGetExtends() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? extends java.lang.Integer>", null));
  }

  public void testGetSuper() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<? super java.lang.Integer>", null));
  }

  public void testGetUnbounded() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));

  }

  public void testLengthSize() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.ArrayList<?>", null));

  }

  //list -> array
  public void testGetAssignmentExtendsToType() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Number", null).createArrayType());
  }

  public void testGetAssignmentExtendsToSuperType() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null).createArrayType());
  }

  public void testGetAssignmentExtendsToChildType() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null).createArrayType());
  }

  // -> threadlocal with wildcard
  public void testThreadLocalProducerExtends() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.util.List<? extends String>>", null));
  }

  //List<? super String> is not assignable to List<String> though it is possible to pass string where ? super String was   
  public void _testThreadLocalProducerSuper() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.util.List<? super String>>", null));
  }

  public void testThreadLocalConsumerSuper() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<? super String>", null));
  }

  public void testThreadLocalConsumerExtends() {
    doTestFirstParamType("method",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<? extends String>", null));
  }
}