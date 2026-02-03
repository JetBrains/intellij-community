// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NormalImplicitClassCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
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

  @NeedsIndex.ForStandardLibrary
  public void testImplicitModuleImport() {
    myFixture.configureByText("a.java", """
      void main(){
        List<String> a = new ArrayLis<caret>
      }""");
    myFixture.complete(CompletionType.BASIC, 0);
    assertEquals(List.of("ArrayList", "CopyOnWriteArrayList"), myFixture.getLookupElementStrings());
    LookupElement[] elements = myFixture.getLookupElements();
    assertNotNull(elements);
    myFixture.getLookup().setCurrentItem(elements[0]);
    myFixture.type('\n');
    myFixture.checkResult("""
                            void main(){
                              List<String> a = new ArrayList<>(<caret>)
                            }""");
  }
}
