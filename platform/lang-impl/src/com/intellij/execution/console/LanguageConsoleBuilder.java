package com.intellij.execution.console;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LanguageConsoleBuilder {
  private final LanguageConsoleImpl myConsole;
  private LanguageConsoleView myConsoleView;
  private Condition<LanguageConsoleImpl> myExecutionEnabled = Conditions.alwaysTrue();

  public LanguageConsoleBuilder(@NotNull LanguageConsoleView consoleView) {
    myConsole = consoleView.getConsole();
    myConsoleView = consoleView;
  }

  public LanguageConsoleBuilder(@NotNull Project project, @NotNull Language language) {
    myConsole = new MyLanguageConsole(project, language);
  }

  public LanguageConsoleBuilder processHandler(@NotNull ProcessHandler processHandler) {
    myExecutionEnabled = new ProcessBackedExecutionEnabledCondition(processHandler);
    return this;
  }

  public LanguageConsoleBuilder executionEnabled(@NotNull Condition<LanguageConsoleImpl> condition) {
    myExecutionEnabled = condition;
    return this;
  }

  public LanguageConsoleBuilder initActions(@NotNull BaseConsoleExecuteActionHandler executeActionHandler, @NotNull String historyType) {
    ensureConsoleViewCreated();

    ConsoleExecuteAction action = new ConsoleExecuteAction(myConsoleView, executeActionHandler, ConsoleExecuteAction.CONSOLE_EXECUTE_ACTION_ID, myExecutionEnabled);
    action.registerCustomShortcutSet(action.getShortcutSet(), myConsole.getConsoleEditor().getComponent());

    new ConsoleHistoryController(historyType, null, myConsole, executeActionHandler.getConsoleHistoryModel()).install();
    return this;
  }

  public LanguageConsoleBuilder historyAnnotation(@Nullable GutterContentProvider provider) {
    ((MyLanguageConsole)myConsole).gutterContentProvider = provider;
    return this;
  }

  private void ensureConsoleViewCreated() {
    if (myConsoleView == null) {
      myConsoleView = new LanguageConsoleViewImpl(myConsole, true);
    }
  }

  public LanguageConsoleView build() {
    myConsole.setShowSeparatorLine(false);
    myConsole.initComponents();

    ensureConsoleViewCreated();
    return myConsoleView;
  }

  public static class ProcessBackedExecutionEnabledCondition implements Condition<LanguageConsoleImpl> {
    private final ProcessHandler myProcessHandler;

    public ProcessBackedExecutionEnabledCondition(ProcessHandler myProcessHandler) {
      this.myProcessHandler = myProcessHandler;
    }

    @Override
    public boolean value(LanguageConsoleImpl console) {
      return !myProcessHandler.isProcessTerminated();
    }
  }

  private static class GutteredEditorPanel extends JPanel {
    private final EditorEx editor;

    public GutteredEditorPanel(EditorEx editor) {
      super(new BorderLayout());

      this.editor = editor;
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);

      Rectangle clip = g.getClipBounds();
      if (clip.height < 0) {
        return;
      }

      int lineHeight = editor.getLineHeight();
      int startLine = clip.y / lineHeight;
      int endLine = Math.min(((clip.y + clip.height) / lineHeight) + 1, ((EditorImpl)editor).getVisibleLineCount());
      if (startLine >= endLine) {
        return;
      }

      int y = ((startLine + 1) * lineHeight);
      g.setColor(editor.getColorsScheme().getColor(EditorColors.INDENT_GUIDE_COLOR));
      for (int i = startLine; i < endLine; i++) {
        g.drawLine(ConsoleIconGutterComponent.ICON_AREA_WIDTH, y, clip.width, y);
        y += lineHeight;
      }
    }
  }

  private static class MyLanguageConsole extends LanguageConsoleImpl {
    @Nullable
    private GutterContentProvider gutterContentProvider;

    public MyLanguageConsole(@NotNull Project project, @NotNull Language language) {
      super(project, language.getDisplayName() + " Console", language, false);
    }

    @Override
    protected void setupEditorDefault(@NotNull EditorEx editor) {
      super.setupEditorDefault(editor);

      if (editor == getConsoleEditor()) {
        // todo consider to fix platform
        editor.getSettings().setAdditionalLinesCount(1);
      }
      else if (gutterContentProvider != null) {
        JScrollPane scrollPane = editor.getScrollPane();
        JPanel panel = new GutteredEditorPanel(editor);

        final ConsoleIconGutterComponent lineStartGutter = new ConsoleIconGutterComponent(editor, gutterContentProvider);
        panel.add(lineStartGutter, BorderLayout.LINE_START);

        panel.add(scrollPane.getViewport().getView(), BorderLayout.CENTER);

        final ConsoleGutterComponent lineEndGutter = new ConsoleGutterComponent(editor, gutterContentProvider);
        panel.add(lineEndGutter, BorderLayout.LINE_END);

        scrollPane.setViewportView(panel);

        getProject().getMessageBus().connect(this).subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
          @Override
          public void updateFinished(@NotNull Document document) {
            if (document.getTextLength() == 0) {
              gutterContentProvider.documentCleared(getHistoryViewer());
            }
            lineStartGutter.updateSize();
            lineEndGutter.updateSize();
          }
        });

        editor.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          public void documentChanged(DocumentEvent event) {
            EditorEx editor = getHistoryViewer();
            DocumentEx document = editor.getDocument();
            if (document.isInBulkUpdate()) {
              return;
            }

            if (document.getTextLength() > 0) {
              int startDocLine = document.getLineNumber(event.getOffset());
              int endDocLine = document.getLineNumber(event.getOffset() + event.getNewLength());
              if (event.getOldLength() > event.getNewLength() || startDocLine != endDocLine || StringUtil.indexOf(event.getOldFragment(), '\n') != -1) {
                lineStartGutter.updateSize();
                lineEndGutter.updateSize();
              }
            }
            else if (event.getOldLength() > 0) {
              gutterContentProvider.documentCleared(editor);
            }
          }
        });
      }
    }

    @Override
    protected void doAddPromptToHistory() {
      if (gutterContentProvider == null) {
        super.doAddPromptToHistory();
      }
      else {
        gutterContentProvider.beforeEvaluate(getHistoryViewer());
      }
    }
  }
}