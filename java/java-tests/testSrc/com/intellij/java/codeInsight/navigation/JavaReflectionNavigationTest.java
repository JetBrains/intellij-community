// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaReflectionNavigationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String FIELD = "Field";
  private static final String METHOD = "Method";
  private static final String DF = "DeclaredField";
  private static final String DM = "DeclaredMethod";

  @Test
  public void testField() {
    doTest("field", FIELD);
  }

  @Test
  public void testField2() {
    doTest("field2", FIELD);
  }

  @Test
  public void testDeclaredField() {
    doTest("field2", DF);
  }

  @Test
  public void testNonexistentField() {
    doNegativeTest("nonexistent", FIELD);
  }

  @Test
  public void testMethod() {
    doTest("method", METHOD);
  }

  @Test
  public void testMethod2() {
    doTest("method2", METHOD);
  }

  @Test
  public void testDeclaredMethod() {
    doTest("method2", DM);
  }

  @Test
  public void testNonexistentMethod() {
    doNegativeTest("nonexistent", METHOD);
  }

  @Test
  public void testInheritedField() {
    doTest("field3", FIELD);
  }

  @Test
  public void testInheritedField2() {
    doTest("field4", FIELD);
  }

  @Test
  public void testInheritedDeclaredField() {
    doNegativeTest("field3", DF);
  }

  @Test
  public void testInheritedMethod() {
    doTest("method3", METHOD);
  }

  @Test
  public void testInheritedMethod2() {
    doTest("method4", METHOD);
  }

  @Test
  public void testInheritedDeclaredMethod() {
    doNegativeTest("method3", DM);
  }

  @Test
  public void testClassDesc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package java.lang.constant; public interface ClassDesc {static ClassDesc of(String name) {return null;}}");
    myFixture.configureByText("Main.java", "class Main {{java.lang.constant.ClassDesc.of(\"java.lang.Obj<caret>ect\");}}");

    ReadAction.runBlocking(() -> {
      int offset = myFixture.getCaretOffset();
      PsiReference reference = myFixture.getFile().findReferenceAt(offset);
      assertNotNull(reference, "No reference at the caret");
      PsiElement element = reference.resolve();
      assertTrue(element instanceof PsiClass);
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, ((PsiClass)element).getQualifiedName());
    });
  }

  @Test
  public void testConstantClassName() {
    doCustomTest("method2",
                 "class Main {" +
                 "  static final String NAME = \"Test\";" +
                 "  void foo() throws ReflectiveOperationException {" +
                 "    Class.forName(NAME).getMethod(\"<caret>method2\", int.class);" +
                 "  }" +
                 "}");
  }

  @Test
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

  @Test
  public void testExpressionClassName() {
    doCustomTest("method3",
                 "class Main {" +
                 "  void foo() throws ReflectiveOperationException {" +
                 "    Class.forName(\"Pa\" + \"rent\").getMethod(\"<caret>method3\");" +
                 "  }" +
                 "}");
  }

  @Test
  public void testOverloadedMethodBothPublic() {
    doTestOverloadedMethod("foo",
                           """
                             class Overloaded {
                               public void foo() {}
                               public void foo(String s) {}
                             }""", false, "java.lang.String");
  }

  @Test
  public void testOverloadedMethodPublicFirst() {
    doTestOverloadedMethod("foo",
                           """
                             class Overloaded {
                               public void foo() {}
                               void foo(String s) {}
                             }""", false);
  }

  @Test
  public void testOverloadedMethodPublicSecond() {
    doTestOverloadedMethod("foo",
                           """
                             class Overloaded {
                               void foo() {}
                               public void foo(String s) {}
                             }""", false, "java.lang.String");
  }

  @Test
  public void testOverloadedDeclaredMethod() {
    doTestOverloadedMethod("foo",
                           """
                             class Overloaded {
                               public void foo() {}
                               public void foo(String s) {}
                             }""", true, "java.lang.String");
  }

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(classText);

    PsiMember member = doTestImpl(name,
                                  "class Main {" +
                                  "  void main() {" +
                                  "    Overloaded.class.get" + (isDeclared?"Declared":"") + "Method(\"<caret>"+name+"\", String.class);" +
                                  "  }" +
                                  "}");
    ReadAction.runBlocking(() -> {
      assertTrue(member instanceof PsiMethod, "Target is a method");
      PsiMethod method = (PsiMethod)member;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      assertEquals(expectedParameterTypes.length, parameters.length, "Parameter count");
      for (int i = 0; i < expectedParameterTypes.length; i++) {
        assertEquals(expectedParameterTypes[i], parameters[0].getType().getCanonicalText(), "Parameter type " + i);
      }
    });
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
    return ReadAction.computeBlocking(() -> {
      assertEquals(name, reference.getCanonicalText(), "Reference text");
      PsiElement resolved = reference.resolve();
      assertNotNull(resolved, "Reference is not resolved: " + reference.getCanonicalText());
      assertTrue(resolved instanceof PsiMember, "Target is a member");
      PsiMember member = (PsiMember)resolved;
      assertEquals(name, member.getName(), "Target name");
      return member;
    });
  }

  private void doNegativeTest(String name,
                              @MagicConstant(stringValues = {FIELD, METHOD, DF, DM}) String type) {
    PsiReference reference = getReference(getMainClassText(name, type));
    ReadAction.runBlocking(() -> {
      assertEquals(name, reference.getCanonicalText(), "Reference text");
      PsiElement resolved = reference.resolve();
      assertNull(resolved, "Reference shouldn't resolve: " + reference.getCanonicalText());
    });
  }

  @NotNull
  private PsiReference getReference(String mainClassText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    return ReadAction.computeBlocking(() -> {
      int offset = myFixture.getCaretOffset();
      PsiReference reference = myFixture.getFile().findReferenceAt(offset);
      assertNotNull(reference, "No reference at the caret");
      return reference;
    });
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
