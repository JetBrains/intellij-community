package com.intellij.json;

import com.intellij.json.formatter.JsonCodeStyleSettings.PropertyAlignment;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonFormattingTest extends JsonTestCase {
  
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/formatting";
  }

  public void testContainerElementsAlignment() {
    doTest();
  }

  public void testBlankLinesStripping() {
    doTest();
  }

  public void testSpacesInsertion() {
    doTest();
  }
  
  public void testDoNotThrowFailedToAlignException() {
    getCustomCodeStyleSettings().PROPERTY_ALIGNMENT = PropertyAlignment.ALIGN_ON_VALUE.getId(); 
    doTest();
  }

  public void testWrapping() {
    getCodeStyleSettings().setRightMargin(JsonLanguage.INSTANCE, 20);
    doTest();
  }

  // WEB-13587
  public void testAlignPropertiesOnColon() {
    checkPropertyAlignment(PropertyAlignment.ALIGN_ON_COLON);
  }

  // WEB-13587
  public void testAlignPropertiesOnValue() {
    checkPropertyAlignment(PropertyAlignment.ALIGN_ON_VALUE);
  }

  private void checkPropertyAlignment(@NotNull final PropertyAlignment alignmentType) {
    getCustomCodeStyleSettings().PROPERTY_ALIGNMENT = alignmentType.getId();
    doTest();
  }

  public void testChopDownArrays() {
    getCustomCodeStyleSettings().ARRAY_WRAPPING = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    getCodeStyleSettings().setRightMargin(JsonLanguage.INSTANCE, 40);
    doTest();
  }

  // IDEA-138902
  public void testObjectsWithSingleProperty() {
    doTest();
  }

  // Moved from JavaScript

  public void testWeb3830() {
    CommonCodeStyleSettings.IndentOptions options = getIndentOptions();
    options.INDENT_SIZE = 8;
    options.USE_TAB_CHARACTER = true;
    options.TAB_SIZE = 8;
    doTest();
  }

  public void testReformatJSon() {
    getIndentOptions().INDENT_SIZE = 4;
    doTest();
  }

  public void testReformatJSon2() {
    getIndentOptions().INDENT_SIZE = 4;
    doTest();
  }

  public void testRemoveTrailingCommas() {
    doTest();
  }

  public void testReformatIncompleteJson1() { doTest();}

  public void testReformatIncompleteJson2() { doTest();}

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".json");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFixture.getProject());
      codeStyleManager.reformat(myFixture.getFile());
    });
    myFixture.checkResultByFile(getTestName(false) + "_after.json");
  }
}
