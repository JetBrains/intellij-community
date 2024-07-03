// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class NormalImplicitClassCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new ProjectDescriptor(JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel());
  }

  @NeedsIndex.SmartMode(reason = "out of standard library")
  public void testImplicitIoImport() {
    myFixture.addClass("""
                         package java.io;
                         
                         public final class IO {
                           public static void println(Object obj) {}
                         }
                         """);

    myFixture.configureByText("a.java", """
      void main(){
        prin<caret>
      }""");
    myFixture.complete(CompletionType.BASIC, 0);
    myFixture.checkResult("""
                            void main(){
                              println(<caret>);
                            }""");
  }
}
