/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.console;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.util.*;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Gregory.Shrago
 * In case of REPL consider to use {@link LanguageConsoleBuilder}
 */
public class LanguageConsoleImpl implements Disposable, TypeSafeDataProvider {
  private static final int SEPARATOR_THICKNESS = 1;
  private final Project myProject;

  private final EditorEx myConsoleEditor;
  private final EditorEx myHistoryViewer;
  private final Document myEditorDocument;
  private final LightVirtualFile myVirtualFile;

  protected PsiFile myFile; // will change on language change

  private final JPanel myPanel = new JPanel(new MyLayout());
  private String myTitle;
  @Nullable
  private String myPrompt = "> ";
  private final LightVirtualFile myHistoryFile;
  private Editor myCurrentEditor;

  private final AtomicBoolean myForceScrollToEnd = new AtomicBoolean(false);
  private final SingleAlarm myUpdateQueue;
  private Runnable myUiUpdateRunnable;

  private boolean myShowSeparatorLine = true;

  private final FocusChangeListener myFocusListener = new FocusChangeListener() {
    @Override
    public void focusGained(Editor editor) {
      myCurrentEditor = editor;
    }

    @Override
    public void focusLost(Editor editor) {
    }
  };

  public LanguageConsoleImpl(@NotNull Project project, @NotNull String title, @NotNull Language language) {
    this(project, title, language, true);
  }

  public LanguageConsoleImpl(@NotNull Project project, @NotNull String title, @NotNull Language language, boolean initComponents) {
    this(project, title, new LightVirtualFile(title, language, ""), initComponents);
  }

  public LanguageConsoleImpl(@NotNull Project project, @NotNull String title, @NotNull LightVirtualFile lightFile, boolean initComponents) {
    this(project, title, lightFile, initComponents, null);
  }

