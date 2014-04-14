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
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.Consumer;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @experimental
 */
public final class LanguageConsoleBuilder {
  @Nullable
  private LanguageConsoleView consoleView;
  @Nullable
  private Condition<LanguageConsoleImpl> executionEnabled = Conditions.alwaysTrue();

  @Nullable
  private PairFunction<VirtualFile, Project, PsiFile> psiFileFactory;
  @Nullable
  private BaseConsoleExecuteActionHandler executeActionHandler;
  @Nullable
  private String historyType;

  @Nullable
  private GutterContentProvider gutterContentProvider;

  private boolean oneLineInput;

  // todo to be removed
  public LanguageConsoleBuilder(@SuppressWarnings("NullableProblems") @NotNull LanguageConsoleView consoleView) {
    this.consoleView = consoleView;
  }

  public LanguageConsoleBuilder() {
  }

  public LanguageConsoleBuilder processHandler(@NotNull ProcessHandler processHandler) {
    //noinspection deprecation
    executionEnabled = new ProcessBackedExecutionEnabledCondition(processHandler);
    return this;
  }

  public LanguageConsoleBuilder executionEnabled(@NotNull Condition<LanguageConsoleImpl> condition) {
    executionEnabled = condition;
    return this;
  }

  /**
   * @see {@link com.intellij.psi.PsiCodeFragment}
   */
  public LanguageConsoleBuilder psiFileFactory(@NotNull PairFunction<VirtualFile, Project, PsiFile> value) {
    psiFileFactory = value;
    return this;
  }

  public LanguageConsoleBuilder initActions(@NotNull BaseConsoleExecuteActionHandler executeActionHandler, @NotNull String historyType) {
    if (consoleView == null) {
      this.executeActionHandler = executeActionHandler;
      this.historyType = historyType;
    }
    else {
      doInitAction(consoleView, executeActionHandler, historyType);
    }
    return this;
  }

