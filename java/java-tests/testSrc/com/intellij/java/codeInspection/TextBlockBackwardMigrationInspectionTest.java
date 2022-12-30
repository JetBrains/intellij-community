// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.TextBlockBackwardMigrationInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @see TextBlockBackwardMigrationInspection
 */
public class TextBlockBackwardMigrationInspectionTest extends LightQuickFixParameterizedTestCase {

  public void testTrailingWhitespace() {
    //noinspection UnnecessaryStringEscape
    configureFromFileText("TrailingWhitespace.java", """
      class TextBlockMigration {

        String multipleLiterals() {
          return ""\"<caret> \t\f
                 hello \t\f
                 world \t\f
                 ""\";
        }
      }""");
    final IntentionAction action = findActionWithText("Replace with regular string literal");
    invoke(action);
    checkResultByText("""
                        class TextBlockMigration {

                          String multipleLiterals() {
                            return "hello\\n" +
                                   "world\\n";
                          }
                        }""");
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new TextBlockBackwardMigrationInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/textBlockBackwardMigration/";
  }
}
