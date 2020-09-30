// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation

import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.NonNls

/**
 * @author Pavel.Dolgov
 */
class JavaLangInvokeHandleNavigationTest : LightJavaCodeInsightFixtureTestCase() {

  fun testVirtual1() = doTest("m1", VIRTUAL)
  fun testVirtual2() = doTest("m2", VIRTUAL)
  fun testVirtual3() = doTest("pm1", VIRTUAL)
  fun testVirtual4() = doTest("pm2", VIRTUAL)
  fun testVirtual5() = doNegativeTest("sm1", VIRTUAL)
  fun testVirtual6() = doNegativeTest("psm1", VIRTUAL)
  fun testVirtual7() = doNegativeTest("f1", VIRTUAL)

  fun testStatic1() = doTest("sm1", STATIC)
  fun testStatic2() = doTest("sm2", STATIC)
  fun testStatic3() = doTest("psm1", STATIC)
  fun testStatic4() = doTest("psm2", STATIC)
  fun testStatic5() = doNegativeTest("m1", STATIC)
  fun testStatic6() = doNegativeTest("pm1", STATIC)
  fun testStatic7() = doNegativeTest("f1", STATIC)

  fun testGetter1() = doTest("f1", GETTER)
  fun testGetter2() = doTest("f2", GETTER)
  fun testGetter3() = doTest("pf1", GETTER)
  fun testGetter4() = doTest("pf2", GETTER)
  fun testGetter5() = doNegativeTest("sf1", GETTER)
  fun testGetter6() = doNegativeTest("psf1", GETTER)
  fun testGetter7() = doNegativeTest("m1", GETTER)

  fun testSetter1() = doTest("f1", SETTER)
  fun testSetter2() = doTest("f2", SETTER)
  fun testSetter3() = doTest("pf1", SETTER)
  fun testSetter4() = doTest("pf2", SETTER)
  fun testSetter5() = doNegativeTest("sf1", SETTER)
  fun testSetter6() = doNegativeTest("psf1", SETTER)
  fun testSetter7() = doNegativeTest("m1", SETTER)

  fun testStaticGetter1() = doTest("sf1", STATIC_GETTER)
  fun testStaticGetter2() = doTest("sf2", STATIC_GETTER)
  fun testStaticGetter3() = doTest("psf1", STATIC_GETTER)
  fun testStaticGetter4() = doTest("psf2", STATIC_GETTER)
  fun testStaticGetter5() = doNegativeTest("f1", STATIC_GETTER)
  fun testStaticGetter6() = doNegativeTest("pf1", STATIC_GETTER)
  fun testStaticGetter7() = doNegativeTest("m1", STATIC_GETTER)

  fun testStaticSetter1() = doTest("sf1", STATIC_SETTER)
  fun testStaticSetter2() = doTest("sf2", STATIC_SETTER)
  fun testStaticSetter3() = doTest("psf1", STATIC_SETTER)
  fun testStaticSetter4() = doTest("psf2", STATIC_SETTER)
  fun testStaticSetter5() = doNegativeTest("f1", STATIC_SETTER)
  fun testStaticSetter6() = doNegativeTest("pf1", STATIC_SETTER)
  fun testStaticSetter7() = doNegativeTest("m1", STATIC_SETTER)

  fun testSpecial1() = doSpecialNegativeTest("pm1", "void.class, int.class", "Object")
  fun testSpecial2() = doSpecialTest("pm1", "void.class, int.class")
  fun testSpecial3() = doSpecialNegativeTest("m1", "void.class, int.class")
  fun testSpecial4() = doSpecialTest("m1", "void.class, int.class", "Test")

  fun testOverloadedBothPublic() = doTestOverloaded(
    """public class Overloaded {
  public void foo(int n) {}
  public void foo(String s) {}
}""", VIRTUAL, "java.lang.String")

  fun testOverloadedFirstPublic() = doTestOverloaded(
    """public class Overloaded {
  public void foo(int n) {}
  void foo(String s) {}
}""", VIRTUAL, "int")

  fun testOverloadedSecondPublic() = doTestOverloaded(
    """public class Overloaded {
  void foo(int n) {}
  public void foo(String s) {}
}""", VIRTUAL, "java.lang.String")

  fun testOverloadedInherited() {
    myFixture.addClass("""public class OverloadedParent {
  public static void foo(String s) {}
}""")

    doTestOverloaded(
      """public class Overloaded extends OverloadedParent {
  public static void foo(int n) {}
}""", STATIC, "java.lang.String")
  }

  fun testOverloadedStatic() = doTestOverloaded(
    """public class Overloaded {
  public static void foo(int n) {}
  public static void foo(String s) {}
}""", STATIC, "java.lang.String")


  private fun doTestOverloaded(@NonNls @Language("JAVA") classText: String, function: String, vararg expectedParameterTypes: String) {
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

    TestCase.assertTrue("Is method", member is PsiMethod)
    val parameters = (member as PsiMethod).parameterList.parameters
    TestCase.assertEquals("Parameter count", expectedParameterTypes.size, parameters.size)
    for (i in 0 until expectedParameterTypes.size) {
      TestCase.assertEquals("Parameter $i", expectedParameterTypes[i], parameters[i].type.canonicalText)
    }
  }


  private fun doTest(name: String,
                     @MagicConstant(stringValues = arrayOf(VIRTUAL, STATIC, SPECIAL,
                                                           GETTER, SETTER,
                                                           STATIC_GETTER, STATIC_SETTER,
                                                           VAR_HANDLE, STATIC_VAR_HANDLE)) function: String) {
    doTestImpl(name, getMainClassText(name, function))
  }

  private fun doTestImpl(name: String, mainClassText: String): PsiMember {
    val reference = getReference(mainClassText)
    TestCase.assertEquals("Reference text", name, reference.canonicalText)
    val resolved = reference.resolve()
    TestCase.assertNotNull("Reference is not resolved: " + reference.canonicalText, resolved)
    TestCase.assertTrue("Target is a member", resolved is PsiMember)
    val member = resolved as PsiMember?
    TestCase.assertEquals("Target name", name, member!!.name)
    return member
  }

  private fun doNegativeTest(name: String,
                             @MagicConstant(stringValues = arrayOf(VIRTUAL, STATIC, SPECIAL,
                                                                   GETTER, SETTER,
                                                                   STATIC_GETTER, STATIC_SETTER,
                                                                   VAR_HANDLE, STATIC_VAR_HANDLE)) function: String) {
    val reference = getReference(getMainClassText(name, function))
    TestCase.assertEquals("Reference text", name, reference.canonicalText)
    val resolved = reference.resolve()
    TestCase.assertNull("Reference shouldn't resolve: " + reference.canonicalText, resolved)
  }

  private fun doSpecialTest(name: String, methodType: String, declaredIn: String = "Parent", calledIn: String = "Test") {
    doTestImpl(name, specialClassText(name, methodType, declaredIn, calledIn))
  }

  private fun doSpecialNegativeTest(name: String, methodType: String, declaredIn: String = "Parent", calledIn: String = "Test") {
    val reference = getReference(specialClassText(name, methodType, declaredIn, calledIn))
    TestCase.assertEquals("Reference text", name, reference.canonicalText)
    val resolved = reference.resolve()
    TestCase.assertNull("Reference shouldn't resolve: " + reference.canonicalText, resolved)
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

  private fun getReference(mainClassText: String): PsiReference {
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

    val offset = myFixture.caretOffset
    val reference = myFixture.file.findReferenceAt(offset)
    assertNotNull("No reference at the caret", reference)
    return reference!!
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
