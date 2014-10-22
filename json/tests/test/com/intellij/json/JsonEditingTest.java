package com.intellij.json;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
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
    WriteCommandAction.runWriteCommandAction(null, new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.type(characters);
        return myFixture.getFile();
      }
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
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myFixture.getProject());
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getCommonSettings(JsonLanguage.INSTANCE).getIndentOptions();
    assertNotNull(indentOptions);
    CommonCodeStyleSettings.IndentOptions oldSettings = (CommonCodeStyleSettings.IndentOptions)indentOptions.clone();
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
}
