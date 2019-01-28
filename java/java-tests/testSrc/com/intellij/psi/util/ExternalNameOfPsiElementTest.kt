// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util

import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * Tests our expectations on which string presentation of PSI elements
 * is returned by method [PsiFormatUtil.getExternalName].
 *
 * This format is used in external annotations files, "annotations.xml", to specify item names:
 * `<item name="org.jetbrains.some.Class void func()">...</item>`
 */
class ExternalNameOfPsiElementTest : LightCodeInsightFixtureTestCase() {
  fun `test class qualified name`() {
    myFixture.addFileToProject("pkg/A.java", """
      package pkg;

      class A { }
      class B<T> { }
      class C {
        class D { }
      }
      class E<T> {
        class F<V> { }
      }
    """.trimIndent())

    val className2externalName = mapOf(
      "pkg.A" to "pkg.A",
      "pkg.B" to "pkg.B",
      "pkg.C" to "pkg.C",
      "pkg.C.D" to "pkg.C.D",
      "pkg.E.F" to "pkg.E.F"
    )

    className2externalName.forEach { className, externalName ->
      val psiClass = myFixture.findClass(className)
      assertEquals(externalName, psiClass.getExternalName())
    }
  }

  fun `test method qualified name`() {
    myFixture.addFileToProject("pkg/A.java", """
      package pkg;

      import java.util.List;
      import java.util.Map;

      class A {
        public A() { }
        public void m1() {}
        public int m2() { return 0; }
        public String m3() { return ""; }
        public void m4(String s) { }
        public void m5(List<String> l) { }
        public <T> T m6() { return null; }
        public <T> void m7(T t) { }
        public <T extends Number, S extends T> T m8(S s) { return s; }
        public void m9(Map<String, Integer> m) { }
        public <K, V> void m10(Map<K, V> m) { }
        public int[] m11() { return null; }
        public String[][] m12() { return null; }
        public List<Comparable<? extends Number>> m13() { return null; }
        public <E extends Class> Class<? extends E> m14() { return null; }
        public <E extends Class> Class<? super E> m15() { return null; }
        public <E, T extends Comparable<E>> Map<Object, String> m16(Object o, Map<Object, String> m, T t) { return null; }
        public void m17(Class<?> c, Class<?>[][] cs) { }
      }
    """.trimMargin())

    val methodName2externalName = mapOf(
      "A" to "pkg.A A()",
      "m1" to "pkg.A void m1()",
      "m2" to "pkg.A int m2()",
      "m3" to "pkg.A java.lang.String m3()",
      "m4" to "pkg.A void m4(java.lang.String)",
      "m5" to "pkg.A void m5(java.util.List<java.lang.String>)",
      "m6" to "pkg.A T m6()",
      "m7" to "pkg.A void m7(T)",
      "m8" to "pkg.A T m8(S)",
      "m9" to "pkg.A void m9(java.util.Map<java.lang.String,java.lang.Integer>)",
      "m10" to "pkg.A void m10(java.util.Map<K,V>)",
      "m11" to "pkg.A int[] m11()",
      "m12" to "pkg.A java.lang.String[][] m12()",
      "m13" to "pkg.A java.util.List<java.lang.Comparable<? extends java.lang.Number>> m13()",
      "m14" to "pkg.A java.lang.Class<? extends E> m14()",
      "m15" to "pkg.A java.lang.Class<? super E> m15()",
      "m16" to "pkg.A java.util.Map<java.lang.Object,java.lang.String> m16(java.lang.Object, java.util.Map<java.lang.Object,java.lang.String>, T)",
      "m17" to "pkg.A void m17(java.lang.Class<?>, java.lang.Class<?>[][])"
    )

    val psiClass = myFixture.findClass("pkg.A")
    methodName2externalName.forEach { methodName, externalName ->
      val psiMethod = psiClass.findMethodsByName(methodName, false).single()
      assertEquals(externalName, psiMethod.getExternalName())
    }
  }

  fun `test inner class empty constructor qualified name`() {
    myFixture.addFileToProject("pkg/A.java", """
      package pkg;

      public class A {
        public class B {
          public B() { }
        }
      }

    """.trimMargin())

    val bClass = myFixture.findClass("pkg.A.B")
    val emptyCtr = bClass.constructors[0]
    assertEquals("pkg.A.B B()", emptyCtr.getExternalName())
  }

  fun `test field qualified name`() {
    myFixture.addFileToProject("pkg/A.java", """
      package pkg;

      class A<T> {
        public int f1;
        public String f2;
        public List<String> f3;
        public T f4;
      }

    """.trimMargin())

    val fieldName2externalName = mapOf(
      "f1" to "pkg.A f1",
      "f2" to "pkg.A f2",
      "f3" to "pkg.A f3",
      "f4" to "pkg.A f4"
    )

    val psiClass = myFixture.findClass("pkg.A")
    fieldName2externalName.forEach { fieldName, externalName ->
      val psiField = psiClass.findFieldByName(fieldName, false)!!
      assertEquals(externalName, psiField.getExternalName())
    }
  }

  fun `test annotation parameter qualified name`() {
    myFixture.addFileToProject("pkg/A.java", """
      package pkg;

      @interface A {
        int p1() default 0;
        String p2() default "";
      }
    """.trimMargin())

    val paramName2externalName = mapOf(
      "p1" to "pkg.A int p1()",
      "p2" to "pkg.A java.lang.String p2()"
    )

    val psiClass = myFixture.findClass("pkg.A")
    paramName2externalName.forEach { paramName, externalName ->
      val psiMethod = psiClass.findMethodsByName(paramName, false).single()
      assertEquals(externalName, psiMethod.getExternalName())
    }
  }

  private fun PsiModifierListOwner.getExternalName() =
    PsiFormatUtil.getExternalName(this, false, Integer.MAX_VALUE)
}