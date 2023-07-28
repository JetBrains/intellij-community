// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.util.registry.Registry;
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
    Registry.get("java.completion.qualifier.as.argument").setValue(true, getTestRootDisposable());
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
    myFixture.complete(CompletionType.BASIC, 1);
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
    Registry.get("java.completion.qualifier.as.argument").setValue(true, getTestRootDisposable());
    myFixture.configureByText("Test.java", """
      package org.test;
                                        
      public abstract class Test {
            
          void run() {
              "test".test4<caret>
          }
          
          public static  <T extends String> void test4(T t, T t2) { }
      }
      """);
    myFixture.complete(CompletionType.BASIC, 1);
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
    Registry.get("java.completion.qualifier.as.argument").setValue(true, getTestRootDisposable());
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
    myFixture.complete(CompletionType.BASIC, 1);
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
}
