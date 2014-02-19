package com.intellij.execution.console;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.Consumer;
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

    ConsoleExecuteAction action = new ConsoleExecuteAction(myConsoleView, executeActionHandler, myExecutionEnabled);
    action.registerCustomShortcutSet(action.getShortcutSet(), myConsole.getConsoleEditor().getComponent());

    new ConsoleHistoryController(historyType, null, myConsole, executeActionHandler.getConsoleHistoryModel()).install();
    return this;
  }

  /**
   * todo This API doesn't look good, but it is much better than force client to know low-level details
   */
  public static Pair<AnAction, ConsoleHistoryController> registerExecuteAction(@NotNull LanguageConsoleImpl console,
                                                                               @NotNull final Consumer<String> executeActionHandler,
                                                                               @NotNull String historyType,
                                                                               @Nullable String historyPersistenceId,
                                                                               @Nullable Condition<LanguageConsoleImpl> enabledCondition) {
    ConsoleExecuteAction.ConsoleExecuteActionHandler handler = new ConsoleExecuteAction.ConsoleExecuteActionHandler(true) {
      @Override
      void doExecute(@NotNull String text, @NotNull LanguageConsoleImpl console, @Nullable LanguageConsoleView consoleView) {
        executeActionHandler.consume(text);
      }
    };

    ConsoleExecuteAction action = new ConsoleExecuteAction(console, handler, enabledCondition);
    action.registerCustomShortcutSet(action.getShortcutSet(), console.getConsoleEditor().getComponent());

    ConsoleHistoryController historyController = new ConsoleHistoryController(historyType, historyPersistenceId, console, handler.getConsoleHistoryModel());
    historyController.install();
    return new Pair<AnAction, ConsoleHistoryController>(action, historyController);
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

  private static class MyLanguageConsole extends LanguageConsoleImpl {
    @Nullable
    private GutterContentProvider gutterContentProvider;

    public MyLanguageConsole(@NotNull Project project, @NotNull Language language) {
      super(project, language.getDisplayName() + " Console", language, false);
    }

    @Override
    protected void setupEditorDefault(@NotNull final EditorEx editor) {
      super.setupEditorDefault(editor);

      if (editor == getConsoleEditor()) {
        // todo consider to fix platform
        editor.getSettings().setAdditionalLinesCount(1);
      }
      else if (gutterContentProvider != null) {
        JLayeredPane layeredPane = new JBLayeredPane() {
          @Override
          public void doLayout() {
            EditorComponentImpl editor = null;
            Component lineStartGutter = null;
            Component lineEndGutter = null;
            for (int i = getComponentCount() - 1; i >= 0; i--) {
              Component component = getComponent(i);
              if (component instanceof EditorComponentImpl) {
                editor = (EditorComponentImpl)component;
              }
              else if (getLayer(component) == JLayeredPane.DEFAULT_LAYER) {
                lineStartGutter = component;
              }
              else {
                lineEndGutter = component;
              }
            }

            assert editor != null && lineStartGutter != null && lineEndGutter != null;

            int w = getWidth();
            int h = getHeight();
            Dimension lineStartGutterDimension = lineStartGutter.getPreferredSize();
            lineStartGutter.setBounds(0, 0, lineStartGutterDimension.width, h);

            editor.setBounds(lineStartGutterDimension.width, 0, w - lineStartGutterDimension.width, h);

            Dimension lineEndGutterDimension = lineEndGutter.getPreferredSize();
            lineEndGutter.setBounds(w - lineEndGutterDimension.width - editor.getEditor().getScrollPane().getVerticalScrollBar().getWidth(), 0, lineEndGutterDimension.width, h);
          }
        };

        ConsoleIconGutterComponent lineStartGutter = new ConsoleIconGutterComponent(editor, gutterContentProvider);
        layeredPane.add(lineStartGutter, JLayeredPane.DEFAULT_LAYER);

        JScrollPane scrollPane = editor.getScrollPane();
        layeredPane.add(scrollPane.getViewport().getView(), JLayeredPane.DEFAULT_LAYER);

        ConsoleGutterComponent lineEndGutter = new ConsoleGutterComponent(editor, gutterContentProvider);
        layeredPane.add(lineEndGutter, JLayeredPane.PALETTE_LAYER);

        scrollPane.setViewportView(layeredPane);

        GutterUpdateScheduler gutterUpdateScheduler = new GutterUpdateScheduler(lineStartGutter, lineEndGutter);
        getProject().getMessageBus().connect(this).subscribe(DocumentBulkUpdateListener.TOPIC, gutterUpdateScheduler);
        editor.getDocument().addDocumentListener(gutterUpdateScheduler);
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

    private class GutterUpdateScheduler extends DocumentAdapter implements DocumentBulkUpdateListener {
      private final ConsoleIconGutterComponent lineStartGutter;
      private final ConsoleGutterComponent lineEndGutter;

      private boolean lineSeparatorPainterAdded;

      public GutterUpdateScheduler(@NotNull ConsoleIconGutterComponent lineStartGutter, @NotNull ConsoleGutterComponent lineEndGutter) {
        this.lineStartGutter = lineStartGutter;
        this.lineEndGutter = lineEndGutter;
      }

      private void addLineSeparatorPainterIfNeed() {
        if (lineSeparatorPainterAdded) {
          return;
        }

        lineSeparatorPainterAdded = true;

        EditorEx editor = getHistoryViewer();
        editor.getMarkupModel().addRangeHighlighter(new MyRangeMarkerImpl(editor), 0, getDocument().getTextLength(), false, false, HighlighterLayer.ADDITIONAL_SYNTAX);
      }

      private DocumentEx getDocument() {
        return getHistoryViewer().getDocument();
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        DocumentEx document = getDocument();
        if (document.isInBulkUpdate()) {
          return;
        }

        if (document.getTextLength() > 0) {
          addLineSeparatorPainterIfNeed();
          int startDocLine = document.getLineNumber(event.getOffset());
          int endDocLine = document.getLineNumber(event.getOffset() + event.getNewLength());
          if (event.getOldLength() > event.getNewLength() || startDocLine != endDocLine || StringUtil.indexOf(event.getOldFragment(), '\n') != -1) {
            lineStartGutter.updateSize();
            lineEndGutter.updateSize();
          }
        }
        else if (event.getOldLength() > 0) {
          assert gutterContentProvider != null;
          gutterContentProvider.documentCleared(getHistoryViewer());
        }
      }

      @Override
      public void updateStarted(@NotNull Document doc) {
      }

      @Override
      public void updateFinished(@NotNull Document doc) {
        if (getDocument().getTextLength() == 0) {
          assert gutterContentProvider != null;
          gutterContentProvider.documentCleared(getHistoryViewer());
        }
        else {
          addLineSeparatorPainterIfNeed();
        }
        lineStartGutter.updateSize();
        lineEndGutter.updateSize();
      }
    }

    private class MyRangeMarkerImpl extends RangeMarkerImpl implements RangeHighlighterEx, Getter<RangeHighlighterEx> {
      private final CustomHighlighterRenderer renderer = new CustomHighlighterRenderer() {
        @Override
        public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
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
          assert gutterContentProvider != null;
          for (int i = startLine; i < endLine; i++) {
            if (gutterContentProvider.isShowSeparatorLine(editor.visualToLogicalPosition(new VisualPosition(i, 0)).line, editor)) {
              g.drawLine(0, y, clip.width, y);
            }
            y += lineHeight;
          }
        }
      };

      public MyRangeMarkerImpl(@NotNull EditorEx editor) {
        super(editor.getDocument(), 0, 1, false);
      }

      @Override
      protected void changedUpdateImpl(DocumentEvent e) {
        setIntervalEnd(myDocument.getTextLength());
      }

      @Override
      public boolean isAfterEndOfLine() {
        return false;
      }

      @Override
      public void setAfterEndOfLine(boolean value) {
      }

      @Override
      public int getAffectedAreaStartOffset() {
        return 0;
      }

      @Override
      public int getAffectedAreaEndOffset() {
        return myDocument.getTextLength();
      }

      @Override
      public void setTextAttributes(@NotNull TextAttributes textAttributes) {
      }

      @NotNull
      @Override
      public HighlighterTargetArea getTargetArea() {
        return HighlighterTargetArea.EXACT_RANGE;
      }

      @Nullable
      @Override
      public TextAttributes getTextAttributes() {
        return null;
      }

      @Nullable
      @Override
      public LineMarkerRenderer getLineMarkerRenderer() {
        return null;
      }

      @Override
      public void setLineMarkerRenderer(@Nullable LineMarkerRenderer renderer) {
      }

      @Nullable
      @Override
      public CustomHighlighterRenderer getCustomRenderer() {
        return renderer;
      }

      @Override
      public void setCustomRenderer(CustomHighlighterRenderer renderer) {
      }

      @Nullable
      @Override
      public GutterIconRenderer getGutterIconRenderer() {
        return null;
      }

      @Override
      public void setGutterIconRenderer(@Nullable GutterIconRenderer renderer) {
      }

      @Nullable
      @Override
      public Color getErrorStripeMarkColor() {
        return null;
      }

      @Override
      public void setErrorStripeMarkColor(@Nullable Color color) {
      }

      @Nullable
      @Override
      public Object getErrorStripeTooltip() {
        return null;
      }

      @Override
      public void setErrorStripeTooltip(@Nullable Object tooltipObject) {
      }

      @Override
      public boolean isThinErrorStripeMark() {
        return false;
      }

      @Override
      public void setThinErrorStripeMark(boolean value) {
      }

      @Nullable
      @Override
      public Color getLineSeparatorColor() {
        return null;
      }

      @Override
      public void setLineSeparatorColor(@Nullable Color color) {
      }

      @Override
      public void setLineSeparatorRenderer(LineSeparatorRenderer renderer) {
      }

      @Override
      public LineSeparatorRenderer getLineSeparatorRenderer() {
        return null;
      }

      @Nullable
      @Override
      public SeparatorPlacement getLineSeparatorPlacement() {
        return null;
      }

      @Override
      public void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement) {
      }

      @Override
      public void setEditorFilter(@NotNull MarkupEditorFilter filter) {
      }

      @NotNull
      @Override
      public MarkupEditorFilter getEditorFilter() {
        return MarkupEditorFilter.EMPTY;
      }

      @Override
      public RangeHighlighterEx get() {
        return this;
      }
    }
  }
}