package com.intellij.java.codeInsight;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.idea.IJIgnore;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MakeInferredAnnotationExplicitTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  @IJIgnore(issue = "AT-4013")
  public void testContractAndNotNull() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Foo {
        static String f<caret>oo() {
          return "s";
        }
      }""");
    IntentionAction intention = myFixture.findSingleIntention("Insert '@Contract(pure = true) @NotNull'");
    assertTrue(intention.startInWriteAction());
    myFixture.launchAction(intention);
    myFixture.checkResult("""
      import org.jetbrains.annotations.Contract;
      import org.jetbrains.annotations.NotNull;

      class Foo {
        @Contract(pure = true)
        static @NotNull String f<caret>oo() {
          return "s";
        }
      }""");
  }

  @Test
  public void testParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Foo {
        static String foo(String s<caret>tr) {
          return str.trim();
        }
      }""");
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@NotNull'"));
    myFixture.checkResult("""
      import org.jetbrains.annotations.NotNull;

      class Foo {
        static String foo(@NotNull String str) {
          return str.trim();
        }
      }""");
  }

  @Test
  public void testCustomNotNull() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public @interface MyNotNull {}");
    NullableNotNullManager.getInstance(myFixture.getProject()).setNotNulls("foo.MyNotNull");

    myFixture.configureByText("a.java", """
      class Foo {
        static String f<caret>oo() {
          unknown();
          return "s";
        }
      }""");
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@MyNotNull'"));
    myFixture.checkResult("""
                            import foo.MyNotNull;
                                  
                            class Foo {
                              @MyNotNull
                              static String f<caret>oo() {
                                unknown();
                                return "s";
                              }
                            }""");
  }

  @Test
  public void testTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; import java.lang.annotation.*;@Target(ElementType.TYPE_USE)public @interface MyNotNull {}");
    NullableNotNullManager.getInstance(myFixture.getProject()).setNotNulls("foo.MyNotNull");
    myFixture.configureByText("a.java", """
      class Foo {
        static void foo(String[] ar<caret>ray) {
          System.out.println(array.length);
        }
      }""");
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@MyNotNull'"));
    myFixture.checkResult("""
                            import foo.MyNotNull;
                                
                            class Foo {
                              static void foo(String @MyNotNull [] array) {
                                System.out.println(array.length);
                              }
                            }""");
  }

  @Test
  public void testTypeUseQualifiedType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; import java.lang.annotation.*;@Target(ElementType.TYPE_USE)public @interface MyNotNull {}");
    NullableNotNullManager.getInstance(myFixture.getProject()).setNotNulls("foo.MyNotNull");
    myFixture.configureByText("a.java", """
      import org.jetbrains.annotations.Contract;
      import foo.MyNotNull;

      class Sample {
          class Inner {
              String a;

              Inner(String a) {
                  this.a = a;
              }
          }

          class Usage {
              @Contract(value = " -> new", pure = true)
              private Sample.@MyNotNull Inner f<caret>oo() {
                  String a = "a";
                 return new Inner(a);
              }
          }
      }""");
    assert myFixture.filterAvailableIntentions("Insert '@MyNotNull'").isEmpty();
  }

  @Test
  public void testNoUnmodifiableInferenceWhenTypeAnnotated() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
       import java.util.List;
       import org.jetbrains.annotations.NotNull;
       import org.jetbrains.annotations.Unmodifiable;

       public final class Example<A>
       {
           public <T> @Unmodifiable @NotNull List<T> <caret>genericInstanceMethod(T value)
           {
               return List.of(value);
           }
       }""");
    List<IntentionAction> actions =
      myFixture.filterAvailableIntentions("Insert '@Contract(value = \"_ -> new\", pure = true) @Unmodifiable'");
    assertThat(actions).isNullOrEmpty();
  }

  @AfterEach
  void tearDown() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    NullableNotNullManager.getInstance(myFixture.getProject()).setNotNulls(ArrayUtil.EMPTY_STRING_ARRAY);
  }
}
