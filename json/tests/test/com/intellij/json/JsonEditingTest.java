package com.intellij.json;

import com.intellij.json.formatter.JsonCodeStyleSettings;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonEditingTest extends JsonTestCase {

  private void doTest(@NotNull final String characters) {
    final String testName = "editing/" + getTestName(false);
    myFixture.configureByFile(testName + ".json");
    final int offset = myFixture.getEditor().getCaretModel().getOffset();
    WriteCommandAction.runWriteCommandAction(null, (Computable<PsiFile>)() -> {
      myFixture.getEditor().getCaretModel().moveToOffset(offset);
      myFixture.type(characters);
      return myFixture.getFile();
    });
    myFixture.checkResultByFile(testName + ".after.json");
  }

  public void testContinuationIndentAfterPropertyKey() {
    doTest("\n");
  }

  public void testContinuationIndentAfterColon() {
    doTest("\n");
  }

  // IDEA-130594
  public void testNormalIndentAfterPropertyWithoutComma() {
    doTest("\n");
  }

  // WEB-13675
  public void testIndentWithTabsWhenSmartTabEnabled() {
    final CommonCodeStyleSettings.IndentOptions indentOptions = getIndentOptions();
    final CommonCodeStyleSettings.IndentOptions oldSettings = (CommonCodeStyleSettings.IndentOptions)indentOptions.clone();
    indentOptions.TAB_SIZE = 4;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.SMART_TABS = true;
    try {
      doTest("\n\"baz\"");
    }
    finally {
      indentOptions.copyFrom(oldSettings);
    }
  }

  // Moved from JavaScript

  // WEB-11600
  public void testEnterWhenPropertiesAlignedOnValue() {
    doEnterTestForWeb11600();
  }

  // WEB-11600
  public void testEnterWhenPropertiesAlignedOnValue1() {
    doEnterTestForWeb11600();
  }

  private void doEnterTestForWeb11600() {
    final JsonCodeStyleSettings settings = getCustomCodeStyleSettings();
    final CommonCodeStyleSettings.IndentOptions indentOptions = getIndentOptions();

    final int oldPropertyAlignment = settings.PROPERTY_ALIGNMENT;
    final int oldIndentSize = indentOptions.INDENT_SIZE;
    settings.PROPERTY_ALIGNMENT = JsonCodeStyleSettings.ALIGN_PROPERTY_ON_VALUE;
    indentOptions.INDENT_SIZE = 4;
    try {
      doTest("\n");
    }
    finally {
      indentOptions.INDENT_SIZE = oldIndentSize;
      settings.PROPERTY_ALIGNMENT = oldPropertyAlignment;
    }
  }
}
