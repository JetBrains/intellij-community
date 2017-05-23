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
package com.intellij.java.codeInsight.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class JavaReflectionNavigationTest extends LightCodeInsightFixtureTestCase {

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


  private void doTest(String name,
                      @MagicConstant(stringValues = {FIELD, METHOD, DF, DM}) String type) {
    doTestImpl(name, getMainClassText(name, type));
  }

  private void doCustomTest(String name,
                            @NotNull @NonNls @Language("JAVA") String mainClassText) {
    doTestImpl(name, mainClassText);
  }

  private void doTestImpl(String name, String mainClassText) {
    PsiReference reference = getReference(mainClassText);
    assertEquals("Reference text", name, reference.getCanonicalText());
    PsiElement resolved = reference.resolve();
    assertNotNull("Reference is not resolved: " + reference.getCanonicalText(), resolved);
    assertTrue("Target is a member", resolved instanceof PsiMember);
    PsiMember member = (PsiMember)resolved;
    assertEquals("Target name", name, member.getName());
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
    myFixture.addClass("class Parent {\n" +
                       "  public int field3;\n" +
                       "  int field4;\n" +
                       "  public void method3(int n) {}\n" +
                       "  void method4(int n) {}\n" +
                       "}");
    myFixture.addClass("class Test extends Parent {\n" +
                       "  public int field;\n" +
                       "  int field2;\n" +
                       "  public void method() {}\n" +
                       "  void method2(int n) {}\n" +
                       "}");
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
