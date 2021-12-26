// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.util.DocumentUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * @author Gregory.Shrago
 * In case of REPL consider to use {@link LanguageConsoleBuilder}
 */
public class LanguageConsoleImpl extends ConsoleViewImpl implements LanguageConsoleView, DataProvider {
  private final Helper myHelper;

  private final ConsoleExecutionEditor myConsoleExecutionEditor;
  private final EditorEx myHistoryViewer;
  private final JPanel myPanel = new ConsoleEditorsPanel(this);
  private final JScrollBar myScrollBar = new JBScrollBar(Adjustable.HORIZONTAL);
  private final MergedHorizontalScrollBarModel myMergedScrollBarModel;
  private final DocumentListener myDocumentAdapter = new DocumentListener() {
    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      myPanel.revalidate();
    }
  };

  public LanguageConsoleImpl(@NotNull Project project, @NotNull String title, @NotNull Language language) {
    this(new Helper(project, new LightVirtualFile(title, language, "")));
  }

  public LanguageConsoleImpl(@NotNull Project project, @NotNull String title, @NotNull VirtualFile virtualFile) {
    this(new Helper(project, virtualFile).setTitle(title));
  }

  public LanguageConsoleImpl(@NotNull Helper helper) {
    super(helper.project, GlobalSearchScope.allScope(helper.project), true, true);
    myHelper = helper;
    EditorFactory editorFactory = EditorFactory.getInstance();
    myConsoleExecutionEditor = new ConsoleExecutionEditor(helper);
    Disposer.register(this, myConsoleExecutionEditor);
    myHistoryViewer = doCreateHistoryEditor();
    myHistoryViewer.getDocument().addDocumentListener(myDocumentAdapter);
    myConsoleExecutionEditor.getDocument().addDocumentListener(myDocumentAdapter);
    myMergedScrollBarModel = MergedHorizontalScrollBarModel.create(myScrollBar, myHistoryViewer, myConsoleExecutionEditor.getEditor());
    myScrollBar.putClientProperty(Alignment.class, Alignment.BOTTOM);
  }

  @NotNull
  protected EditorEx doCreateHistoryEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document historyDocument = ((EditorFactoryImpl)editorFactory).createDocument(true);
    UndoUtil.disableUndoFor(historyDocument);
    return (EditorEx)editorFactory.createViewer(historyDocument, getProject(), EditorKind.CONSOLE);
  }

  @NotNull
  @Override
  protected final EditorEx doCreateConsoleEditor() {
    return myHistoryViewer;
  }

  @Override
  protected final void disposeEditor() {
  }

  @NotNull
  @Override
  protected JComponent createCenterComponent() {
    initComponents();
    return myPanel;
  }

  @Override
  public @NotNull JComponent getPreferredFocusableComponent() {
    return getConsoleEditor().getContentComponent();
  }

  private void initComponents() {
    setupComponents();

    myPanel.setLayout(new EditorMergedHorizontalScrollBarLayout(myScrollBar, myHistoryViewer, myConsoleExecutionEditor.getEditor(),
                                                                isHistoryViewerForceAdditionalColumnsUsage(), getMinHistoryLineCount()));
    myPanel.add(myHistoryViewer.getComponent());
    myPanel.add(myConsoleExecutionEditor.getComponent());
    myPanel.add(myScrollBar);
    myPanel.setBackground(JBColor.lazy(() -> myConsoleExecutionEditor.getEditor().getBackgroundColor()));
    DataManager.registerDataProvider(myPanel, this);
  }

  @Override
  public void setConsoleEditorEnabled(boolean consoleEditorEnabled) {
    if (isConsoleEditorEnabled() == consoleEditorEnabled) {
      return;
    }
    myConsoleExecutionEditor.setConsoleEditorEnabled(consoleEditorEnabled);
    myMergedScrollBarModel.setEnabled(consoleEditorEnabled);
  }

  private void setupComponents() {
    myHelper.setupEditor(myConsoleExecutionEditor.getEditor());
    myHelper.setupEditor(myHistoryViewer);

    myHistoryViewer.getComponent().setMinimumSize(JBUI.emptySize());
    myHistoryViewer.getComponent().setPreferredSize(JBUI.emptySize());
    myHistoryViewer.setCaretEnabled(false);

    myConsoleExecutionEditor.initComponent();

    myHistoryViewer.getContentComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent event) {
        if (isConsoleEditorEnabled() && UIUtil.isReallyTypedEvent(event)) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
            () -> IdeFocusManager.getGlobalInstance().requestFocus(myConsoleExecutionEditor.getEditor().getContentComponent(), true)
          );
          myConsoleExecutionEditor.getEditor().processKeyTyped(event);
        }
      }
    });

    EmptyAction.registerActionShortcuts(myHistoryViewer.getComponent(), myConsoleExecutionEditor.getComponent());
    myHistoryViewer.putUserData(EXECUTION_EDITOR_KEY, myConsoleExecutionEditor);
  }

  @Override
  public final boolean isConsoleEditorEnabled() {
    return myConsoleExecutionEditor.isConsoleEditorEnabled();
  }

  @Override
  @Nullable
  public String getPrompt() {
    return myConsoleExecutionEditor.getPrompt();
  }

  @Override
  @Nullable
  public ConsoleViewContentType getPromptAttributes() {
    return myConsoleExecutionEditor.getPromptAttributes();
  }


  @NotNull
  public ConsolePromptDecorator getConsolePromptDecorator() {
    return myConsoleExecutionEditor.getConsolePromptDecorator();
  }

  @Override
  public void setPromptAttributes(@NotNull ConsoleViewContentType textAttributes) {
    myConsoleExecutionEditor.setPromptAttributes(textAttributes);
  }

  @Override
  public void setPrompt(@Nullable String prompt) {
    myConsoleExecutionEditor.setPrompt(prompt);
  }

  @Override
  public void setEditable(boolean editable) {
   myConsoleExecutionEditor.setEditable(editable);
  }

  @Override
  public boolean isEditable() {
    return myConsoleExecutionEditor.isEditable();
  }

  @Override
  @NotNull
  public final PsiFile getFile() {
    return myHelper.getFileSafe();
  }

  @Override
  @NotNull
  public final VirtualFile getVirtualFile() {
    return myConsoleExecutionEditor.getVirtualFile();
  }

  @Override
  @NotNull
  public final EditorEx getHistoryViewer() {
    return myHistoryViewer;
  }

  @Override
  @NotNull
  public final Document getEditorDocument() {
    return myConsoleExecutionEditor.getDocument();
  }

  @Override
  @NotNull
  public final EditorEx getConsoleEditor() {
    return myConsoleExecutionEditor.getEditor();
  }

  @Override
  public @NlsContexts.TabTitle @NotNull String getTitle() {
    return myHelper.title;
  }

  @Override
  public void setTitle(@NotNull String title) {
    myHelper.setTitle(title);
  }

  public String addToHistory(@NotNull TextRange textRange, @NotNull EditorEx editor, boolean preserveMarkup) {
    return addToHistoryInner(textRange, editor, false, preserveMarkup);
  }

  @NotNull
  public String prepareExecuteAction(boolean addToHistory, boolean preserveMarkup, boolean clearInput) {
    EditorEx editor = getCurrentEditor();
    Document document = editor.getDocument();
    String text = document.getText();
    TextRange range = new TextRange(0, document.getTextLength());
    if (!clearInput) {
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }

    if (addToHistory) {
      addToHistoryInner(range, editor, clearInput, preserveMarkup);
    }
    else if (clearInput) {
      setInputText("");
    }
    return text;
  }

  @NotNull
  protected String addToHistoryInner(@NotNull final TextRange textRange, @NotNull final EditorEx editor, boolean erase, final boolean preserveMarkup) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    String result = addTextRangeToHistory(textRange, editor, preserveMarkup);
    if (erase) {
      DocumentUtil.writeInRunUndoTransparentAction(
        () -> editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset()));
    }
    // always scroll to end on user input
    scrollToEnd();
    return result;
  }

  public static String printWithHighlighting(@NotNull LanguageConsoleView console, @NotNull Editor inputEditor, @NotNull TextRange textRange) {
    String text;
    EditorHighlighter highlighter;
    if (inputEditor instanceof EditorWindow) {
      PsiFile file = ((EditorWindow)inputEditor).getInjectedFile();
      highlighter =
        HighlighterFactory.createHighlighter(file.getVirtualFile(), EditorColorsManager.getInstance().getGlobalScheme(), console.getProject());
      String fullText = InjectedLanguageUtilBase.getUnescapedText(file, null, null);
      highlighter.setText(fullText);
      text = textRange.substring(fullText);
    }
    else {
      text = inputEditor.getDocument().getText(textRange);
      highlighter = inputEditor.getHighlighter();
    }
    SyntaxHighlighter syntax =
      highlighter instanceof LexerEditorHighlighter ? ((LexerEditorHighlighter)highlighter).getSyntaxHighlighter() : null;
    LanguageConsoleImpl consoleImpl = (LanguageConsoleImpl)console;
    consoleImpl.doAddPromptToHistory();
    if (syntax != null) {
      ConsoleViewUtil.printWithHighlighting(console, text, syntax, () -> {
        String identPrompt = consoleImpl.myConsoleExecutionEditor.getConsolePromptDecorator().getIndentPrompt();
        if (StringUtil.isNotEmpty(identPrompt)) {
          consoleImpl.addPromptToHistoryImpl(identPrompt);
        }
      });
    }
    else {
      console.print(text, ConsoleViewContentType.USER_INPUT);
    }
    console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
    return text;
  }

  @NotNull
  protected String addTextRangeToHistory(@NotNull TextRange textRange, @NotNull EditorEx inputEditor, boolean preserveMarkup) {
    return printWithHighlighting(this, inputEditor, textRange);


    //if (preserveMarkup) {
    //  duplicateHighlighters(markupModel, DocumentMarkupModel.forDocument(inputEditor.getDocument(), myProject, true), offset, textRange);
    //  // don't copy editor markup model, i.e. brace matcher, spell checker, etc.
    //  // duplicateHighlighters(markupModel, inputEditor.getMarkupModel(), offset, textRange);
    //}
  }

  protected void doAddPromptToHistory() {
    String prompt = myConsoleExecutionEditor.getPrompt();
    if (prompt != null) {
      addPromptToHistoryImpl(prompt);
    }
  }

    public static void duplicateHighlighters(@NotNull MarkupModel to, @NotNull MarkupModel from, int offset, @NotNull TextRange textRange, @Nullable String... disableAttributes) {
    for (RangeHighlighter rangeHighlighter : from.getAllHighlighters()) {
      if (!rangeHighlighter.isValid()) {
        continue;
      }
      Object tooltip = rangeHighlighter.getErrorStripeTooltip();
      HighlightInfo highlightInfo = tooltip instanceof HighlightInfo? (HighlightInfo)tooltip : null;
      if (highlightInfo != null) {
        if (highlightInfo.getSeverity() != HighlightSeverity.INFORMATION) {
          continue;
        }
          if (highlightInfo.type.getAttributesKey() == EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES) {
              continue;
          }

        if(Arrays.stream(disableAttributes).filter(Objects::nonNull).anyMatch(x -> x.equals(highlightInfo.type .getAttributesKey().getExternalName())))
            continue;
      }
      int localOffset = textRange.getStartOffset();
      int start = Math.max(rangeHighlighter.getStartOffset(), localOffset) - localOffset;
      int end = Math.min(rangeHighlighter.getEndOffset(), textRange.getEndOffset()) - localOffset;
      if (start > end) {
        continue;
      }
      RangeHighlighter h = to.addRangeHighlighter(start + offset, end + offset, rangeHighlighter.getLayer(),
                                                  rangeHighlighter.getTextAttributes(null), rangeHighlighter.getTargetArea());
      ((RangeHighlighterEx)h).setAfterEndOfLine(((RangeHighlighterEx)rangeHighlighter).isAfterEndOfLine());
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    // double dispose via RunContentDescriptor and ContentImpl
    if (myHistoryViewer.isDisposed()) return;

    myConsoleExecutionEditor.getDocument().removeDocumentListener(myDocumentAdapter);
    myHistoryViewer.getDocument().removeDocumentListener(myDocumentAdapter);
    myHistoryViewer.putUserData(EXECUTION_EDITOR_KEY, null);

    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myHistoryViewer);

    closeFile();
  }

  protected void closeFile() {
    if (getProject().isOpen()) {
      FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
      if (editorManager.isFileOpen(getVirtualFile())) {
        editorManager.closeFile(getVirtualFile());
      }
    }
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    return super.getData(dataId);
  }


  @Override
  @NotNull
  public EditorEx getCurrentEditor() {
    return myConsoleExecutionEditor.getCurrentEditor();
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return getFile().getLanguage();
  }

  @Override
  public void setLanguage(@NotNull Language language) {
    myHelper.setLanguage(language);
    myHelper.getFileSafe();
  }

  @Override
  public void setInputText(@NotNull final String query) {
    myConsoleExecutionEditor.setInputText(query);
  }

  boolean isHistoryViewerForceAdditionalColumnsUsage() {
    return true;
  }

  protected int getMinHistoryLineCount() {
    return 2;
  }

  private void addPromptToHistoryImpl(@NotNull String prompt) {
    flushDeferredText();
    DocumentEx document = getHistoryViewer().getDocument();
    RangeHighlighter highlighter =
      this.getHistoryViewer().getMarkupModel().addRangeHighlighter(null, document.getTextLength(), document.getTextLength(), 0,
                                                                   HighlighterTargetArea.EXACT_RANGE);
    print(prompt, myConsoleExecutionEditor.getPromptAttributes());
    highlighter.putUserData(ConsoleHistoryCopyHandler.PROMPT_LENGTH_MARKER, prompt.length());
  }

  public static class Helper {
    public final Project project;
    public final VirtualFile virtualFile;
    @NlsSafe String title;
    PsiFile file;

    public Helper(@NotNull Project project, @NotNull VirtualFile virtualFile) {
      this.project = project;
      this.virtualFile = virtualFile;
      title = virtualFile.getName();
    }

    public String getTitle() {
      return this.title;
    }

    public Helper setTitle(@NotNull String title) {
      this.title = title;
      return this;
    }

    @NotNull
    public PsiFile getFile() {
      return ReadAction.compute(() -> PsiUtilCore.getPsiFile(project, virtualFile));
    }

    @NotNull
    public Document getDocument() {
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null) {
        Language language = (virtualFile instanceof LightVirtualFile) ? ((LightVirtualFile)virtualFile).getLanguage() : null;
        throw new AssertionError(String.format("no document for: %s (fileType: %s, language: %s, length: %s, valid: %s)",
                                               virtualFile,
                                               virtualFile.getFileType(), language, virtualFile.getLength(), virtualFile.isValid()));
      }
      return document;
    }

    public void setLanguage(Language language) {
      if (!(virtualFile instanceof LightVirtualFile)) {
        throw new UnsupportedOperationException();
      }
      ((LightVirtualFile)virtualFile).setLanguage(language);
      ((LightVirtualFile)virtualFile).setContent(getDocument(), getDocument().getText(), false);
      FileContentUtil.reparseFiles(project, Collections.singletonList(virtualFile), false);
    }

    public void setupEditor(@NotNull EditorEx editor) {
      ConsoleViewUtil.setupLanguageConsoleEditor(editor);

      editor.setHorizontalScrollbarVisible(true);
      editor.setVerticalScrollbarVisible(true);

      DataManager.registerDataProvider(editor.getComponent(), (dataId) -> getEditorData(editor, dataId));
    }

    @NotNull
    public PsiFile getFileSafe() {
      return file == null || !file.isValid() ? file = getFile() : file;
    }

    @Nullable
    protected Object getEditorData(@NotNull EditorEx editor, String dataId) {
      if (OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
        return editor;
      }
      else if (project.isInitialized()) {
        Caret caret = editor.getCaretModel().getCurrentCaret();
        return FileEditorManagerEx.getInstanceEx(project).getData(dataId, editor, caret);
      }
      return null;
    }
  }

  public static class ConsoleEditorsPanel extends JPanel {
    private final LanguageConsoleImpl myConsole;

    public ConsoleEditorsPanel(@NotNull LanguageConsoleImpl console) {
      myConsole = console;
    }

    @NotNull
    public LanguageConsoleImpl getConsole() {
      return myConsole;
    }
  }
}
