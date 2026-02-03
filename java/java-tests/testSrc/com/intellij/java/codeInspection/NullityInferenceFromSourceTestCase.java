// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static org.junit.Assert.assertNotEquals;

public abstract class NullityInferenceFromSourceTestCase extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  public void testReturnStringLiteral() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String foo() { return \"a\"; }")));
  }

  public void testReturnNull() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(parse("String foo() { return null; }")));
  }

  public void testPrimitiveReturnType() {
    assertEquals(Nullability.UNKNOWN, inferNullability(parse("int foo() { return \"z\"; }")));
  }

  public void testDelegation() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String foo() { return bar(); }; String bar() { return \"z\"; }; ")));
  }

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

  public void testIfBranchReturnsNull() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(parse("String bar() { if (equals(2)) return null; return \"a\"; }; ")));
  }

  public void testTernaryBranchReturnsNull() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(parse("String bar() { return equals(2) ? \"a\" : equals(3) ? null : \"a\"; }; ")));
  }

  public void testTernaryBranchNotnull() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String bar() { return equals(2) ? \"a\" : equals(3) ? \"b\" : \"c\"; }; ")));
  }

  public void testTypeCastNotnull() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String foo() { return (String)bar(); }; Object bar() { return \"a\"; }; ")));
  }

  public void testStringConcatenation() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("String bar(String s1, String s2) { return s1 + s2; }; ")));
  }

  public void testDelegationToNullableMeansNothing() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(
                   parse("String foo() { return bar(\"2\"); }; String bar(String s) { if (s != \"2\") return null; return \"a\"; }; ")));
  }

  public void testReturnBoxedBooleanConstant() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("Object foo() { return true; }")));
  }

  public void testReturnBoxedBooleanValue() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("Object foo(Object o) { return o == null; }")));
  }

  public void testReturnBoxedInteger() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("Object foo() { return 1; }")));
  }

  public void testNullInsideLambda() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("Object foo() { return () -> { return null; }; }")));
  }

  public void testInPresenceOfExplicitNullContract() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("""
                                          @Contract("null->null")
                                          Object foo(Object o) { if (o == null) return null; return 2; }
                                          """)));
  }

  public void testInPresenceOfInferredNullContract() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("""
                                          Object foo(Object o) { if (o == null) return null; return 2; }
                                          """)));
  }

  public void testInPresenceOfFailContract() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("""
                                          @Contract("null->fail")
                                          Object foo(Object o) { if (o == null) return o.hashCode(); return 2; }
                                          """)));
  }

  public void testReturningInstanceofEdVariableViaStatement() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("""
                                          String foo(Object o) {\s
                                            if (o instanceof String) return ((String)o);\s
                                            return "abc";\s
                                          }""")));
  }

  public void testReturningInstanceofEdVariableViaTernary() {
    assertEquals(Nullability.NOT_NULL,

                 inferNullability(parse("String foo(Object o) { return o instanceof String ? (String)o : \"abc\"; }")));
  }

  public void testSystemExit() {
    assertEquals(Nullability.UNKNOWN,
                 inferNullability(parse("String foo(Object obj) {try {return bar();} catch(Exception ex) {System.exit(1);return null;}}"))
    );
  }

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

  public void testNullCheckWithReturn() {
    assertEquals(Nullability.NOT_NULL,
                 inferNullability(parse("""
                                          String foo() {
                                            String res = getUnknown();
                                            if (res == null) return "foo";
                                            return res;
                                          }""")));
  }

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

  public void testNullOrEmpty() {
    assertEquals(Nullability.NULLABLE,
                 inferNullability(parse("""
                                          String test() {
                                            String p = isFoo() ? null : getFoo();
                                            if (p == null || p.isEmpty()) return p;
                                            return "foo";
                                          }""")));
  }

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
    return myFixture.addClass("import org.jetbrains.annotations.*; final class Foo { " + method + " }").getMethods()[0];
  }

  public static class LightInferenceTest extends NullityInferenceFromSourceTestCase {
    @Override
    public Nullability inferNullability(PsiMethod method) {
      final PsiFileImpl file = (PsiFileImpl)method.getContainingFile();
      assertFalse(file.isContentsLoaded());
      Nullability result = NullableNotNullManager.getNullability(method);
      assertFalse(file.isContentsLoaded());

      // check inference works same on both light and real AST
      WriteCommandAction.runWriteCommandAction(getProject(),
                                               () -> {
                                                 file.getViewProvider().getDocument().insertString(0, " ");
                                                 PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
                                               });
      assertNotNull(method.getNode());
      assertEquals(result, JavaSourceInference.inferNullability((PsiMethodImpl)method));
      return result;
    }

    public void testSkipWhenErrors() {
      assertEquals(Nullability.UNKNOWN, inferNullability(parse("String foo() { if(); return 2; } ")));
    }

    public void testNoNullableAnnotationInPresenceOfInferredNullContract() {
      PsiMethod method = parse("Object foo(Object o) { if (o == null) return null; return 2; }");
      PsiAnnotation[] annos = InferredAnnotationsManager.getInstance(getProject()).findInferredAnnotations(method);
      assertEquals(Arrays.asList(Contract.class.getName()), Arrays.stream(annos).map(t -> t.getQualifiedName()).toList());
    }
  }

  public static class DfaInferenceTest extends NullityInferenceFromSourceTestCase {
    @Override
    public Nullability inferNullability(PsiMethod method) {
      return DfaUtil.inferMethodNullability(method);
    }
  }
}
