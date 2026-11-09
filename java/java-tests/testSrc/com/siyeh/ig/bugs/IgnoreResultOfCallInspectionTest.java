// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
@SuppressWarnings("ALL")
public class IgnoreResultOfCallInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_15);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new IgnoreResultOfCallInspection(), getEnvironmentClasses());

  private String[] getEnvironmentClasses() {
    return new String[]{
      """
        package java.util.regex;
        
        public class Pattern {
          public static Pattern compile(String regex) {return null;}
        
          public Matcher matcher(CharSequence input) {return null;}
        }
        """,
      """
        package java.util.regex;
        
        public class Matcher {
          public boolean find() {return true;}
        }
        """,
      """
        package javax.annotation;
        
        import java.lang.annotation.Documented;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        
        import javax.annotation.meta.When;
        
        @Documented
        @Target({ ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.PACKAGE })
        @Retention(RetentionPolicy.RUNTIME)
        public @interface CheckReturnValue {
            When when() default When.ALWAYS;
        }
        """,
      """
        package com.google.errorprone.annotations;
        import static java.lang.annotation.ElementType.CONSTRUCTOR;
        import static java.lang.annotation.ElementType.METHOD;
        import static java.lang.annotation.ElementType.PACKAGE;
        import static java.lang.annotation.ElementType.TYPE;
        import static java.lang.annotation.RetentionPolicy.RUNTIME;
        import java.lang.annotation.Documented;
        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;
        @Documented
        @Target({METHOD,CONSTRUCTOR,TYPE,PACKAGE})
        @Retention(value=RUNTIME)
        public @interface CheckReturnValue {}
        """,
      """
        package org.jetbrains.annotations;
        import java.lang.annotation.Documented;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Documented
        @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.PACKAGE})
        public @interface CheckReturnValue {}
        """,
      """
        package org.springframework.lang;
        import java.lang.annotation.Documented;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Documented
        @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.PACKAGE})
        public @interface CheckReturnValue {}
        """,
      """
        package org.assertj.core.util;
        import static java.lang.annotation.ElementType.CONSTRUCTOR;
        import static java.lang.annotation.ElementType.METHOD;
        import static java.lang.annotation.ElementType.PACKAGE;
        import static java.lang.annotation.ElementType.TYPE;
        import static java.lang.annotation.RetentionPolicy.RUNTIME;
        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;
        @Target({CONSTRUCTOR,METHOD,PACKAGE,TYPE})
        @Retention(value=CLASS)
        public @interface CheckReturnValue {}
        """,
      """
        package a;
         public @interface CheckReturnValue {}
        """,
      """
        package com.google.errorprone.annotations;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Target({ElementType.METHOD, ElementType.TYPE})
        @Retention(RetentionPolicy.CLASS)
        public @interface CanIgnoreReturnValue {}
        """,
      """
        package org.assertj.core.util;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Target({CONSTRUCTOR,METHOD,PACKAGE,TYPE})
        @Retention(value=CLASS)
        public @interface CanIgnoreReturnValue{}
        """,
      """
        package org.assertj.core.annotation;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Target({CONSTRUCTOR,METHOD,PACKAGE,TYPE})
        public @interface CanIgnoreReturnValue{}
        """,
      """
        package org.apache.commons.lang3;
        public class Validate {
          public native static <T> T notNull(T object);
        }""",
      """
        package org.junit.jupiter.api.function;
        public interface Executable {
          void execute() throws Throwable;
        }""",
      """
        package org.junit.jupiter.api;
        import org.junit.jupiter.api.function.Executable;
        import java.util.function.Supplier;
        public class Assertions {
        public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {
            return null;
          }
        public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable, Supplier<String> messageSupplier) {
            return null;
          }
        }""",
      """
        package org.assertj.core.api.ThrowableAssert;
        public interface ThrowingCallable {
          void call() throws Throwable;
        }""",
      """
        package org.assertj.core.api;
        import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
        public class Assertions {
          public static Object assertThatThrownBy(ThrowingCallable shouldRaiseThrowable) {
            return null;
          }
        }""",
      """
       package org.mockito;
       public interface MockedStatic<T> {
        Object when(Verification v);
        interface Verification {
         void apply();
        }
       }""",
      """
      package java.nio;
      public abstract class ByteBuffer{
        abstract boolean hasRemaining();
      }
      """,
      """
      package java.nio.channels;
      import java.nio.ByteBuffer;
      import java.io.IOException;
      public abstract class FileChannel{
        public abstract int write(ByteBuffer src) throws IOException;
      }
      """};
  }

  @Test
  public void testJetbrainsAnnotationOnClass() {
    inspectionFixture.get().doTest("""
             import org.jetbrains.annotations.CheckReturnValue;
             
             @CheckReturnValue
             class Test {
             
                 String foo() {
                    return "foo";
                 }
             
                 public static void main(String[] args){
                   new Test()./*Result of 'Test.foo()' is ignored*/foo/**/();
                 }
             }
             """);
  }

  @Test
  public void testSpringAnnotationOnClass() {
    inspectionFixture.get().doTest("""
             import org.springframework.lang.CheckReturnValue;
             
             @CheckReturnValue
             class Test {
             
                 String foo() {
                    return "foo";
                 }
             
                 public static void main(String[] args){
                   new Test()./*Result of 'Test.foo()' is ignored*/foo/**/();
                 }
             }
             """);
  }

  @Test
  public void testCanIgnoreReturnValue() {
    inspectionFixture.get().doTest("""
             import com.google.errorprone.annotations.CanIgnoreReturnValue;
             import javax.annotation.CheckReturnValue;

             @CheckReturnValue
             class Test {
               int lookAtMe() { return 1; }

               @CanIgnoreReturnValue
               int ignoreMe() { return 2; }

               void run() {
                 /*Result of 'Test.lookAtMe()' is ignored*/lookAtMe/**/(); // Bad!  This line should produce a warning.
                 ignoreMe(); // OK.  This line should *not* produce a warning.
               }
             }""");
  }

  @Test
  public void testCanIgnoreReturnValue2() {
    inspectionFixture.get().doTest(
      """
        class TestClass {

            public void m() {
                var javax = new Javax();
                javax./*Result of 'Javax.unannotated()' is ignored*/unannotated/**/();
                javax.assertJ();
                javax.errorProne();

                var errorProne = new ErrorProne();
                errorProne./*Result of 'ErrorProne.unannotated()' is ignored*/unannotated/**/();
                errorProne.assertJ();
                errorProne.errorProne();

                var assertJ = new AssertJ();
                assertJ./*Result of 'AssertJ.unannotated()' is ignored*/unannotated/**/();
                assertJ.assertJ();
                assertJ.errorProne();

            }

            @javax.annotation.CheckReturnValue
            public static class Javax {
                int unannotated() {
                    return 3;
                }

                @org.assertj.core.util.CanIgnoreReturnValue
                int assertJ() {
                    return 3;
                }

                @com.google.errorprone.annotations.CanIgnoreReturnValue
                int errorProne() {
                    return 3;
                }
            }

            @com.google.errorprone.annotations.CheckReturnValue
            public static class ErrorProne {
                int unannotated() {
                    return 3;
                }

                @org.assertj.core.util.CanIgnoreReturnValue
                int assertJ() {
                    return 3;
                }

                @com.google.errorprone.annotations.CanIgnoreReturnValue
                int errorProne() {
                    return 3;
                }
            }

            @org.assertj.core.util.CheckReturnValue
            public static class AssertJ {
                int unannotated() {
                    return 3;
                }

                @org.assertj.core.util.CanIgnoreReturnValue
                int assertJ() {
                    return 3;
                }

                @com.google.errorprone.annotations.CanIgnoreReturnValue
                int errorProne() {
                    return 3;
                }
            }
        }
        """);
  }

  @Test
  public void testCanIgnoreReturnValue3() {
    inspectionFixture.get().doTest(
      """
        import com.google.errorprone.annotations.CanIgnoreReturnValue;
        import com.google.errorprone.annotations.CheckReturnValue;
        @CheckReturnValue
        class Test {
            static int lookAtMe() { return 1; }
            @CanIgnoreReturnValue
            static int ignoreMe() { return 2; }
            void run() {
                /*Result of 'Test.lookAtMe()' is ignored*/lookAtMe/**/(); // <- inspection\s
                ignoreMe(); // <- also inspection
            }
        }
        """);
  }

  @Test
  public void testCanIgnoreReturnValue4() {
    inspectionFixture.get().doTest("""
             import org.assertj.core.annotation.CanIgnoreReturnValue;
             import javax.annotation.CheckReturnValue;

             @CheckReturnValue
             class Test {
               int lookAtMe() { return 1; }

               @CanIgnoreReturnValue
               int ignoreMe() { return 2; }

               void run() {
                 /*Result of 'Test.lookAtMe()' is ignored*/lookAtMe/**/(); // Bad!  This line should produce a warning.
                 ignoreMe(); // OK.  This line should *not* produce a warning.
               }
             }""");
  }



  @Test
  public void testCustomCheckReturnValue() {
    inspectionFixture.get().doTest("""
             import a.CheckReturnValue;

             class Test {
               @CheckReturnValue
               int lookAtMe() { return 1; }

               void run() {
                 /*Result of 'Test.lookAtMe()' is ignored*/lookAtMe/**/();
               }
             }""");
  }

  @Test
  public void testObjectMethods() {
    inspectionFixture.get().doTest("""
             class C {
               void foo(Object o, String s) {
                 o./*Result of 'Object.equals()' is ignored*/equals/**/(s);
               }
             }
             """);
  }

  @Test
  public void testMatcher() {
    inspectionFixture.get().doTest("""
             class C {
               void matcher() {
                 final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("baaaa");
                 final java.util.regex.Matcher matcher = pattern.matcher("babaaaaaaaa");
                 matcher./*Result of 'Matcher.find()' is ignored*/find/**/();
                 matcher.notifyAll();
               }
             }
             """);
  }

  @Test
  public void testReader() {
    inspectionFixture.get().doTest("import java.io.Reader;" +
           "import java.io.IOException;" +
           "class U {" +
           "  void m(Reader r) throws IOException {" +
           "    r./*Result of 'Reader.read()' is ignored*/read/**/();" +
           "  }" +
           "}");
  }

  @Test
  public void testJSR305Annotation() {
    inspectionFixture.get().doTest("import javax.annotation.CheckReturnValue;" +
           "class A {" +
           "  @CheckReturnValue" +
           "  static Object a() {" +
           "    return null;" +
           "  }" +
           "  void b() {" +
           "    /*Result of 'A.a()' is ignored*/a/**/();" +
           "  }" +
           "}");
  }

  @Test
  public void testRandomGetter() {
    inspectionFixture.get().doTest("class A {" +
           "  private String name;" +
           "  public String getName() {" +
           "    return name;" +
           "  }" +
           "  void m() {" +
           "    /*Result of 'A.getName()' is ignored*/getName/**/();" +
           "  }" +
           "}");
  }

  @Test
  public void testJSR305Annotation2() {
    inspectionFixture.get().doTest("import javax.annotation.CheckReturnValue;" +
           "@CheckReturnValue " +
           "class A {" +
           "  static Object a() {" +
           "    return null;" +
           "  }" +
           "  void b() {" +
           "    /*Result of 'A.a()' is ignored*/a/**/();" +
           "  }" +
           "}");
  }

  @Test
  public void testJSR305Annotation3() {
    inspectionFixture.get().doTest("import javax.annotation.CheckReturnValue;" +
           "@CheckReturnValue " +
           "class Parent {" +
           "  class A {" +
           "    Object a() {" +
           "      return null;" +
           "    }" +
           "    void b() {" +
           "      /*Result of 'A.a()' is ignored*/a/**/();" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testInference() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    Registry.get("ide.ignore.call.result.inspection.honor.inferred.pure").setValue(true, myFixture.getTestRootDisposable());
    inspectionFixture.get().doTest(
      """
        class Test {
          private static <T> T checkNotNull(T reference) {
            if (reference == null) {
              throw new NullPointerException();
            }
            return reference;
          }
         \s
          private static String twice(String s) {
            return s+s;
          }
         \s
          void test(String string) {
            checkNotNull(string);
            /*Result of 'Test.twice()' is ignored*/twice/**/("foo");
            /*Result of 'Test.twice()' is ignored*/twice/**/("bar");
          }
        }
        """);
  }

  @Test
  public void testPureMethod() {
    inspectionFixture.get().doTest(
      """
        import org.jetbrains.annotations.Contract;

        class Util {
          @Contract(pure=true)
          static Object util() { return null; }
        }

        class C {
          static {
            Util./*Result of 'Util.util()' is ignored*/util/**/();
          }
        }
        """);
  }

  @Test
  public void testPureMethodReturningThis() {
    inspectionFixture.get().doTest(
      """
        import org.jetbrains.annotations.Contract;

        class Test {
          boolean closed;
         \s
          @Contract(pure=true, value="->this")
          Test validate() {
            if(closed) throw new IllegalStateException();
            return this;
          }
         \s
          void test() {
            validate();
            System.out.println("ok");
          }
        }
        """);
  }

  @Test
  public void testPureMethodInVoidFunctionalExpression() {
    inspectionFixture.get().doTest(
      """
        import org.jetbrains.annotations.Contract;

        class Util {
          @Contract(pure=true)
          static Object util() { return null; }
        }

        class C {
          static {
            Runnable r = () -> Util./*Result of 'Util.util()' is ignored*/util/**/();
            Runnable r1 = Util::/*Result of 'Util.util()' is ignored*/util/**/;
            Runnable r2 = Object::new; // Reported by ResultOfObjectAllocationIgnoredInspection
          }
        }
        """);
  }

  @Test
  public void testStream() {
    inspectionFixture.get().doTest(
      """
        import java.util.stream.*;
        import java.util.*;

        class Test {
          void test() {
            Stream.of("a", "b", "c")./*Result of 'Stream.collect()' is ignored*/collect/**/(Collectors.toSet());
            Stream.of("a", "b", "c")./*Result of 'Stream.collect()' is ignored*/collect/**/(Collectors.toCollection(() -> new ArrayList<>()));
            Set<String> result = new TreeSet<>();
            result.add("-");
            // violates stream principles, but quite widely used (IDEA-164501);
            // this inspection should not warn here; probably some other inspection should suggest better option\s
            Stream.of("a", "b", "c").collect(Collectors.toCollection(() -> result));
          }
        }""");
  }

  @Test
  public void testPattern() {
    inspectionFixture.get().doTest(
      """
        import java.util.regex.Pattern;
        import java.util.Set;

        class Test {
          void test(Set<String> names) {
            names.forEach(Pattern::/*Result of 'Pattern.compile()' is ignored*/compile/**/);
          }
        }
        """);
  }

  @Test
  public void testPatternCaught() {
    inspectionFixture.get().doTest(
      """
        import java.util.regex.*;
        import java.util.Set;

        class Test {
          void test(Set<String> names) {
            try {
              names.forEach(Pattern::compile);
            }
            catch (PatternSyntaxException e) {
              throw new RuntimeException("Pattern error", e);
            }
          }
        }
        """);
  }

  @Test
  public void testOptionalOrElseThrow() {
    //noinspection OptionalUsedAsFieldOrParameterType
    inspectionFixture.get().doTest(
      """
        import java.util.Optional;

        class Test {
          void test(Optional<String> opt) {
            opt.orElseThrow(RuntimeException::new);
          }
        }
        """);
  }

  @Test
  public void testParamContract() {
    inspectionFixture.get().doTest(
      """
        class X{
        public static int atLeast(int min, int actual, String varName) {
            if (actual < min) throw new IllegalArgumentException('\\'' + varName + " must be at least " + min + ": " + actual);
            return actual;
          }

          public byte[] getMemory(int address, int length) {
            atLeast(0, address, "address");
            atLeast(1, length, "length");

            return new byte[length];
          }
        }
        """);
  }

  @Test
  public void testInForExpressionList() {
    //noinspection StatementWithEmptyBody
    inspectionFixture.get().doTest(
      """
        class X {
          void test(String s) {
            for(int i=0; i<10; i++, s./*Result of 'String.trim()' is ignored*/trim/**/()) {}
          }
        }
        """);
  }

  @Test
  public void testInSwitchExpression() {
    //noinspection SwitchStatementWithTooFewBranches
    inspectionFixture.get().doTest("""
             class X {
               String test(String s) {
                 return switch(s) {
                   default -> s.trim();
                 };
               }
             }
             """);
  }

  @Test
  public void testOptionalGet() {
    //noinspection ALL
    inspectionFixture.get().doTest(
      """
        class X {
          void test(java.util.Optional<String> opt) {
            opt.get();
            if (opt.isPresent()) opt./*Result of 'Optional.get()' is ignored*/get/**/();
          }
        }""");
  }

  @Test
  public void testCommonsLang3NotNull() {
    inspectionFixture.get().doTest(
      """
        import org.apache.commons.lang3.Validate;
        class X{
          void test(String foo) {
            if (foo == null) return;
            Validate.notNull(foo);
          }
        }
        """);
  }

  @Test
  public void testVoidType() {
    inspectionFixture.get().doTest("""
             class X {
               void a() {
                 b();
               }
              \s
               static Void b() {
                 return null;
               }
             }
             """);
  }

  @Test
  public void testIgnoreMethodDefinedInSubclasses() {
    inspectionFixture.get().doTest(
      """
        import java.util.stream.Stream;
        import java.util.*;
        abstract class StreamEx<T> implements Stream<T> {
          public <C extends Collection<? super T>> C into(C collection) {
            return null;
          }
         \s
          public static<T> StreamEx<T> of(T... values) {
            return null;\s
          }
         \s
          public static void main(String[] args) {
            List<Integer> list = new ArrayList<>();
            StreamEx.of(1, 2, 3).into(list);
          }
        }
        """);
  }

  @Test
  public void testStringBuilderMethods() {
    //noinspection DataFlowIssue,EqualsOnSuspiciousObject,TooBroadScope
    inspectionFixture.get().doTest(
      """
        class X {
          void x() {
            String abc = "abc";
                StringBuilder sb = new StringBuilder(abc);

                abc./*Result of 'String.substring()' is ignored*/substring/**/(0, 1);    // OK, warning: Result of `String.substring()` is ignored
                abc./*Result of 'String.compareTo()' is ignored*/compareTo/**/(null);
                sb./*Result of 'AbstractStringBuilder.substring()' is ignored*/substring/**/(0, 1);    // KO, no warning
                sb./*Result of 'Object.equals()' is ignored*/equals/**/(null);
                sb./*Result of 'AbstractStringBuilder.capacity()' is ignored*/capacity/**/();
                sb./*Result of 'AbstractStringBuilder.chars()' is ignored*/chars/**/();
                sb./*Result of 'AbstractStringBuilder.codePointAt()' is ignored*/codePointAt/**/(1);
                sb./*Result of 'AbstractStringBuilder.codePointBefore()' is ignored*/codePointBefore/**/(1);
                sb./*Result of 'AbstractStringBuilder.codePointCount()' is ignored*/codePointCount/**/(0, 1);
                sb./*Result of 'AbstractStringBuilder.codePoints()' is ignored*/codePoints/**/();
                sb./*Result of 'StringBuilder.compareTo()' is ignored*/compareTo/**/(null);
                sb./*Result of 'AbstractStringBuilder.offsetByCodePoints()' is ignored*/offsetByCodePoints/**/(0, 1);
                System.out.println(sb);
          }
        }
        """);
  }

  @Test
  public void testParseShort() {
    inspectionFixture.get().doTest(
      """
        class X {
          boolean validate(String s) {
            try {
              Short.parseShort(s);
              return true;
            }
            catch (NumberFormatException e) {
              return false;
            }
          }
        }
        """);
  }
  @Test
  @SuppressWarnings("Convert2Lambda")
  public void testIgnoreInTestContainers() {
    inspectionFixture.get().doTest(
      """        
        import org.junit.jupiter.api.function.Executable;
        import org.mockito.MockedStatic;
        class X {
          public static String name(){return "name";}
          public void test(String s, MockedStatic<Object> mockedStatic){
            mockedStatic.when(X::name);
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, ()->Short.parseShort(s));
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, ()->Short.parseShort(s),
            () -> {
                Short./*Result of 'Short.parseShort()' is ignored*/parseShort/**/(s);
                return "test";
             });
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, ()-> {
              Short.parseShort("s");
            });
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, ()-> {
                twice("abc");
            });
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, (new Executable() {
              @Override
              public void execute() {
               twice("abc");
            }}));
            org.assertj.core.api.Assertions.assertThatThrownBy((((()->Short.parseShort(s)))));
          }
          private static String twice(String s) {
            return s+s;
          }
        }
        """);
  }

  @Test
  public void testArgumentSideEffects() {
    inspectionFixture.get().doTest("""
             import java.nio.channels.FileChannel;
             import java.nio.ByteBuffer;
             import java.io.IOException;
             class X {
               public static void withoutSideEffects(FileChannel fch, ByteBuffer[] srcs) throws IOException {
                 fch./*Result of 'FileChannel.write()' is ignored*/write/**/(srcs);
               }
               public static long useResult(FileChannel fch, ByteBuffer[] srcs) throws IOException {
                 return fch.write(srcs);
               }
               public static void hasSideEffect(FileChannel fch, ByteBuffer[] srcs) throws IOException {
                 do{
                   fch.write(srcs);
                 } while (test(srcs));
               }
             
               public static boolean test(ByteBuffer[] srcs){
                 return srcs[0].hasRemaining();
               }
             }
             """);
  }
  
  @Test
  public void testFragment() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    PsiFile file = JavaCodeFragmentFactory.getInstance(myFixture.getProject()).createCodeBlockCodeFragment("String.format(\"hello\");", null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.testHighlighting(true, false, false);
  }
}