// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailTypes;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class ExpectedTypeInfoTest extends LightJavaCodeInsightTestCase {
  public void testIntersectStrictStrict1() {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.lang.Exception"));
  }

  public void testIntersectStrictStrict2() {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo(CommonClassNames.JAVA_LANG_OBJECT, ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(0, result.length);
  }

  public void testIntersectSubtypeStrict1() {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSubtypeStrict2() {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo(CommonClassNames.JAVA_LANG_OBJECT, ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(0, result.length);
  }

  public void testIntersectSupertypeStrict1() {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.lang.Exception"));
  }

  public void testIntersectSupertypeStrict2() {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_STRICTLY);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(0, result.length);
  }

  public void testIntersectStrictSubtype1() {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectStrictSubtype2() {
    ExpectedTypeInfo info1 = createInfo(CommonClassNames.JAVA_LANG_OBJECT, ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(0, result.length);
  }

  public void testIntersectStrictSupertype1() {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.lang.Exception"));
  }

  public void testIntersectStrictSupertype2() {
    ExpectedTypeInfo info1 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_STRICTLY);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(0, result.length);
  }

  public void testIntersectSubtypeSubtype1() {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUBTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSubtypeSubtype2() {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUBTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSubtypeSubtype3() {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("javax.swing.JButton", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(0, result.length);
  }

  public void testIntersectSuperSuper1() {
    ExpectedTypeInfo info1 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUPERTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSuperSuper2() {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUPERTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.IOException"));
  }

  public void testIntersectSuperSuper3() {
    ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
    ExpectedTypeInfo info2 = createInfo("javax.swing.JButton", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertEquals(0, result.length);
  }

  public void testIntersectSubSuper1() {
    ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
    ExpectedTypeInfo info2 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

    ExpectedTypeInfo[] result = info1.intersect(info2);
    assertNotNull(result);
    assertEquals(1, result.length);
    assertEquals(ExpectedTypeInfo.TYPE_OR_SUPERTYPE, result[0].getKind());
    assertTrue(result[0].getType().equalsToText("java.io.EOFException"));
  }

  @NotNull
  private ExpectedTypeInfo createInfo(String className, @ExpectedTypeInfo.Type int kind) {
    PsiType type = getJavaFacade().getElementFactory().createTypeByFQClassName(className, GlobalSearchScope.allScope(getProject()));
    return ExpectedTypesProvider.createInfo(type, kind, type, TailTypes.noneType());
  }
}
