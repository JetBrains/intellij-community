package com.intellij.execution.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class FoldLinesLikeThis extends DumbAwareAction {
  private final Editor myEditor;
  private final ConsoleViewImpl myConsole;

  public FoldLinesLikeThis(Editor editor, ConsoleViewImpl console) {
    super("Fold Lines Like This");
    myEditor = editor;
    myConsole = console;
  }

  @Nullable
  private String getSingleLineSelection() {
    final SelectionModel model = myEditor.getSelectionModel();
    final Document document = myEditor.getDocument();
    if (!model.hasSelection()) {
      final int offset = myEditor.getCaretModel().getOffset();
      if (offset <= document.getTextLength()) {
        final int lineNumber = document.getLineNumber(offset);
        final String line = document.getText().substring(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber)).trim();
        if (StringUtil.isNotEmpty(line)) {
          return line;
        }
      }

      return null;
    }
    final int start = model.getSelectionStart();
    final int end = model.getSelectionEnd();
    if (document.getLineNumber(start) == document.getLineNumber(end)) {
      final String selection = document.getText().substring(start, end).trim();
      if (StringUtil.isNotEmpty(selection)) {
        return selection;
      }
    }
    return null;
  }

  @Override
  public void update(AnActionEvent e) {
    final boolean enabled = getSingleLineSelection() != null;
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final String selection = getSingleLineSelection();
    assert selection != null;
    ShowSettingsUtil.getInstance().editConfigurable(myEditor.getProject(), new ConsoleFoldingConfigurable() {
      @Override
      public void reset() {
        super.reset();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            addRule(selection);
          }
        }, ModalityState.stateForComponent(createComponent()));
      }
    });
    myConsole.foldImmediately();
  }
}
