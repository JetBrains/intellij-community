// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
@IndexingModeTemplate
public class QualifierCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  @NeedsIndex.Full
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            void run() {
                "test".test3<caret>
            }
      
            public static  <T extends String> void test3(T t, T t2) { }
            public static  <T extends String> void test3(T t) { }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.checkResult("""
                              package org.test;
                            
                              public abstract class Test {
                            
                                  void run() {
                                      test3("test"<caret>);
                                  }
                            
                                  public static  <T extends String> void test3(T t, T t2) { }
                                  public static  <T extends String> void test3(T t) { }
                              }
                              """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSimpleDoubleInvocation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            void run() {
                "test".test3<caret>
            }
      
            public static  <T extends String> void test3(T t, T t2) { }
            public static  <T extends String> void test3(T t) { }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 2);
      myFixture.checkResult("""
                              package org.test;
                            
                              public abstract class Test {
                            
                                  void run() {
                                      test3("test"<caret>);
                                  }
                            
                                  public static  <T extends String> void test3(T t, T t2) { }
                                  public static  <T extends String> void test3(T t) { }
                              }
                              """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSeveralArguments() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            void run() {
                "test".test4<caret>
            }
      
            public static  <T extends String> void test4(T t, T t2) { }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
                              package org.test;
                            
                              public abstract class Test {
                            
                                  void run() {
                                      test4("test", <caret>);
                                  }
                            
                                  public static  <T extends String> void test4(T t, T t2) { }
                              }
                              """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAnotherFile() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.addClass( """
        package org.test2;
      
        public abstract class Test2 {
              public static  <T extends String> void test6(T t, T t2) { }
        }
        """);

      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            void run() {
                "test".test6<caret>
            }
      
            public static  <T extends String> void test4(T t, T t2) { }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
                              package org.test;
                            
                              import org.test2.Test2;
                            
                              public abstract class Test {
                            
                                  void run() {
                                      Test2.test6("test", );
                                  }
                            
                                  public static  <T extends String> void test4(T t, T t2) { }
                              }
                              """);
    });
  }
  @Test
  @NeedsIndex.Full
  public void testPrimitiveLong() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            void run() {
                1L.test4<caret>
            }
      
            public static void test4(Long t) { }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
        package org.test;
      
        public abstract class Test {
      
            void run() {
                test4(1L);
            }
      
            public static void test4(Long t) { }
        }
        """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPrimitiveReferenceInt() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            void run() {
                int x = 1;
                x.test4<caret>
            }
      
            public static void test4(Integer t) { }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
        package org.test;
      
        public abstract class Test {
      
            void run() {
                int x = 1;
                test4(x);
            }
      
            public static void test4(Integer t) { }
        }
        """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testNull() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            void run() {
                null.test4<caret>
            }
      
            public static void test4(Integer t) { }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
        package org.test;
      
        public abstract class Test {
      
            void run() {
                test4(null);
            }
      
            public static void test4(Integer t) { }
        }
        """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testNullForNotNull() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
        import org.jetbrains.annotations.NotNull;
      
        public abstract class Test {
      
            void run() {
                null.test4<caret>
            }
      
            public static void test4(@NotNull Integer t) { }
        }
        """);
      LookupElement[] complete = myFixture.complete(CompletionType.BASIC, 3);
      assertThat(complete).isNullOrEmpty();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testIntersectionType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            void run() {
                "ttt".test4<caret>
            }
      
            public static <T extends CharSequence & Comparable<String>> void test4(T t) { }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
        package org.test;
      
        public abstract class Test {
      
            void run() {
                test4("ttt");
            }
      
            public static <T extends CharSequence & Comparable<String>> void test4(T t) { }
        }
        """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testIntersectionTypeNotFound() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            void run() {
                "ttt".test4<caret>
            }
      
            public static <T extends CharSequence & Comparable<Integer>> void test4(T t) { }
        }
        """);
      LookupElement[] completed = myFixture.complete(CompletionType.BASIC, 3);
      assertThat(completed).isNullOrEmpty();
    });
  }
  @Test
  @NeedsIndex.Full
  public void testCaptureType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
        import java.util.ArrayList;
        import java.util.List;
      
        public abstract class Test {
      
          private static void test2(List<? extends CharSequence> l) {
                l.get(0).testPrint<caret>
            }
      
          public static void testPrint(CharSequence charSequence) {}
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
        package org.test;
        import java.util.ArrayList;
        import java.util.List;
      
        public abstract class Test {
      
          private static void test2(List<? extends CharSequence> l) {
                testPrint(l.get(0));
            }
      
          public static void testPrint(CharSequence charSequence) {}
        }
        """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSimpleGeneric() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
        import java.util.ArrayList;
        import java.util.List;
      
        public abstract class Test {
      
          private static void test2(List<? extends CharSequence> l) {
                List<String> t = new ArrayList<>();
                t.testPrint<caret>
            }
      
          public static <T extends CharSequence> void testPrint(List<T> l) {}
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
        package org.test;
        import java.util.ArrayList;
        import java.util.List;
      
        public abstract class Test {
      
          private static void test2(List<? extends CharSequence> l) {
                List<String> t = new ArrayList<>();
                testPrint(t);
            }
      
          public static <T extends CharSequence> void testPrint(List<T> l) {}
        }
        """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSimpleGenericNotFound() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
        import java.util.ArrayList;
        import java.util.List;
      
        public abstract class Test {
      
          private static void test2(List<? extends CharSequence> l) {
                List<Integer> t = new ArrayList<>();
                t.testPrint<caret>
            }
      
          public static <T extends CharSequence> void testPrint(List<T> l) {}
        }
        """);
      LookupElement[] completed = myFixture.complete(CompletionType.BASIC, 3);
      assertThat(completed).isNullOrEmpty();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAnotherFileWithStaticImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.addClass( """
        package org.test2;
      
        public abstract class Test2 {
              public static  <T extends String> void test6(T t, T t2) { }
        }
        """);

      myFixture.configureByText("Test.java", """
        package org.test;
        import static org.test2.Test2.*;
      
        public abstract class Test {
      
            void run() {
                "test".test6<caret>
            }
      
            public static  <T extends String> void test4(T t, T t2) { }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
        package org.test;
        import static org.test2.Test2.*;
      
        public abstract class Test {
      
            void run() {
                test6("test", );
            }
      
            public static  <T extends String> void test4(T t, T t2) { }
        }
        """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSmartSimple() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            Integer run() {
                return "test".test3<caret>
            }
      
            public static Integer test3(String t) { return 0; }
        }
        """);
      myFixture.complete(CompletionType.SMART, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
                              package org.test;
                            
                              public abstract class Test {
                            
                                  Integer run() {
                                      return test3("test")
                                  }
                            
                                  public static Integer test3(String t) { return 0; }
                              }
                              """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSmartSimpleNotFound() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            Integer run() {
                return "test".test3<caret>
            }
      
            public static String test3(String t) { return 0; }
        }
        """);
      LookupElement[] completed = myFixture.complete(CompletionType.SMART, 3);
      assertThat(completed).isNullOrEmpty();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSmartGenericsSimple() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            String run() {
                return "test".test3<caret>
            }
      
            public static <T> T test3(T t) { return null; }
        }
        """);
      myFixture.complete(CompletionType.SMART, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
                              package org.test;
                            
                              public abstract class Test {
                            
                                  String run() {
                                      return test3("test")
                                  }
                            
                                  public static <T> T test3(T t) { return null; }
                              }
                              """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSmartGenericsSimpleNotFound() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            Runnable run() {
                return "test".test3<caret>
            }
      
            public static <T> T test3(T t) { return null; }
        }
        """);
      LookupElement[] completed = myFixture.complete(CompletionType.SMART, 3);
      assertThat(completed).isNullOrEmpty();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSmartGenericsIndependent() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public abstract class Test {
      
            Runnable run() {
                return "test".test3<caret>
            }
      
            public static <T, K extends Runnable> K test3(T t) { return null; }
        }
        """);
      myFixture.complete(CompletionType.SMART, 3);
      myFixture.type('\n');
      myFixture.checkResult("""
                              package org.test;
                            
                              public abstract class Test {
                            
                                  Runnable run() {
                                      return test3("test")
                                  }
                            
                                  public static <T, K extends Runnable> K test3(T t) { return null; }
                              }
                              """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testStaticInClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.addClass("""
            package org.test;
            public class T {
              static public void test(T t){
              }
            }

        """);
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public class Test {
          public static void main(T t){
            t.test<caret>
          }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 2);
      myFixture.type('\n');
      myFixture.checkResult("""
        package org.test;
      
        public class Test {
          public static void main(T t){
            T.test(t);
          }
        }
        """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSmartStaticInClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, myFixture.getTestRootDisposable());
      myFixture.addClass("""
            package org.test;
            public class T {
              static public String test(T t){
              }
            }

        """);
      myFixture.configureByText("Test.java", """
        package org.test;
      
        public class Test {
          public static String main(T t){
            return t.test<caret>
          }
        }
        """);
      myFixture.complete(CompletionType.SMART, 2);
      myFixture.type('\n');
      myFixture.checkResult("""
        package org.test;
      
        public class Test {
          public static String main(T t){
            return T.test(t);
          }
        }
        """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testStaticWithImplicitImports() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true,
                                                                        myFixture.getTestRootDisposable());
      IdeaTestUtil.withLevel(myFixture.getModule(),
                             JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> {
          myFixture.addClass("""
                               package java.io;
                             
                                public final class IO {
                                  public static void println(Object obj) {}
                                }
                               """);
          myFixture.configureByText("Test.java", """
            public static void main(String[] args) {
                "TEST".printl<caret>
            }
            """);
          myFixture.complete(CompletionType.BASIC, 2);
          myFixture.type('\n');
          myFixture.checkResult("""
                                  public static void main(String[] args) {
                                      println("TEST");
                                  }
                                  """);
        });
    });
  }
  @Test
  @NeedsIndex.Full
  public void testStaticWithoutImplicitImports() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true,
                                                                        myFixture.getTestRootDisposable());
      IdeaTestUtil.withLevel(myFixture.getModule(),
                             JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
          myFixture.addClass("""
                               package java.io;
                             
                               public final class IO {
                                  public static void println(Object obj) {}
                               }
                               """);
          myFixture.configureByText("Test.java", """
            public class Test{
              public static void main(String[] args) {
                  "TEST".printl<caret>
              }
            }
            """);
          myFixture.complete(CompletionType.BASIC, 2);
          myFixture.type('\n');
          myFixture.checkResult("""
                                  import java.io.IO;
                                
                                  public class Test{
                                    public static void main(String[] args) {
                                        IO.println("TEST");
                                    }
                                  }
                                  """);
        });
    });
  }
}
