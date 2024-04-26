package com.intellij.java.codeInsight.daemon;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class RehighlightingTest extends JavaCodeInsightFixtureTestCase {
  public void testDeleteClassCaptionUndo() {
    myFixture.addClass("package java.lang.reflect; public class Modifier {}");

    myFixture.configureByText("a.java", """
      import java.lang.reflect.Modifier;

      <caret><selection>class MemberModifiers extends Modifier {</selection>
          public static final MemberModifiers DEFAULT_MODIFIERS = new MemberModifiers(false, false, false);

          private final boolean isVirtual;
          private final boolean isOverride;

          public MemberModifiers(boolean isAbstract, boolean isVirtual, boolean isOverride) {
              this.isVirtual = isVirtual;
              this.isOverride = isOverride;
          }
      

          public boolean isVirtual() {
              return isVirtual;
          }
      
          public boolean isOverride() {
              return isOverride;
          }
      }""");
    myFixture.checkHighlighting(false, false, false);
    final String caption = myFixture.getEditor().getSelectionModel().getSelectedText();
    myFixture.type(" ");
    assertFalse(myFixture.doHighlighting().isEmpty());

    WriteCommandAction.runWriteCommandAction(
      getProject(), () -> myFixture.getEditor().getDocument().insertString(myFixture.getEditor().getCaretModel().getOffset(), caption));

    myFixture.checkHighlighting(false, false, false);
  }
}
