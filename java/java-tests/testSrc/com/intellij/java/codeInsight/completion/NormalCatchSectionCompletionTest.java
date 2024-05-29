// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("RedundantThrows")
public class NormalCatchSectionCompletionTest extends NormalCompletionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testUnhandledCheckedExceptions() {
    simpleTestCatchSection(List.of("catch", "catch (CheckedException1 e)", "catch (CheckedException2 e)"));
  }

  public void testUnhandledCheckedExceptionsWithCatch() {
    simpleTestCatchSection(List.of("catch", "catch (CheckedException1 e)"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testUnhandledCheckedExceptionsWithAllCaught() {
    simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testUnhandledCheckedExceptionsWithParents() {
    simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)"));
  }

  public void testUnhandledCheckedExceptionsWithExistedImport() {
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

    configure();

    checkRenderedItems(List.of("catch", "catch (TestException e)"));

    LookupElement element = myItems[1];
    LookupElementPresentation presentation = renderElement(element);
    String text = presentation.getTailText();
    assertEquals(" (TestException e)", text);
    selectItem(element);

    checkResult();
  }

  public void testUnhandledCheckedExceptionsWithImport() {
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

    configure();

    checkRenderedItems(List.of("catch", "catch (TestException e)"));

    LookupElement element = myItems[1];
    LookupElementPresentation presentation = renderElement(element);
    String text = presentation.getTailText();
    assertEquals(" (TestException e)", text);
    selectItem(element);

    checkResult();
  }

  @NeedsIndex.ForStandardLibrary
  public void testManyUnhandledCheckedExceptions() {
    simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testUnhandledCheckedExceptionsWithDefault() {
    simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)"));
  }

  public void testUnhandledRuntimeExceptions() {
    simpleTestCatchSection(List.of("catch", "catch (CheckedException1 e)", "catch (CheckedException2 e)"));
  }

  public void testUnhandledCheckedExceptionsWithRuntimeException() {
    simpleTestCatchSection(List.of("catch", "catch (CheckedException1 e)", "catch (CheckedException2 e)"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testEmptyTryCatch() {
    simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)"));
  }


  @NeedsIndex.ForStandardLibrary
  public void testUnhandledCheckedExceptionsWithNullType() {
    simpleTestCatchSection(List.of("catch", "catch (Exception e)", "catch (RuntimeException e)"));
  }

  private void simpleTestCatchSection(@NotNull List<String> catches) {
    configure();

    checkRenderedItems(catches);

    selectItem(myItems[1]);

    checkResult();
  }

  private void checkRenderedItems(@NotNull List<String> catches) {
    List<String> renderedItems = Arrays.stream(myItems).map(t -> {
      LookupElementPresentation presentation = new LookupElementPresentation();
      t.renderElement(presentation);
      return (presentation.getItemText() != null ? presentation.getItemText() : "") +
             (presentation.getTailText() != null ? presentation.getTailText() : "");
    }).toList();
    assertEquals(catches, renderedItems);
  }
}
