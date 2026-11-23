// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
@IndexingModeTemplate
public class NormalImplicitClassCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  @NeedsIndex.SmartMode(reason = "out of standard library")
  public void testImplicitIoImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testImplicitModuleImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }
}
