// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation;

import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class JavaReflectionNavigationTest extends LightJavaCodeInsightFixtureTestCase {

  private static final String FIELD = "Field";
  private static final String METHOD = "Method";
  private static final String DF = "DeclaredField";
  private static final String DM = "DeclaredMethod";

  public void testField() {doTest("field", FIELD);}

  public void testField2() {doTest("field2", FIELD);}

  public void testDeclaredField() {doTest("field2", DF);}

  public void testNonexistentField() {doNegativeTest("nonexistent", FIELD);}

  public void testMethod() {doTest("method", METHOD);}

  public void testMethod2() {doTest("method2", METHOD);}

  public void testDeclaredMethod() {doTest("method2", DM);}

  public void testNonexistentMethod() {doNegativeTest("nonexistent", METHOD);}


  public void testInheritedField() {doTest("field3", FIELD);}

  public void testInheritedField2() {doTest("field4", FIELD);}

  public void testInheritedDeclaredField() {doNegativeTest("field3", DF);}

  public void testInheritedMethod() {doTest("method3", METHOD);}

  public void testInheritedMethod2() {doTest("method4", METHOD);}

  public void testInheritedDeclaredMethod() {doNegativeTest("method3", DM);}

  public void testClassDesc() {
    myFixture.addClass("package java.lang.constant; public interface ClassDesc {static ClassDesc of(String name) {return null;}}");
    myFixture.configureByText("Main.java", "class Main {{java.lang.constant.ClassDesc.of(\"java.lang.Obj<caret>ect\");}}");

    int offset = myFixture.getCaretOffset();
    PsiReference reference = myFixture.getFile().findReferenceAt(offset);
    assertNotNull("No reference at the caret", reference);
    PsiElement element = reference.resolve();
    assertTrue(element instanceof PsiClass);
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, ((PsiClass)element).getQualifiedName());
  }

  public void testConstantClassName() {
    doCustomTest("method2",
                 "class Main {" +
                 "  static final String NAME = \"Test\";" +
                 "  void foo() throws ReflectiveOperationException {" +
                 "    Class.forName(NAME).getMethod(\"<caret>method2\", int.class);" +
                 "  }" +
                 "}");
  }

  public void testVariableClassName() {
    doCustomTest("method",
                 "class Main {" +
                 "  void foo() throws ReflectiveOperationException {" +
                 "    String name;" +
                 "    name = \"Te\" + \"st\";" +
                 "    Class.forName(name).getMethod(\"<caret>method\");" +
                 "  }" +
                 "}");
  }

  public void testExpressionClassName() {
    doCustomTest("method3",
                 "class Main {" +
                 "  void foo() throws ReflectiveOperationException {" +
                 "    Class.forName(\"Pa\" + \"rent\").getMethod(\"<caret>method3\");" +
                 "  }" +
                 "}");
  }

  public void testOverloadedMethodBothPublic() {
    doTestOverloadedMethod("foo",
                           """
                             class Overloaded {
                               public void foo() {}
                               public void foo(String s) {}
                             }""", false, "java.lang.String");
  }

  public void testOverloadedMethodPublicFirst() {
    doTestOverloadedMethod("foo",
                           """
                             class Overloaded {
                               public void foo() {}
                               void foo(String s) {}
                             }""", false);
  }

  public void testOverloadedMethodPublicSecond() {
    doTestOverloadedMethod("foo",
                           """
                             class Overloaded {
                               void foo() {}
                               public void foo(String s) {}
                             }""", false, "java.lang.String");
  }

  public void testOverloadedDeclaredMethod() {
    doTestOverloadedMethod("foo",
                           """
                             class Overloaded {
                               public void foo() {}
                               public void foo(String s) {}
                             }""", true, "java.lang.String");
  }

  public void testOverloadedInheritedMethod() {
    doTestOverloadedMethod("bar",
                           """
                             class OverloadedParent {  public void bar(String s) {}
                             }class Overloaded extends OverloadedParent {
                               public void bar() {}
                             }""", false, "java.lang.String");
  }

  private void doTestOverloadedMethod(String name,
                                      @NotNull @NonNls @Language("JAVA") String classText,
                                      boolean isDeclared,
                                      String... expectedParameterTypes) {
    myFixture.addClass(classText);

    PsiMember member = doTestImpl(name,
                                  "class Main {" +
                                  "  void main() {" +
                                  "    Overloaded.class.get" + (isDeclared?"Declared":"") + "Method(\"<caret>"+name+"\", String.class);" +
                                  "  }" +
                                  "}");
    assertTrue("Target is a method", member instanceof PsiMethod);
    PsiMethod method = (PsiMethod)member;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    assertEquals("Parameter count", expectedParameterTypes.length, parameters.length);
    for (int i = 0; i < expectedParameterTypes.length; i++) {
      assertEquals("Parameter type " + i, expectedParameterTypes[i], parameters[0].getType().getCanonicalText());
    }
  }


  private void doTest(String name,
                      @MagicConstant(stringValues = {FIELD, METHOD, DF, DM}) String type) {
    doTestImpl(name, getMainClassText(name, type));
  }

  private void doCustomTest(String name,
                            @NotNull @NonNls @Language("JAVA") String mainClassText) {
    doTestImpl(name, mainClassText);
  }

  private PsiMember doTestImpl(String name, @NotNull @NonNls @Language("JAVA") String mainClassText) {
    PsiReference reference = getReference(mainClassText);
    assertEquals("Reference text", name, reference.getCanonicalText());
    PsiElement resolved = reference.resolve();
    assertNotNull("Reference is not resolved: " + reference.getCanonicalText(), resolved);
    assertTrue("Target is a member", resolved instanceof PsiMember);
    PsiMember member = (PsiMember)resolved;
    assertEquals("Target name", name, member.getName());
    return member;
  }

  private void doNegativeTest(String name,
                              @MagicConstant(stringValues = {FIELD, METHOD, DF, DM}) String type) {
    PsiReference reference = getReference(getMainClassText(name, type));
    assertEquals("Reference text", name, reference.getCanonicalText());
    PsiElement resolved = reference.resolve();
    assertNull("Reference shouldn't resolve: " + reference.getCanonicalText(), resolved);
  }

  @NotNull
  private PsiReference getReference(String mainClassText) {
    myFixture.addClass("""
                         class Parent {
                           public int field3;
                           int field4;
                           public void method3(int n) {}
                           void method4(int n) {}
                         }""");
    myFixture.addClass("""
                         class Test extends Parent {
                           public int field;
                           int field2;
                           public void method() {}
                           void method2(int n) {}
                         }""");
    myFixture.configureByText("Main.java", mainClassText);

    int offset = myFixture.getCaretOffset();
    PsiReference reference = myFixture.getFile().findReferenceAt(offset);
    assertNotNull("No reference at the caret", reference);
    return reference;
  }

  @NotNull
  private static String getMainClassText(String name,
                                         @MagicConstant(stringValues = {FIELD, METHOD, DF, DM}) String type) {
    return "class Main {\n" +
           "  void foo() throws ReflectiveOperationException {\n" +
           "    Test.class.get" + type + "(\"<caret>" + name + "\");\n" +
           "  }\n" +
           "}";
  }
}
