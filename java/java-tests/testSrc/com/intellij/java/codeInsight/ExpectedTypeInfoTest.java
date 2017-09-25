/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class ExpectedTypeInfoTest extends LightCodeInsightTestCase {
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
    assertEquals(result.length, 0);
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
    assertEquals(result.length, 0);
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
    assertEquals(result.length, 0);
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
    assertEquals(result.length, 0);
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
    assertEquals(result.length, 0);
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
    assertEquals(result.length, 0);
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
    assertEquals(result.length, 0);
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
  private static ExpectedTypeInfo createInfo(String className, @ExpectedTypeInfo.Type int kind) {
    PsiType type = getJavaFacade().getElementFactory().createTypeByFQClassName(className, GlobalSearchScope.allScope(getProject()));
    return ExpectedTypesProvider.createInfo(type, kind, type, TailType.NONE);
  }
}
