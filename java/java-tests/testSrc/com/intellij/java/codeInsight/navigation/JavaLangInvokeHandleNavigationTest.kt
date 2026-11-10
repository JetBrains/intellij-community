// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiMember
import kotlin.time.Duration.Companion.minutes
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.intellij.lang.annotations.Language
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.NonNls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class JavaLangInvokeHandleNavigationTest {
  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testVirtual1(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("m1", VIRTUAL) }
  @Test
  fun testVirtual2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("m2", VIRTUAL) }
  @Test
  fun testVirtual3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("pm1", VIRTUAL) }
  @Test
  fun testVirtual4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("pm2", VIRTUAL) }
  @Test
  fun testVirtual5(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("sm1", VIRTUAL) }
  @Test
  fun testVirtual6(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("psm1", VIRTUAL) }
  @Test
  fun testVirtual7(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("f1", VIRTUAL) }

  @Test
  fun testStatic1(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("sm1", STATIC) }
  @Test
  fun testStatic2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("sm2", STATIC) }
  @Test
  fun testStatic3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("psm1", STATIC) }
  @Test
  fun testStatic4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("psm2", STATIC) }
  @Test
  fun testStatic5(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("m1", STATIC) }
  @Test
  fun testStatic6(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("pm1", STATIC) }
  @Test
  fun testStatic7(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("f1", STATIC) }

  @Test
  fun testGetter1(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("f1", GETTER) }
  @Test
  fun testGetter2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("f2", GETTER) }
  @Test
  fun testGetter3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("pf1", GETTER) }
  @Test
  fun testGetter4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("pf2", GETTER) }
  @Test
  fun testGetter5(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("sf1", GETTER) }
  @Test
  fun testGetter6(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("psf1", GETTER) }
  @Test
  fun testGetter7(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("m1", GETTER) }

  @Test
  fun testSetter1(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("f1", SETTER) }
  @Test
  fun testSetter2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("f2", SETTER) }
  @Test
  fun testSetter3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("pf1", SETTER) }
  @Test
  fun testSetter4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("pf2", SETTER) }
  @Test
  fun testSetter5(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("sf1", SETTER) }
  @Test
  fun testSetter6(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("psf1", SETTER) }
  @Test
  fun testSetter7(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("m1", SETTER) }

  @Test
  fun testStaticGetter1(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("sf1", STATIC_GETTER) }
  @Test
  fun testStaticGetter2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("sf2", STATIC_GETTER) }
  @Test
  fun testStaticGetter3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("psf1", STATIC_GETTER) }
  @Test
  fun testStaticGetter4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("psf2", STATIC_GETTER) }
  @Test
  fun testStaticGetter5(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("f1", STATIC_GETTER) }
  @Test
  fun testStaticGetter6(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("pf1", STATIC_GETTER) }
  @Test
  fun testStaticGetter7(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("m1", STATIC_GETTER) }

  @Test
  fun testStaticSetter1(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("sf1", STATIC_SETTER) }
  @Test
  fun testStaticSetter2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("sf2", STATIC_SETTER) }
  @Test
  fun testStaticSetter3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("psf1", STATIC_SETTER) }
  @Test
  fun testStaticSetter4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("psf2", STATIC_SETTER) }
  @Test
  fun testStaticSetter5(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("f1", STATIC_SETTER) }
  @Test
  fun testStaticSetter6(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("pf1", STATIC_SETTER) }
  @Test
  fun testStaticSetter7(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doNegativeTest("m1", STATIC_SETTER) }

  @Test
  fun testSpecial1(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doSpecialNegativeTest("pm1", "void.class, int.class", "Object") }
  @Test
  fun testSpecial2(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doSpecialTest("pm1", "void.class, int.class") }
  @Test
  fun testSpecial3(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doSpecialNegativeTest("m1", "void.class, int.class") }
  @Test
  fun testSpecial4(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doSpecialTest("m1", "void.class, int.class", "Test") }

  @Test
  fun testOverloadedBothPublic(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestOverloaded(
    """public class Overloaded {
  public void foo(int n) {}
  public void foo(String s) {}
}""", VIRTUAL, "java.lang.String") }

  @Test
  fun testOverloadedFirstPublic(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestOverloaded(
    """public class Overloaded {
  public void foo(int n) {}
  void foo(String s) {}
}""", VIRTUAL, "int") }

  @Test
  fun testOverloadedSecondPublic(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestOverloaded(
    """public class Overloaded {
  void foo(int n) {}
  public void foo(String s) {}
}""", VIRTUAL, "java.lang.String") }

  @Test
  fun testOverloadedInherited(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.addClass("""public class OverloadedParent {
  public static void foo(String s) {}
}""")

    doTestOverloaded(
      """public class Overloaded extends OverloadedParent {
  public static void foo(int n) {}
}""", STATIC, "java.lang.String")
  }

  @Test
  fun testOverloadedStatic(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestOverloaded(
    """public class Overloaded {
  public static void foo(int n) {}
  public static void foo(String s) {}
}""", STATIC, "java.lang.String") }

  private suspend fun doTestOverloaded(@NonNls @Language("JAVA") classText: String, function: String, vararg expectedParameterTypes: String) {
    myFixture.addClass(classText)

    val methodType = arrayOf("void", *expectedParameterTypes).map { "$it.class" }.joinToString(", ")
    val member = doTestImpl("foo", """
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
class Main {
  void foo() throws ReflectiveOperationException {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.$function(Overloaded.class, "<caret>foo", MethodType.methodType($methodType));
  }
}""")

    readAction {
      assertTrue(member is PsiMethod, "Is method")
      val parameters = (member as PsiMethod).parameterList.parameters
      assertEquals(expectedParameterTypes.size, parameters.size, "Parameter count")
      for (i in expectedParameterTypes.indices) {
        assertEquals(expectedParameterTypes[i], parameters[i].type.canonicalText, "Parameter $i")
      }
    }
  }

  private suspend fun doTest(name: String,
                             @MagicConstant(stringValues = arrayOf(VIRTUAL, STATIC, SPECIAL,
                                                                   GETTER, SETTER,
                                                                   STATIC_GETTER, STATIC_SETTER,
                                                                   VAR_HANDLE, STATIC_VAR_HANDLE)) function: String) {
    doTestImpl(name, getMainClassText(name, function))
  }

  private suspend fun doTestImpl(name: String, mainClassText: String): PsiMember {
    val reference = getReference(mainClassText)
    return readAction {
      assertEquals(name, reference.canonicalText, "Reference text")
      val resolved = reference.resolve()
      assertNotNull(resolved, "Reference is not resolved: " + reference.canonicalText)
      assertTrue(resolved is PsiMember, "Target is a member")
      val member = resolved as PsiMember?
      assertEquals(name, member!!.name, "Target name")
      member
    }
  }

  private suspend fun doNegativeTest(name: String,
                                     @MagicConstant(stringValues = arrayOf(VIRTUAL, STATIC, SPECIAL,
                                                                           GETTER, SETTER,
                                                                           STATIC_GETTER, STATIC_SETTER,
                                                                           VAR_HANDLE, STATIC_VAR_HANDLE)) function: String) {
    val reference = getReference(getMainClassText(name, function))
    readAction {
      assertEquals(name, reference.canonicalText, "Reference text")
      val resolved = reference.resolve()
      assertNull(resolved, "Reference shouldn't resolve: " + reference.canonicalText)
    }
  }

  private suspend fun doSpecialTest(name: String, methodType: String, declaredIn: String = "Parent", calledIn: String = "Test") {
    doTestImpl(name, specialClassText(name, methodType, declaredIn, calledIn))
  }

  private suspend fun doSpecialNegativeTest(name: String, methodType: String, declaredIn: String = "Parent", calledIn: String = "Test") {
    val reference = getReference(specialClassText(name, methodType, declaredIn, calledIn))
    readAction {
      assertEquals(name, reference.canonicalText, "Reference text")
      val resolved = reference.resolve()
      assertNull(resolved, "Reference shouldn't resolve: " + reference.canonicalText)
    }
  }

  private fun specialClassText(name: String, methodType: String, declaredIn: String, calledIn: String): String {
    @Suppress("UnnecessaryVariable")
    @Language("JAVA")
    val text = """import foo.bar.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
class Main {
 void foo() throws ReflectiveOperationException {
   MethodHandles.Lookup lookup = MethodHandles.lookup();
   lookup.findSpecial($declaredIn.class, "<caret>$name", MethodType.methodType($methodType), $calledIn.class);
 }
}"""
    return text
  }

  private suspend fun getReference(mainClassText: String): PsiReference {
    myFixture.addClass("""package foo.bar;
public class Parent {
  public int pf1;
  private int pf2;
  public static int psf1;
  private static int psf2;

  public void pm1(int n) {}
  private void pm2(int n) {}
  public static void psm1() {}
  private static void psm2() {}
}""")
    myFixture.addClass("""package foo.bar;
public class Test extends Parent {
  public int f1;
  private int f2;
  public static int sf1;
  private static int sf2;

  public void m1(int n) {}
  private void m2(int n) {}
  public static void sm1() {}
  private static void sm2() {}
}""")
    myFixture.configureByText("Main.java", mainClassText)

    return readAction {
      val offset = myFixture.caretOffset
      val reference = myFixture.file.findReferenceAt(offset)
      assertNotNull(reference, "No reference at the caret")
      reference!!
    }
  }

  private fun getMainClassText(name: String, function: String): String {
    return """import foo.bar.*;
import java.lang.invoke.MethodHandles;

class Main {
  void foo() throws ReflectiveOperationException {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.$function(Test.class, "<caret>$name");
  }
}"""
  }

  companion object {
    private val projectFixture = lightJavaProjectFixture()
    const val VIRTUAL = "findVirtual"
    const val STATIC = "findStatic"
    const val SPECIAL = "findSpecial"

    const val GETTER = "findGetter"
    const val SETTER = "findSetter"
    const val STATIC_GETTER = "findStaticGetter"
    const val STATIC_SETTER = "findStaticSetter"

    const val VAR_HANDLE = "findVarHandle"
    const val STATIC_VAR_HANDLE = "findStaticVarHandle"
  }
}
