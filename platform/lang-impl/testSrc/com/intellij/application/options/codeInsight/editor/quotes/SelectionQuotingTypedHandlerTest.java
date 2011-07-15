package com.intellij.application.options.codeInsight.editor.quotes;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author oleg
 */
public class SelectionQuotingTypedHandlerTest extends LightPlatformCodeInsightFixtureTestCase {

  private boolean myPrevValue;

 /**
   * Perfoms an action as write action
   *
   * @param project Project
   * @param action  Runnable to be executed
   */
  public static void performAction(final Project project, final Runnable action) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, action, "test command", null);
      }
    });
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPrevValue = CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED;
    CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = myPrevValue;
    super.tearDown();
  }

  public void testWOSelection() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "aaa\nbbb\n\n");
    myFixture.getEditor().getCaretModel().moveToOffset(0);
    final TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();
    performAction(myFixture.getProject(), new Runnable() {
      public void run() {
        typedAction.actionPerformed(myFixture.getEditor(), '"', ((EditorEx)myFixture.getEditor()).getDataContext());
      }
    });
    myFixture.checkResult("\"aaa\nbbb\n\n");
  }

  public void testWithSelection() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "aaa\nbbb\n\n");
    myFixture.getEditor().getCaretModel().moveToOffset(0);
    myFixture.getEditor().getSelectionModel().setSelection(0, 4);
    final TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();
    performAction(myFixture.getProject(), new Runnable() {
      public void run() {
        typedAction.actionPerformed(myFixture.getEditor(), '"', ((EditorEx)myFixture.getEditor()).getDataContext());
      }
    });
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResult("\"aaa\n\"bbb\n\n");
  }

  public void testWithSingleCharSelection() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "aaa\nbbb\n\n");
    myFixture.getEditor().getCaretModel().moveToOffset(0);
    myFixture.getEditor().getSelectionModel().setSelection(0, 1);
    final TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();
    performAction(myFixture.getProject(), new Runnable() {
      public void run() {
        typedAction.actionPerformed(myFixture.getEditor(), '"', ((EditorEx)myFixture.getEditor()).getDataContext());
      }
    });
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResult("\"a\"aa\nbbb\n\n");
  }

  public void testChangeQuotes() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "\"aaa\"\nbbb\n\n");
    myFixture.getEditor().getCaretModel().moveToOffset(0);
    myFixture.getEditor().getSelectionModel().setSelection(0, 5);
    final TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();
    performAction(myFixture.getProject(), new Runnable() {
      public void run() {
        typedAction.actionPerformed(myFixture.getEditor(), '\'', ((EditorEx)myFixture.getEditor()).getDataContext());
      }
    });
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResult("'aaa'\nbbb\n\n");
  }

  public void testRuby7852ErrantEditor() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "\"aaa\"\nbbb\n\n");
    myFixture.getEditor().getCaretModel().moveToOffset(0);
    myFixture.getEditor().getSelectionModel().setSelection(0, 5);
    final TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();
    performAction(myFixture.getProject(), new Runnable() {
      public void run() {
        typedAction.actionPerformed(myFixture.getEditor(), '\'', ((EditorEx)myFixture.getEditor()).getDataContext());
      }
    });
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResult("'aaa'\nbbb\n\n");

    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getLineStartOffset(3));
    performAction(myFixture.getProject(), new Runnable() {
      public void run() {
        typedAction.actionPerformed(myFixture.getEditor(), 'A', ((EditorEx)myFixture.getEditor()).getDataContext());
        typedAction.actionPerformed(myFixture.getEditor(), 'B', ((EditorEx)myFixture.getEditor()).getDataContext());
      }
    });
    myFixture.checkResult("'aaa'\nbbb\n\nAB");
  }
}