  private void doInitAction(@NotNull LanguageConsoleView consoleView, @NotNull BaseConsoleExecuteActionHandler executeActionHandler, @NotNull String historyType) {
    ConsoleExecuteAction action = new ConsoleExecuteAction(consoleView, executeActionHandler, executionEnabled);
    action.registerCustomShortcutSet(action.getShortcutSet(), consoleView.getConsole().getConsoleEditor().getComponent());

    new ConsoleHistoryController(historyType, null, consoleView.getConsole(), executeActionHandler.getConsoleHistoryModel()).install();
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

  public LanguageConsoleBuilder gutterContentProvider(@Nullable GutterContentProvider value) {
    gutterContentProvider = value;
    return this;
  }

  /**
   * @see {@link com.intellij.openapi.editor.ex.EditorEx#setOneLineMode(boolean)}
   */
  public LanguageConsoleBuilder oneLineInput() {
    oneLineInput(true);
    return this;
  }

  /**
   * @see {@link com.intellij.openapi.editor.ex.EditorEx#setOneLineMode(boolean)}
   */
  public LanguageConsoleBuilder oneLineInput(boolean value) {
    oneLineInput = value;
    return this;
  }

  public LanguageConsoleView build(@NotNull Project project, @NotNull Language language) {
    GutteredLanguageConsole console = new GutteredLanguageConsole(language.getDisplayName() + " Console", project, language, gutterContentProvider, psiFileFactory);
    if (oneLineInput) {
      console.getConsoleEditor().setOneLineMode(true);
    }
    LanguageConsoleViewImpl consoleView = new LanguageConsoleViewImpl(console, true);
    if (executeActionHandler != null) {
      assert historyType != null;
      doInitAction(consoleView, executeActionHandler, historyType);
    }
    console.initComponents();
    return consoleView;
  }

  @Deprecated
  /**
   * @deprecated Don't use it directly!
   * Will be private in IDEA >13
   */
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

  private final static class GutteredLanguageConsole extends LanguageConsoleImpl {
    private final GutterContentProvider gutterContentProvider;
    @Nullable
    private final PairFunction<VirtualFile, Project, PsiFile> psiFileFactory;

    public GutteredLanguageConsole(@NotNull String title,
                                   @NotNull Project project,
                                   @NotNull Language language,
                                   @Nullable GutterContentProvider gutterContentProvider,
                                   @Nullable PairFunction<VirtualFile, Project, PsiFile> psiFileFactory) {
      super(project, title, new LightVirtualFile(title, language, ""), false, psiFileFactory);

      setShowSeparatorLine(false);

      this.gutterContentProvider = gutterContentProvider == null ? new BasicGutterContentProvider() : gutterContentProvider;
      this.psiFileFactory = psiFileFactory;
    }

    @Override
    boolean isHistoryViewerForceAdditionalColumnsUsage() {
      return false;
    }

    @Override
    int getMinHistoryLineCount() {
      return 1;
    }

    @NotNull
    @Override
    protected PsiFile createFile(@NotNull LightVirtualFile virtualFile, @NotNull Document document, @NotNull Project project) {
      if (psiFileFactory == null) {
        return super.createFile(virtualFile, document, project);
      }
      else {
        return psiFileFactory.fun(virtualFile, project);
      }
    }

    @Override
    protected void setupEditorDefault(@NotNull EditorEx editor) {
      super.setupEditorDefault(editor);

      if (editor == getConsoleEditor()) {
        return;
      }

      final ConsoleGutterComponent lineStartGutter = new ConsoleGutterComponent(editor, gutterContentProvider, true);
      final ConsoleGutterComponent lineEndGutter = new ConsoleGutterComponent(editor, gutterContentProvider, false);
      JLayeredPane layeredPane = new JBLayeredPane() {
        @Override
        public Dimension getPreferredSize() {
          Dimension editorSize = getEditorComponent().getPreferredSize();
          return new Dimension(lineStartGutter.getPreferredSize().width + editorSize.width, editorSize.height);
        }

        @Override
        public Dimension getMinimumSize() {
          Dimension editorSize = getEditorComponent().getMinimumSize();
          return new Dimension(lineStartGutter.getPreferredSize().width + editorSize.width, editorSize.height);
        }

        @Override
        public void doLayout() {
          EditorComponentImpl editor = getEditorComponent();
          int w = getWidth();
          int h = getHeight();
          int lineStartGutterWidth = lineStartGutter.getPreferredSize().width;
          lineStartGutter.setBounds(0, 0, lineStartGutterWidth + gutterContentProvider.getLineStartGutterOverlap(editor.getEditor()), h);

          editor.setBounds(lineStartGutterWidth, 0, w - lineStartGutterWidth, h);

          int lineEndGutterWidth = lineEndGutter.getPreferredSize().width;
          lineEndGutter.setBounds(lineStartGutterWidth + (w - lineEndGutterWidth - editor.getEditor().getScrollPane().getVerticalScrollBar().getWidth()), 0, lineEndGutterWidth, h);
        }

        @NotNull
        private EditorComponentImpl getEditorComponent() {
          for (int i = getComponentCount() - 1; i >= 0; i--) {
            Component component = getComponent(i);
            if (component instanceof EditorComponentImpl) {
              return (EditorComponentImpl)component;
            }
          }
          throw new IllegalStateException();
        }
      };

      layeredPane.add(lineStartGutter, JLayeredPane.PALETTE_LAYER);

      JScrollPane scrollPane = editor.getScrollPane();
      layeredPane.add(scrollPane.getViewport().getView(), JLayeredPane.DEFAULT_LAYER);

      layeredPane.add(lineEndGutter, JLayeredPane.PALETTE_LAYER);

      scrollPane.setViewportView(layeredPane);

      GutterUpdateScheduler gutterUpdateScheduler = new GutterUpdateScheduler(lineStartGutter, lineEndGutter);
      getProject().getMessageBus().connect(this).subscribe(DocumentBulkUpdateListener.TOPIC, gutterUpdateScheduler);
      editor.getDocument().addDocumentListener(gutterUpdateScheduler);
    }

    @Override
    protected void doAddPromptToHistory() {
      gutterContentProvider.beforeEvaluate(getHistoryViewer());
    }

    private final class GutterUpdateScheduler extends DocumentAdapter implements DocumentBulkUpdateListener {
      private final ConsoleGutterComponent lineStartGutter;
      private final ConsoleGutterComponent lineEndGutter;

      private Task gutterSizeUpdater;
      private RangeHighlighterEx lineSeparatorPainter;

      public GutterUpdateScheduler(@NotNull ConsoleGutterComponent lineStartGutter, @NotNull ConsoleGutterComponent lineEndGutter) {
        this.lineStartGutter = lineStartGutter;
        this.lineEndGutter = lineEndGutter;

        // console view can invoke markupModel.removeAllHighlighters(), so, we must be aware of it
        getHistoryViewer().getMarkupModel().addMarkupModelListener(GutteredLanguageConsole.this, new MarkupModelListener.Adapter() {
          @Override
          public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
            if (lineSeparatorPainter == highlighter) {
              lineSeparatorPainter = null;
            }
          }
        });
      }

      private void addLineSeparatorPainterIfNeed() {
        if (lineSeparatorPainter != null) {
          return;
        }

        EditorEx editor = getHistoryViewer();
        int endOffset = getDocument().getTextLength();
        lineSeparatorPainter = new LineSeparatorPainter(editor, endOffset);
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
            updateGutterSize(startDocLine, endDocLine);
          }
        }
        else if (event.getOldLength() > 0) {
          documentCleared();
        }
      }

