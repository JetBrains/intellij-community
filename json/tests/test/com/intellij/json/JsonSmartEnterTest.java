package com.intellij.json;

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonSmartEnterTest extends JsonTestCase {
  public void doTest() {
    myFixture.configureByFile("smartEnter/" + getTestName(false) + ".json");
    final List<SmartEnterProcessor> processors = SmartEnterProcessors.INSTANCE.forKey(JsonLanguage.INSTANCE);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      final Editor editor = myFixture.getEditor();
      for (SmartEnterProcessor processor : processors) {
        processor.process(myFixture.getProject(), editor, myFixture.getFile());
      }
    });
    myFixture.checkResultByFile("smartEnter/" + getTestName(false) + "_after.json", true);
  }

  public void testCommaInsertedAfterArrayElement() {
    doTest();
  }

  public void testCommaInsertedAfterProperty() {
    doTest();
  }

  public void testCommaInsertedAfterPropertyWithMultilineValue() {
    doTest();
  }

  public void testColonInsertedAfterPropertyKey() {
    doTest();
  }

  public void testPropertyKeyQuotedAndCommaInserted() {
    doTest();
  }
}