  LanguageConsoleImpl(@NotNull Project project,
                      @NotNull String title,
                      @NotNull LightVirtualFile lightFile,
                      boolean initComponents,
                      @Nullable PairFunction<VirtualFile, Project, PsiFile> psiFileFactory) {
    myProject = project;
    myTitle = title;
    myVirtualFile = lightFile;
    EditorFactory editorFactory = EditorFactory.getInstance();
    myHistoryFile = new LightVirtualFile(getTitle() + ".history.txt", FileTypes.PLAIN_TEXT, "");
    myEditorDocument = FileDocumentManager.getInstance().getDocument(lightFile);
    assert myEditorDocument != null;
    myFile = psiFileFactory == null ? createFile(myVirtualFile, myEditorDocument, myProject) : psiFileFactory.fun(myVirtualFile, myProject);
    myConsoleEditor = (EditorEx)editorFactory.createEditor(myEditorDocument, myProject);
    myConsoleEditor.addFocusListener(myFocusListener);
    myCurrentEditor = myConsoleEditor;
    myHistoryViewer = (EditorEx)editorFactory.createViewer(((EditorFactoryImpl)editorFactory).createDocument(true), myProject);
    myUpdateQueue = new SingleAlarm(new Runnable() {
      @Override
      public void run() {
        if (isConsoleEditorEnabled()) {
          myPanel.revalidate();
          myPanel.repaint();
        }
        if (myUiUpdateRunnable != null) {
          myUiUpdateRunnable.run();
        }
      }
    }, 300, this);

    // action shortcuts are not yet registered
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        installEditorFactoryListener();
      }
    }, myProject.getDisposed());

    if (initComponents) {
      initComponents();
    }
  }

  public void initComponents() {
    setupComponents();

    myPanel.add(myHistoryViewer.getComponent());
    myPanel.add(myConsoleEditor.getComponent());

    DataManager.registerDataProvider(myPanel, new TypeSafeDataProviderAdapter(this));

    myHistoryViewer.getComponent().addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (myForceScrollToEnd.compareAndSet(true, false)) {
          scrollHistoryToEnd();
        }
      }

      @Override
      public void componentShown(ComponentEvent e) {
        componentResized(e);
      }
    });
    setPromptInner(myPrompt);
  }

  public void setConsoleEditorEnabled(boolean consoleEditorEnabled) {
    if (isConsoleEditorEnabled() == consoleEditorEnabled) {
      return;
    }

    if (consoleEditorEnabled) {
      FileEditorManager.getInstance(getProject()).closeFile(myVirtualFile);
      myPanel.removeAll();
      myPanel.add(myHistoryViewer.getComponent());
      myPanel.add(myConsoleEditor.getComponent());

      myHistoryViewer.setHorizontalScrollbarVisible(false);
      myCurrentEditor = myConsoleEditor;
    }
    else {
      myPanel.removeAll();
      myPanel.add(myHistoryViewer.getComponent(), BorderLayout.CENTER);
      myHistoryViewer.setHorizontalScrollbarVisible(true);
    }
  }

  public void setShowSeparatorLine(boolean showSeparatorLine) {
    myShowSeparatorLine = showSeparatorLine;
  }

  private void setupComponents() {
    setupEditorDefault(myConsoleEditor);
    setupEditorDefault(myHistoryViewer);

    //noinspection ConstantConditions
    if (SEPARATOR_THICKNESS > 0 && myShowSeparatorLine) {
      myHistoryViewer.getComponent().setBorder(new SideBorder(JBColor.LIGHT_GRAY, SideBorder.BOTTOM));
    }
    myHistoryViewer.getComponent().setMinimumSize(new Dimension(0, 0));
    myHistoryViewer.getComponent().setPreferredSize(new Dimension(0, 0));
    myHistoryViewer.setCaretEnabled(false);

    myConsoleEditor.addEditorMouseListener(EditorActionUtil.createEditorPopupHandler(IdeActions.GROUP_CONSOLE_EDITOR_POPUP));
    myConsoleEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(myVirtualFile, myConsoleEditor.getColorsScheme(), myProject));

    myConsoleEditor.getScrollingModel().addVisibleAreaListener(new VisibleAreaListener() {
      @Override
      public void visibleAreaChanged(VisibleAreaEvent e) {
        final int offset = myConsoleEditor.getScrollingModel().getHorizontalScrollOffset();
        final ScrollingModel model = myHistoryViewer.getScrollingModel();
        final int historyOffset = model.getHorizontalScrollOffset();
        if (historyOffset != offset) {
          try {
            model.disableAnimation();
            model.scrollHorizontally(offset);
          }
          finally {
            model.enableAnimation();
          }
        }
      }
    });
    final DocumentAdapter docListener = new DocumentAdapter() {
      @Override
      public void documentChanged(final DocumentEvent e) {
        queueUiUpdate(false);
      }
    };
    myEditorDocument.addDocumentListener(docListener, this);
    myHistoryViewer.getDocument().addDocumentListener(docListener, this);

    myHistoryViewer.getContentComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent event) {
        if (isConsoleEditorEnabled() && UIUtil.isReallyTypedEvent(event)) {
          myConsoleEditor.getContentComponent().requestFocus();
          myConsoleEditor.processKeyTyped(event);
        }
      }
    });

    //noinspection deprecation
    for (AnAction action : createActions()) {
      action.registerCustomShortcutSet(action.getShortcutSet(), myConsoleEditor.getComponent());
    }
    EmptyAction.registerActionShortcuts(myHistoryViewer.getComponent(), myConsoleEditor.getComponent());
  }

  public boolean isConsoleEditorEnabled() {
    return myPanel.getComponentCount() > 1;
  }

  @NotNull
  @Deprecated
  /**
   * @deprecated LanguageConsoleImpl is not intended to be extended
   */
  protected AnAction[] createActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * @deprecated Use {@link #setInputText}
   * to remove in IDEA 15
   */
  public void setTextToEditor(@NotNull String text) {
    setInputText(text);
  }

  protected void setupEditorDefault(@NotNull EditorEx editor) {
    ConsoleViewUtil.setupConsoleEditor(editor, false, false);
    editor.getContentComponent().setFocusCycleRoot(false);
    editor.setHorizontalScrollbarVisible(false);
    editor.setVerticalScrollbarVisible(true);
    editor.setBorder(null);

    final EditorSettings editorSettings = editor.getSettings();
    if (myHistoryViewer != editor) {
      editorSettings.setAdditionalLinesCount(1);
    }
    editorSettings.setAdditionalColumnsCount(1);
  }

  public void setUiUpdateRunnable(Runnable uiUpdateRunnable) {
    assert myUiUpdateRunnable == null : "can be set only once";
    myUiUpdateRunnable = uiUpdateRunnable;
  }

  public void flushAllUiUpdates() {
    myUpdateQueue.flush();
  }

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public LightVirtualFile getHistoryFile() {
    return myHistoryFile;
  }

  @Nullable
  public String getPrompt() {
    return myPrompt;
  }

  public void setPrompt(@Nullable String prompt) {
    // always add space to the prompt otherwise it may look ugly
    myPrompt = prompt != null && !prompt.endsWith(" ") ? prompt + " " : prompt;
    setPromptInner(myPrompt);
  }

  private void setPromptInner(@Nullable final String prompt) {
    myUpdateQueue.checkDisposed();

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        myConsoleEditor.setPrefixTextAndAttributes(prompt, ConsoleViewContentType.USER_INPUT.getAttributes());
        if (myPanel.isVisible()) {
          queueUiUpdate(false);
        }
      }
    });
  }

  public void setEditable(boolean editable) {
    myConsoleEditor.setRendererMode(!editable);
    setPromptInner(editable ? myPrompt : "");
  }

  public boolean isEditable() {
    return !myConsoleEditor.isRendererMode();
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @NotNull
  public EditorEx getHistoryViewer() {
    return myHistoryViewer;
  }

  @NotNull
  public Document getEditorDocument() {
    return myEditorDocument;
  }

  @NotNull
  public EditorEx getConsoleEditor() {
    return myConsoleEditor;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  public void setTitle(@NotNull String title) {
    myTitle = title;
  }

  public void printToHistory(@NotNull CharSequence text, @NotNull TextAttributes attributes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    text = StringUtilRt.unifyLineSeparators(text);
    final boolean scrollToEnd = shouldScrollHistoryToEnd();
    addTextToHistory(text, attributes);
    if (scrollToEnd) {
      scrollHistoryToEnd();
    }
    queueUiUpdate(scrollToEnd);
  }

  protected void addTextToHistory(@Nullable CharSequence text, @Nullable TextAttributes attributes) {
    if (StringUtil.isEmpty(text) || attributes == null) {
      return;
    }

    Document history = myHistoryViewer.getDocument();
    int offset = appendToHistoryDocument(history, text);
    DocumentMarkupModel.forDocument(history, myProject, true).addRangeHighlighter(offset, offset + text.length(), HighlighterLayer.SYNTAX, attributes,
                                                                                  HighlighterTargetArea.EXACT_RANGE);
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * @deprecated Use {@link LanguageConsoleBuilder},
   * {@link LanguageConsoleBuilder#registerExecuteAction)} or
   * {@link ConsoleExecuteAction#prepareRunExecuteAction)}
   *
   * to remove in IDEA 15
   */
  public String addCurrentToHistory(@NotNull TextRange textRange, boolean erase, boolean preserveMarkup) {
    return addToHistoryInner(textRange, myConsoleEditor, erase, preserveMarkup);
  }

  public String addToHistory(@NotNull TextRange textRange, @NotNull EditorEx editor, boolean preserveMarkup) {
    return addToHistoryInner(textRange, editor, false, preserveMarkup);
  }

  @NotNull
  public String prepareExecuteAction(boolean addToHistory, boolean preserveMarkup, boolean clearInput) {
    Editor editor = getCurrentEditor();
    Document document = editor.getDocument();
    String text = document.getText();
    TextRange range = new TextRange(0, document.getTextLength());
    if (!clearInput) {
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }

    if (addToHistory) {
      addToHistoryInner(range, myConsoleEditor, clearInput, preserveMarkup);
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
      DocumentUtil.writeInRunUndoTransparentAction(new Runnable() {
        @Override
        public void run() {
          editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
        }
      });
    }
    // always scroll to end on user input
    scrollHistoryToEnd();
    queueUiUpdate(true);
    return result;
  }

  public boolean shouldScrollHistoryToEnd() {
    final Rectangle visibleArea = myHistoryViewer.getScrollingModel().getVisibleArea();
    final Dimension contentSize = myHistoryViewer.getContentSize();
    return contentSize.getHeight() - visibleArea.getMaxY() < (getMinHistoryLineCount() * myHistoryViewer.getLineHeight());
  }

  private void scrollHistoryToEnd() {
    if (myHistoryViewer.getDocument().getTextLength() != 0) {
      EditorUtil.scrollToTheEnd(myHistoryViewer);
    }
  }

  @NotNull
  protected String addTextRangeToHistory(@NotNull TextRange textRange, @NotNull EditorEx inputEditor, boolean preserveMarkup) {
    doAddPromptToHistory();

    final Document history = myHistoryViewer.getDocument();
    final MarkupModel markupModel = DocumentMarkupModel.forDocument(history, myProject, true);
    final int localStartOffset = textRange.getStartOffset();
    String text;
    EditorHighlighter highlighter;
    if (inputEditor instanceof EditorWindow) {
      PsiFile file = ((EditorWindow)inputEditor).getInjectedFile();
      highlighter = HighlighterFactory.createHighlighter(file.getVirtualFile(), EditorColorsManager.getInstance().getGlobalScheme(), getProject());
      String fullText = InjectedLanguageUtil.getUnescapedText(file, null, null);
      highlighter.setText(fullText);
      text = textRange.substring(fullText);
    }
    else {
      text = inputEditor.getDocument().getText(textRange);
      highlighter = inputEditor.getHighlighter();
    }
    //offset can be changed after text trimming after insert due to buffer constraints
    int offset = appendToHistoryDocument(history, text);

    final HighlighterIterator iterator = highlighter.createIterator(localStartOffset);
    final int localEndOffset = textRange.getEndOffset();
    while (!iterator.atEnd()) {
      final int itStart = iterator.getStart();
      if (itStart > localEndOffset) {
        break;
      }
      final int itEnd = iterator.getEnd();
      if (itEnd >= localStartOffset) {
        final int start = Math.max(itStart, localStartOffset) - localStartOffset + offset;
        final int end = Math.min(itEnd, localEndOffset) - localStartOffset + offset;
        markupModel.addRangeHighlighter(start, end, HighlighterLayer.SYNTAX, iterator.getTextAttributes(),
                                        HighlighterTargetArea.EXACT_RANGE);
      }
      iterator.advance();
    }
    if (preserveMarkup) {
      duplicateHighlighters(markupModel, DocumentMarkupModel.forDocument(inputEditor.getDocument(), myProject, true), offset, textRange);
      // don't copy editor markup model, i.e. brace matcher, spell checker, etc.
      // duplicateHighlighters(markupModel, inputEditor.getMarkupModel(), offset, textRange);
    }
    if (!text.endsWith("\n")) {
      appendToHistoryDocument(history, "\n");
    }
    return text;
  }

  protected void doAddPromptToHistory() {
    addTextToHistory(myPrompt, ConsoleViewContentType.USER_INPUT.getAttributes());
  }

  // returns the real (cyclic-buffer-aware) start offset of the inserted text
  protected int appendToHistoryDocument(@NotNull Document history, @NotNull CharSequence text) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    history.insertString(history.getTextLength(), text);
    return history.getTextLength() - text.length();
  }

  private static void duplicateHighlighters(@NotNull MarkupModel to, @NotNull MarkupModel from, int offset, @NotNull TextRange textRange) {
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
      }
      final int localOffset = textRange.getStartOffset();
      final int start = Math.max(rangeHighlighter.getStartOffset(), localOffset) - localOffset;
      final int end = Math.min(rangeHighlighter.getEndOffset(), textRange.getEndOffset()) - localOffset;
      if (start > end) {
        continue;
      }
      final RangeHighlighter h = to.addRangeHighlighter(start + offset, end + offset, rangeHighlighter.getLayer(), rangeHighlighter.getTextAttributes(), rangeHighlighter.getTargetArea());
      ((RangeHighlighterEx)h).setAfterEndOfLine(((RangeHighlighterEx)rangeHighlighter).isAfterEndOfLine());
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  public void queueUiUpdate(boolean forceScrollToEnd) {
    myForceScrollToEnd.compareAndSet(false, forceScrollToEnd);
    myUpdateQueue.request();
  }

  @Override
  public void dispose() {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myConsoleEditor);
    editorFactory.releaseEditor(myHistoryViewer);

    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    if (editorManager.isFileOpen(myVirtualFile)) {
      editorManager.closeFile(myVirtualFile);
    }
  }

  @Override
  public void calcData(@NotNull DataKey key, @NotNull DataSink sink) {
    if (OpenFileDescriptor.NAVIGATE_IN_EDITOR == key) {
      sink.put(OpenFileDescriptor.NAVIGATE_IN_EDITOR, myConsoleEditor);
    }
    else if (getProject().isInitialized()) {
      sink.put(key, FileEditorManagerEx.getInstanceEx(getProject()).getData(key.getName(), myConsoleEditor, myVirtualFile));
    }
  }

  private void installEditorFactoryListener() {
    FileEditorManagerAdapter fileEditorListener = new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (myConsoleEditor == null || !Comparing.equal(file, myVirtualFile)) {
          return;
        }

        Editor selectedTextEditor = source.getSelectedTextEditor();
        for (FileEditor fileEditor : source.getAllEditors(file)) {
          if (!(fileEditor instanceof TextEditor)) {
            continue;
          }

          final EditorEx editor = (EditorEx)((TextEditor)fileEditor).getEditor();
          editor.addFocusListener(myFocusListener);
          if (selectedTextEditor == editor) { // already focused
            myCurrentEditor = editor;
          }
          EmptyAction.registerActionShortcuts(editor.getComponent(), myConsoleEditor.getComponent());
          editor.getCaretModel().addCaretListener(new CaretAdapter() {
            @Override
            public void caretPositionChanged(CaretEvent e) {
              queueUiUpdate(false);
            }
          });
        }
        queueUiUpdate(false);
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!Comparing.equal(file, myVirtualFile)) {
          return;
        }
        if (myUiUpdateRunnable != null && !Boolean.TRUE.equals(file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN))) {
          if (myCurrentEditor != null && myCurrentEditor.isDisposed()) {
            myCurrentEditor = null;
          }
          ApplicationManager.getApplication().runReadAction(myUiUpdateRunnable);
        }
      }
    };
    myProject.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorListener);
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    if (editorManager.isFileOpen(myVirtualFile)) {
      fileEditorListener.fileOpened(editorManager, myVirtualFile);
    }
  }

  public Editor getCurrentEditor() {
    return ObjectUtils.chooseNotNull(myCurrentEditor, myConsoleEditor);
  }

  public Language getLanguage() {
    return myVirtualFile.getLanguage();
  }

  public void setLanguage(@NotNull Language language) {
    myVirtualFile.setLanguage(language);
    myVirtualFile.setContent(myEditorDocument, myEditorDocument.getText(), false);
    FileContentUtil.reparseFiles(myProject, Collections.<VirtualFile>singletonList(myVirtualFile), false);
    myFile = createFile(myVirtualFile, myEditorDocument, myProject);
  }

  public void setInputText(@NotNull final String query) {
    DocumentUtil.writeInRunUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        myConsoleEditor.getDocument().setText(query);
      }
    });
  }

  @NotNull
  protected PsiFile createFile(@NotNull LightVirtualFile virtualFile, @NotNull Document document, @NotNull Project project) {
    return ObjectUtils.assertNotNull(PsiManager.getInstance(project).findFile(virtualFile));
  }

  boolean isHistoryViewerForceAdditionalColumnsUsage() {
    return true;
  }

  int getMinHistoryLineCount() {
    return 2;
  }

  private class MyLayout extends AbstractLayoutManager {
    @Override
    public Dimension preferredLayoutSize(final Container parent) {
      return new Dimension(0, 0);
    }

    @Override
    public void layoutContainer(@NotNull final Container parent) {
      final int componentCount = parent.getComponentCount();
      if (componentCount == 0) {
        return;
      }

      final EditorEx history = myHistoryViewer;
      final EditorEx input = componentCount == 2 ? myConsoleEditor : null;
      if (input == null) {
        parent.getComponent(0).setBounds(parent.getBounds());
        return;
      }

      final Dimension panelSize = parent.getSize();
      if (panelSize.getHeight() <= 0) {
        return;
      }
      final Dimension historySize = history.getContentSize();
      final Dimension inputSize = input.getContentSize();

      int newInputHeight;
      // deal with width
      final int width = Math.max(inputSize.width, historySize.width);
      if (isHistoryViewerForceAdditionalColumnsUsage()) {
        history.getSoftWrapModel().forceAdditionalColumnsUsage();
        input.getSettings().setAdditionalColumnsCount(2 + (width - inputSize.width) / EditorUtil.getSpaceWidth(Font.PLAIN, input));
        history.getSettings().setAdditionalColumnsCount(2 + (width - historySize.width) / EditorUtil.getSpaceWidth(Font.PLAIN, history));
      }

      // deal with height, WEB-11122 we cannot trust editor width — it could be 0 in case of soft wrap even if editor has text
      if (history.getDocument().getLineCount() == 0) {
        historySize.height = 0;
      }

      int minHistoryHeight = historySize.height > 0 ? (getMinHistoryLineCount() * history.getLineHeight() + (myShowSeparatorLine ? SEPARATOR_THICKNESS : 0)) : 0;
      int minInputHeight = input.isViewer() ? 0 : input.getLineHeight();
      final int inputPreferredHeight = input.isViewer() ? 0 : Math.max(minInputHeight, inputSize.height);
      final int historyPreferredHeight = Math.max(minHistoryHeight, historySize.height);
      if (panelSize.height < minInputHeight) {
        newInputHeight = panelSize.height;
      }
      else if (panelSize.height < inputPreferredHeight) {
        newInputHeight = panelSize.height - minHistoryHeight;
      }
      else if (panelSize.height < (inputPreferredHeight + historyPreferredHeight) || inputPreferredHeight == 0) {
        newInputHeight = inputPreferredHeight;
      }
      else {
        newInputHeight = panelSize.height - historyPreferredHeight;
      }

      int newHistoryHeight = panelSize.height - newInputHeight;
      // apply
      input.getComponent().setBounds(0, newHistoryHeight, panelSize.width, newInputHeight);
      myForceScrollToEnd.compareAndSet(false, shouldScrollHistoryToEnd());
      history.getComponent().setBounds(0, 0, panelSize.width, newHistoryHeight);
    }
  }
}
