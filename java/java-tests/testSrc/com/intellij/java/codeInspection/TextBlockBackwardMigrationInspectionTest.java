// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.TextBlockBackwardMigrationInspection;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @see TextBlockBackwardMigrationInspection
 */
public class TextBlockBackwardMigrationInspectionTest extends LightQuickFixParameterizedTestCase {

  public void testTrailingWhitespace() {
    configureFromFileText("TrailingWhitespace.java", "class TextBlockMigration {\n" +
                                                     "\n" +
                                                     "  String multipleLiterals() {\n" +
                                                     "    return \"\"\"<caret> \t\f\n" +
                                                     "           hello \t\f\n" +
                                                     "           world \t\f\n" +
                                                     "           \"\"\";\n" +
                                                     "  }\n" +
                                                     "}");
    final IntentionAction action = findActionWithText("Replace with regular string literal");
    invoke(action);
    checkResultByText("class TextBlockMigration {\n" +
                      "\n" +
                      "  String multipleLiterals() {\n" +
                      "    return \"hello\\n\" +\n" +
                      "           \"world\\n\";\n" +
                      "  }\n" +
                      "}");
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new TextBlockBackwardMigrationInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/textBlockBackwardMigration/";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_14_PREVIEW;
  }
}
