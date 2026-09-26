// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.refactoring.suggested.LightJavaCodeInsightFixtureTestCaseWithUtils;

public final class JavadocReferenceNavigationTest extends LightJavaCodeInsightFixtureTestCaseWithUtils {
  private static final String BASIC_FUNCTION = "void function();";
  private static final String BASIC_FUNCTION_ARGS = "void functionArgs(int beep, int boop);";

  public void testTags() {
    check("/// @see function<caret>", BASIC_FUNCTION);
    check("/// @see #functionArgs<caret>(int, int)", BASIC_FUNCTION_ARGS);

    check("/// {@link function<caret>()}", BASIC_FUNCTION);
    check("/// {@link functionArgs<caret>(int, int)}", BASIC_FUNCTION_ARGS);
  }

  public void testMarkdownReferences() {
    check("/// [function<caret>()]", BASIC_FUNCTION);
    check("/// [functionArgs<caret>(int,int)]", BASIC_FUNCTION_ARGS);
    check("/// [function<caret>()]", BASIC_FUNCTION);
    check("/// [functionArgs<caret>(int,int)]", BASIC_FUNCTION_ARGS);

    // Just ignore how the module is not taken into account here
    check("/// [java.base/SeeTags#function<caret>()]", BASIC_FUNCTION);
  }

  /// Verify if the navigation brings you to the expected line
  ///
  /// The caller is expected to bring a comment containing a `<caret>` tag
  private void check(String comment, String expectedLine) {
    myFixture.configureByText("SeeTags.java", """
      %s
      interface SeeTags {
        %s
        %s
      }""".formatted(comment, BASIC_FUNCTION, BASIC_FUNCTION_ARGS));
    navigateAndCheckLine(expectedLine);
  }

  private void navigateAndCheckLine(String expectedLine) {
    myFixture.performEditorAction("GotoDeclaration");
    final var selectionModel = myFixture.getEditor().getSelectionModel();
    selectionModel.selectLineAtCaret();
    assertEquals(expectedLine, selectionModel.getSelectedText().trim());
  }
}