      private void documentCleared() {
        gutterSizeUpdater = null;
        lineEndGutter.documentCleared();
        gutterContentProvider.documentCleared(getHistoryViewer());
      }

      @Override
      public void updateStarted(@NotNull Document document) {
      }

      @Override
      public void updateFinished(@NotNull Document document) {
        if (getDocument().getTextLength() == 0) {
          documentCleared();
        }
        else {
          addLineSeparatorPainterIfNeed();
          updateGutterSize(0, Integer.MAX_VALUE);
        }
      }

      private void updateGutterSize(int start, int end) {
        if (gutterSizeUpdater != null) {
          gutterSizeUpdater.start = Math.min(start, gutterSizeUpdater.start);
          gutterSizeUpdater.end = Math.max(end, gutterSizeUpdater.end);
          return;
        }

        gutterSizeUpdater = new Task(start, end);
        SwingUtilities.invokeLater(gutterSizeUpdater);
      }

      private final class Task implements Runnable {
        private int start;
        private int end;

        public Task(int start, int end) {
          this.start = start;
          this.end = end;
        }

        @Override
        public void run() {
          if (!getHistoryViewer().isDisposed()) {
            lineStartGutter.updateSize(start, end);
            lineEndGutter.updateSize(start, end);
          }
          gutterSizeUpdater = null;
        }
      }
    }

    private final class LineSeparatorPainter extends RangeMarkerImpl implements RangeHighlighterEx, Getter<RangeHighlighterEx> {
      private final CustomHighlighterRenderer renderer = new CustomHighlighterRenderer() {
        @Override
        public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
          Rectangle clip = g.getClipBounds();
          int lineHeight = editor.getLineHeight();
          int startLine = clip.y / lineHeight;
          int endLine = Math.min(((clip.y + clip.height) / lineHeight) + 1, ((EditorImpl)editor).getVisibleLineCount());
          if (startLine >= endLine) {
            return;
          }

          // workaround - editor ask us to paint line 4-6, but we should draw line for line 3 (startLine - 1) also, otherwise it will be not rendered
          int actualStartLine = startLine == 0 ? 0 : startLine - 1;
          int y = (actualStartLine + 1) * lineHeight;
          g.setColor(editor.getColorsScheme().getColor(EditorColors.INDENT_GUIDE_COLOR));
          for (int visualLine = actualStartLine; visualLine < endLine; visualLine++) {
            if (gutterContentProvider.isShowSeparatorLine(editor.visualToLogicalPosition(new VisualPosition(visualLine, 0)).line, editor)) {
              g.drawLine(0, y, clip.width, y);
            }
            y += lineHeight;
          }
        }
      };

      private final MarkupModelEx markupModel;

      public LineSeparatorPainter(@NotNull EditorEx editor, int endOffset) {
        super(editor.getDocument(), 0, endOffset, false);

        markupModel = editor.getMarkupModel();
        registerInTree(0, endOffset, false, false, HighlighterLayer.ADDITIONAL_SYNTAX);
      }

      @Override
      protected void changedUpdateImpl(DocumentEvent e) {
        setIntervalEnd(myDocument.getTextLength());
      }

      @Override
      public boolean setValid(boolean value) {
        return super.setValid(value);
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

      @Override
      protected void registerInTree(int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
        markupModel.addRangeHighlighter(this, start, end, greedyToLeft, greedyToRight, layer);
      }

      @Override
      protected boolean unregisterInTree() {
        if (!isValid()) {
          return false;
        }

        // we store highlighters in MarkupModel
        markupModel.removeHighlighter(this);
        return true;
      }
    }
  }
}