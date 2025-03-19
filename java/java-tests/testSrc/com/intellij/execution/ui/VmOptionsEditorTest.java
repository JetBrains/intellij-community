// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.testFramework.LightPlatform4TestCase;
import com.intellij.ui.EditorTextField;
import org.junit.Test;

public class VmOptionsEditorTest extends LightPlatform4TestCase {
  @Test
  public void testVmOptionsEditor() {
    ApplicationConfiguration configuration = new ApplicationConfiguration("Test", getProject());
    VmOptionsEditor editor = new VmOptionsEditor(configuration);
    String text = "  -XX:Abc=\"hello world\"   -XX<caret>:Def=123";
    EditorTextField origField = editor.getTextField();
    origField.addNotify();
    assertNotNull(origField.getEditor(true));
    assertTrue(origField.getEditor().isOneLineMode());
    assertFalse(editor.isExpanded());
    origField.setText(text.replace("<caret>", ""));
    origField.getEditor().getCaretModel().moveToOffset(text.indexOf("<caret>"));
    checkExpectedText(origField, text);
    
    editor.expand();
    assertTrue(editor.isExpanded());
    EditorTextField expanded = editor.getTextField();
    expanded.addNotify();
    assertNotNull(expanded.getEditor(true));
    assertFalse(expanded.getEditor().isOneLineMode());
    checkExpectedText(expanded, """
      -XX:Abc="hello world"
      -XX:Def<caret>=123""");
    editor.collapse();
    
    expanded.removeNotify();
    origField.removeNotify();
  }

  private static void checkExpectedText(EditorTextField expanded, String expected) {
    int offset = expanded.getEditor().getCaretModel().getOffset();
    String expandedText = expanded.getText();
    String expandedTextWithCaret = expandedText.substring(0, offset) + "<caret>" + expandedText.substring(offset);
    assertEquals(expected, expandedTextWithCaret);
  }
}
