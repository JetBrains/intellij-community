/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 19, 2002
 * Time: 5:40:30 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class ExpectedTypeInfoTest extends LightCodeInsightTestCase {
  public void testIntersectStrictStrict1() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.lang.Exception"));
  }

  public void testIntersectStrictStrict2() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo(CommonClassNames.JAVA_LANG_OBJECT, ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(result.length, 0);
  }

  public void testIntersectSubtypeStrict1() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSubtypeStrict2() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo(CommonClassNames.JAVA_LANG_OBJECT, ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(result.length, 0);
  }

  public void testIntersectSupertypeStrict1() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.lang.Exception"));
  }

  public void testIntersectSupertypeStrict2() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(result.length, 0);
  }

  public void testIntersectStrictSubtype1() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectStrictSubtype2() throws Exception {
    ExpectedTypeInfo info1 = createInfo(CommonClassNames.JAVA_LANG_OBJECT, ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(result.length, 0);
  }

  public void testIntersectStrictSupertype1() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.lang.Exception"));
  }

  public void testIntersectStrictSupertype2() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(result.length, 0);
  }

  public void testIntersectSubtypeSubtype1() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUBTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSubtypeSubtype2() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUBTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSubtypeSubtype3() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("javax.swing.JButton", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(result.length, 0);
  }

  public void testIntersectSuperSuper1() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUPERTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSuperSuper2() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUPERTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSuperSuper3() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("javax.swing.JButton", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(result.length, 0);
  }

  public void testIntersectSubSuper1() throws Exception {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertNotNull(result);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUPERTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.EOFException"));
  }

  @NotNull
  private static ExpectedTypeInfo createInfo(String className, @ExpectedTypeInfo.Type int kind) throws Exception{
    PsiType type = getJavaFacade().getElementFactory().createTypeByFQClassName(className, GlobalSearchScope.allScope(getProject()));
    return ExpectedTypesProvider.createInfo(type, kind, type, TailType.NONE);
  }
}
