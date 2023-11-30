// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeStyle;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;

public class JavaCodeStyleManagerTest extends LightJavaCodeInsightFixtureTestCase {

  public void testSuggestSemanticNameEnumConstantVariable() {
    @Language("JAVA") String source = """
      public class Test {
          public static void main(String[] args) {
              check("Some", (Month.J<caret>ANUARY), 17);
          }
          
          private enum Month {
             JANUARY
          }
      }
      """;
    checkSuggestedNames(source, VariableKind.LOCAL_VARIABLE, "january", "month");
    checkSuggestedNames(source, VariableKind.PARAMETER, "month", "january");
  }

  public void testSuggestSemanticNameStringConstant() {
    checkSuggestedNames("""
                          public class Test {
                              public static void main(String[] args) {
                                  check("Som<caret>e", (Month.JANUARY), 17);
                              }
                              
                              private enum Month {
                                 JANUARY
                              }
                          }
                          """, VariableKind.LOCAL_VARIABLE, "some", "s", "string");
  }

  public void testSuggestSemanticNameVariableNameParameter() {
    checkSuggestedNames("""
                          public class Test {
                              public static void main(String[] args) {
                                  Month month2 = Month.JANUARY;
                                  check("Some", m<caret>onth2, 17);
                              }
                              
                              private enum Month {
                                 JANUARY
                              }
                          }
                          """, VariableKind.PARAMETER, "month2", "month");
  }

  public void testShortNameForBoxedTypes() {
    //noinspection ALL
    checkSuggestedNames("""
                          class X {
                            void x() {
                              Integer.<caret>valueOf(1);
                            }
                          }
                          """, VariableKind.LOCAL_VARIABLE, "valueOf", "value", "i", "integer");
  }

  public void testHashCode() {
    checkSuggestedNames("""
                          class X {
                            void x(String s) {
                              s.<caret>hashCode()
                            }
                          }
                          """, VariableKind.LOCAL_VARIABLE, "hashCode", "code", "hash", "i");
  }

  public void testCompareTo() {
    checkSuggestedNames("""
                          class X {
                            void x(String s) {
                              s.<caret>compareTo("");
                            }
                          }
                          """, VariableKind.PARAMETER, "compareTo", "compare", "i");
  }

  public void testNameSuggestionFromLiteralArgument() {
    checkSuggestedNames("class A {{new Str<caret>ing(\"string with spaces\")}}",
                        VariableKind.LOCAL_VARIABLE,
                        "stringWithSpaces", "string_with_spaces", "withSpaces", "with_spaces", "spaces", "string", "s");
  }

  public void testWordByPreposition() {
    checkSuggestedNames("class A {{getParent<caret>OfType()} String getParentOfType() {return null;}}",
                        VariableKind.LOCAL_VARIABLE,
                        "getParentOfType", "parentOfType", "ofType", "type", "parent", "s", "string");
  }

  public void testNameByAssignmentContext() {
    checkSuggestedNames("class A {{String bar = \"<caret>\";}}",
                        VariableKind.PARAMETER,
                        "bar", "s", "string");
  }
  
  public void testInvalidName() {
    SuggestedNameInfo info = JavaCodeStyleManager.getInstance(getProject())
      .suggestVariableName(VariableKind.LOCAL_VARIABLE, "hello?world", null, PsiTypes.intType(), true);
    assertEquals(List.of("i"), List.of(info.names));
  }

  private void checkSuggestedNames(@NotNull String code, @NotNull VariableKind kind, String @NotNull ... expected) {
    assert EditorTestUtil.getCaretPosition(code) >= 0 : "No <caret> specified";
    PsiFile file = myFixture.configureByText("Test.java", code);
    PsiElement element = PsiUtilCore.getElementAtOffset(file, myFixture.getCaretOffset());
    PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiExpression.class, false);
    SuggestedNameInfo nameInfo = JavaCodeStyleManager.getInstance(getProject())
      .suggestVariableName(kind, null, expression, expression.getType());
    Assert.assertArrayEquals('"' + StringUtil.join(nameInfo.names, "\", \"") + '"', expected, nameInfo.names);
  }
}
