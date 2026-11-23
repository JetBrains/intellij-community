// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Contract;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The nullability inference tests that each subclass runs with its own inference.
 */
public abstract class NullityInferenceFromSourceTestCase {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  protected final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testReturnStringLiteral() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String foo() { return \"a\"; }")));
  }

  @Test
  public void testReturnNull() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(parse("String foo() { return null; }")));
  }

  @Test
  public void testPrimitiveReturnType() {
    assertEquals(Nullability.UNKNOWN, inferNullability(parse("int foo() { return \"z\"; }")));
  }

  @Test
  public void testDelegation() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String foo() { return bar(); }; String bar() { return \"z\"; }; ")));
  }

  @Test
  public void testSameDelegateMethodInvokedTwice() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("""

                                          String foo() {\s
                                            if (equals(2)) return bar();
                                            if (equals(3)) return bar();
                                            return "abc";\s
                                          }
                                          String bar() { return "z"; }
                                          """)));
  }

  @Test
  public void testUnknownWinsOverASingleDelegate() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("""

                                          String foo() {\s
                                            if (equals(3)) return bar();
                                            return smth;\s
                                          }
                                          String bar() { return "z"; }
                                          """)));
  }

  @Test
  public void testIfBranchReturnsNull() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(parse("String bar() { if (equals(2)) return null; return \"a\"; }; ")));
  }

  @Test
  public void testTernaryBranchReturnsNull() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(parse("String bar() { return equals(2) ? \"a\" : equals(3) ? null : \"a\"; }; ")));
  }

  @Test
  public void testTernaryBranchNotnull() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String bar() { return equals(2) ? \"a\" : equals(3) ? \"b\" : \"c\"; }; ")));
  }

  @Test
  public void testTypeCastNotnull() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String foo() { return (String)bar(); }; Object bar() { return \"a\"; }; ")));
  }

  @Test
  public void testStringConcatenation() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String bar(String s1, String s2) { return s1 + s2; }; ")));
  }

  @Test
  public void testDelegationToNullableMeansNothing() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(
                   parse("String foo() { return bar(\"2\"); }; String bar(String s) { if (s != \"2\") return null; return \"a\"; }; ")));
  }

  @Test
  public void testReturnBoxedBooleanConstant() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("Object foo() { return true; }")));
  }

  @Test
  public void testReturnBoxedBooleanValue() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("Object foo(Object o) { return o == null; }")));
  }

  @Test
  public void testReturnBoxedInteger() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("Object foo() { return 1; }")));
  }

  @Test
  public void testNullInsideLambda() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("Object foo() { return () -> { return null; }; }")));
  }

  @Test
  public void testInPresenceOfExplicitNullContract() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("""
                                          @Contract("null->null")
                                          Object foo(Object o) { if (o == null) return null; return 2; }
                                          """)));
  }

  @Test
  public void testInPresenceOfInferredNullContract() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("""
                                          Object foo(Object o) { if (o == null) return null; return 2; }
                                          """)));
  }

  @Test
  public void testInPresenceOfFailContract() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("""
                                          @Contract("null->fail")
                                          Object foo(Object o) { if (o == null) return o.hashCode(); return 2; }
                                          """)));
  }

  @Test
  public void testReturningInstanceofEdVariableViaStatement() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("""
                                          String foo(Object o) {\s
                                            if (o instanceof String) return ((String)o);\s
                                            return "abc";\s
                                          }""")));
  }

  @Test
  public void testReturningInstanceofEdVariableViaTernary() {
    assertEquals(Nullability.NOT_NULL,

                 inferNullability(parse("String foo(Object o) { return o instanceof String ? (String)o : \"abc\"; }")));
  }

  @Test
  public void testSystemExit() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("String foo(Object obj) {try {return bar();} catch(Exception ex) {System.exit(1);return null;}}"))
    );
  }

  @Test
  public void testSystemExit2() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(
                   parse("String foo(boolean b) {" +
                         "if(b) return null;" +
                         "try {x();} " +
                         "catch(Exception ex) {System.exit(1);return null;}" +
                         "return \"xyz\".trim();" +
                         "}")));
  }

  @Test
  public void testDeclareAndFill() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(
                   parse("""
                           java.util.List<String> foo() {
                             java.util.List<String> x = new java.util.ArrayList<>();
                             for(int i=0; i<10; i++) x.add("foo");
                             return x;
                           }""")));
  }

  @Test
  public void testAssignAndFill() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(
                   parse("""
                           java.util.List<String> foo() {
                             java.util.List<String> x;
                             x = new java.util.ArrayList<>();
                             for(int i=0; i<10; i++) x.add("foo");
                             return x;
                           }""")));
  }

  @Test
  public void testDereference() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(
                   parse("""
                           String foo() {
                             String x = getUnknown();
                             System.out.println(x.trim());
                             return x;
                           }""")));
  }

  @Test
  public void testReassignedInIf() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("""
                                          String foo() {
                                            String res = "start";
                                            if(foo) {
                                              res = getBar();
                                            }
                                            return res;
                                          }""")));
  }

  @Test
  public void testReassignedInBothBranches() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(parse("""
                                          String foo() {
                                            String res;
                                            if(foo) {
                                              res = getBar();
                                            } else {
                                              res = null;
                                            }
                                            return res;
                                          }""")));
  }

  @Test
  public void testReassignedInSwitch() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("""
                                          String foo(int foo) {
                                            String res = "bar";
                                            switch(foo) {
                                            case 1:res = getSomething();
                                            case 2:return res;
                                            }
                                            return "";
                                          }""")));
  }

  @Test
  public void testNullCheckWithReturn() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("""
                                          String foo() {
                                            String res = getUnknown();
                                            if (res == null) return "foo";
                                            return res;
                                          }""")));
  }

  @Test
  public void testIfNullReassign() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("""
                                          String test() {
                                            String result = getFoo();
                                            if (result != null && result.isEmpty()) {
                                              result = null;
                                            }
                                            if (result == null) {
                                              result = getBar();
                                            }
                                            return result;
                                          }""")));
  }

  @Test
  public void testNestedIfs() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("""
                                          String test() {
                                            String result = "foo";
                                            if (bar) {
                                              if(baz) {
                                                System.out.println(result.trim());
                                              }
                                            }
                                            return result;
                                          }""")));
  }

  @Test
  public void testNullOrEmpty() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(parse("""
                                          String test() {
                                            String p = isFoo() ? null : getFoo();
                                            if (p == null || p.isEmpty()) return p;
                                            return "foo";
                                          }""")));
  }

  @Test
  public void testSetToNullInIfBranch() {
    assertNotEquals(Nullability.NOT_NULL,
                    inferNullability(parse("""
                                             String test(String r) {
                                               String p;
                                               if(r == null) {
                                                 p = null;
                                               } else {
                                                 p = " foo ".trim();
                                               }
                                               return p;
                                             }""")));
  }

  @Test
  public void testNullSwitchExpression() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(
                   parse("""
                           private static String test(Object t) {
                               return switch (t) {
                                   case String a -> {
                                       if (t.hashCode() == 1) {
                                           yield "t1";
                                       } else {
                                           yield null;
                                       }
                                   }
                                   case final Integer l -> "t2";
                                   default -> throw new IllegalStateException("Unexpected value: " + t);
                               };
                           }""")));
  }

  @Test
  public void testNotNullSwitchExpression() {
    assertEquals(Nullability.NOT_NULL,
                    inferNullability(
                      parse("""
                              private static String test(Object t) {
                                  return switch (t) {
                                      case String a -> "notnull";
                                      case final Integer l -> throw new UnsupportedOperationException();
                                      default -> throw new IllegalStateException("Unexpected value: " + t);
                                  };
                              }""")));
  }

  @Test
  public void testNullRuleSwitchExpression() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(
                   parse("""
                           private static String test(Object t) {
                               return switch (t) {
                                   case String a -> null;
                                   case final Integer l -> throw new UnsupportedOperationException();
                                   default -> "1";
                               };
                           }""")));
  }

  @Test
  public void testNullOldSwitchExpression() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(
                   parse("""
                           private static String test(Object t) {
                               return switch (t) {
                                   case String a:
                                       if (t.hashCode() == 1) {
                                           yield  "t1";
                                       } else {
                                           yield null;
                                       }
                                   case final Integer l:
                                       yield "t2";
                                   default:
                                       throw new IllegalArgumentException();
                               };
                           }""")));
  }

  @Test
  public void testNotNullOldSwitchExpression() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(
                   parse("""
                           private static String test(Object t) {
                               return switch (t) {
                                   case String a:
                                       if (t.hashCode() == 1) {
                                           yield  "t1";
                                       } else {
                                           yield "notnull";
                                       }
                                   case final Integer l:
                                       yield "t2";
                                   default:
                                       throw new IllegalArgumentException();
                               };
                           }""")));
  }


  protected abstract Nullability inferNullability(PsiMethod method);

  protected PsiMethod parse(String method) {
    PsiClass psiClass = codeInsightFixture.get().addClass("import org.jetbrains.annotations.*; final class Foo { " + method + " }");
    return ReadAction.computeBlocking(() -> psiClass.getMethods()[0]);
  }

  @TestApplication
  public static class LightInferenceTest extends NullityInferenceFromSourceTestCase {
    @Override
    public Nullability inferNullability(PsiMethod method) {
      final PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)method.getContainingFile());
      assertFalse(file.isContentsLoaded());
      Nullability result = ReadAction.computeBlocking(() -> NullableNotNullManager.getNullability(method));
      assertFalse(file.isContentsLoaded());

      // check inference works same on both light and real AST
      Project project = codeInsightFixture.get().getProject();
      WriteCommandAction.runWriteCommandAction(project,
                                               () -> {
                                                 file.getViewProvider().getDocument().insertString(0, " ");
                                                 PsiDocumentManager.getInstance(project).commitAllDocuments();
                                               });
      ReadAction.runBlocking(() -> {
        assertNotNull(method.getNode());
        assertEquals(result, JavaSourceInference.inferNullability((PsiMethodImpl)method));
      });
      return result;
    }

    @Test
    public void testSkipWhenErrors() {
      assertEquals(Nullability.UNKNOWN, inferNullability(parse("String foo() { if(); return 2; } ")));
    }

    @Test
    public void testNoNullableAnnotationInPresenceOfInferredNullContract() {
      PsiMethod method = parse("Object foo(Object o) { if (o == null) return null; return 2; }");
      ReadAction.runBlocking(() -> {
        PsiAnnotation[] annos = InferredAnnotationsManager.getInstance(codeInsightFixture.get().getProject()).findInferredAnnotations(method);
        assertEquals(Arrays.asList(Contract.class.getName()), Arrays.stream(annos).map(t -> t.getQualifiedName()).toList());
      });
    }
  }

  @TestApplication
  public static class DfaInferenceTest extends NullityInferenceFromSourceTestCase {
    @Override
    public Nullability inferNullability(PsiMethod method) {
      return ReadAction.computeBlocking(() -> DfaUtil.inferMethodNullability(method));
    }
  }
}
