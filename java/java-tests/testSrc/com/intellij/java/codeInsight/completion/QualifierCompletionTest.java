// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class QualifierCompletionTest extends NormalCompletionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @NeedsIndex.Full
  public void testSimple() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testSimpleDoubleInvocation() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testSeveralArguments() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testAnotherFile() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }
  @NeedsIndex.Full
  public void testPrimitiveLong() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testPrimitiveReferenceInt() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testNull() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testNullForNotNull() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
    assertEmpty(complete);
  }

  @NeedsIndex.Full
  public void testIntersectionType() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testIntersectionTypeNotFound() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
    assertEmpty(completed);
  }
  @NeedsIndex.Full
  public void testCaptureType() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testSimpleGeneric() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testSimpleGenericNotFound() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
    assertEmpty(completed);
  }

  @NeedsIndex.Full
  public void testAnotherFileWithStaticImport() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testSmartSimple() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testSmartSimpleNotFound() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
    assertEmpty(completed);
  }

  @NeedsIndex.Full
  public void testSmartGenericsSimple() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testSmartGenericsSimpleNotFound() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
    assertEmpty(completed);
  }

  @NeedsIndex.Full
  public void testSmartGenericsIndependent() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testStaticInClass() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }

  @NeedsIndex.Full
  public void testSmartStaticInClass() {
    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("java.completion.qualifier.as.argument", true, getTestRootDisposable());
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
  }
}
