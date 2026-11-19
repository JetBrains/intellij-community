// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("RedundantThrows")
@TestApplication
@IndexingModeTemplate
public class NormalCatchSectionCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  public void testUnhandledCheckedExceptions() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (CheckedException1 e)", "catch (CheckedException2 e)")));
  }

  @Test
  public void testUnhandledCheckedExceptionsWithCatch() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (CheckedException1 e)")));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testUnhandledCheckedExceptionsWithAllCaught() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)")));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testUnhandledCheckedExceptionsWithParents() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)")));
  }

  @Test
  public void testUnhandledCheckedExceptionsWithExistedImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
                           package com.test;
                         
                           public class TestException extends RuntimeException{}
                           """);
      myFixture.addClass("""
                           package com.test2;
                           import com.test.TestException;
                         
                           public final class Test{
                             public static void test() throws TestException{}
                           }
                           """);

      completion.get().configure();

      checkRenderedItems(List.of("catch", "catch (TestException e)"));

      LookupElement element = completion.get().getItems()[1];
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(element);
      String text = presentation.getTailText();
      assertEquals(" (TestException e)", text);
      completion.get().selectItem(element);

      completion.get().checkResult();
    });
  }

  @Test
  public void testUnhandledCheckedExceptionsWithImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
                           package com.test;
                         
                           public class TestException extends RuntimeException{}
                           """);
      myFixture.addClass("""
                           package com.test2;
                           import com.test.TestException;
                         
                           public final class Test{
                             public static void test() throws TestException{}
                           }
                           """);

      completion.get().configure();

      checkRenderedItems(List.of("catch", "catch (TestException e)"));

      LookupElement element = completion.get().getItems()[1];
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(element);
      String text = presentation.getTailText();
      assertEquals(" (TestException e)", text);
      completion.get().selectItem(element);

      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testManyUnhandledCheckedExceptions() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)")));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testUnhandledCheckedExceptionsWithDefault() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)")));
  }

  @Test
  public void testUnhandledRuntimeExceptions() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (CheckedException1 e)", "catch (CheckedException2 e)")));
  }

  @Test
  public void testUnhandledCheckedExceptionsWithRuntimeException() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (CheckedException1 e)", "catch (CheckedException2 e)")));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEmptyTryCatch() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)")));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testUnhandledCheckedExceptionsWithNullType() {
    EdtTestUtil.runInEdtAndWait(() -> simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)")));
  }

  private void simpleTestCatchSection(@NotNull List<String> catches) {
    completion.get().configure();

    checkRenderedItems(catches);

    completion.get().selectItem(completion.get().getItems()[1]);

    completion.get().checkResult();
  }

  private void checkRenderedItems(@NotNull List<String> catches) {
    List<String> renderedItems = Arrays.stream(completion.get().getItems()).map(t -> {
      LookupElementPresentation presentation = new LookupElementPresentation();
      t.renderElement(presentation);
      return (presentation.getItemText() != null ? presentation.getItemText() : "") +
             (presentation.getTailText() != null ? presentation.getTailText() : "");
    }).toList();
    assertEquals(catches, renderedItems);
  }
}
