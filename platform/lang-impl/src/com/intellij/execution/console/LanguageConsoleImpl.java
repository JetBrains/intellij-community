/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
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
import com.intellij.util.FileContentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
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
 * In case of REPL consider to use {@link com.intellij.execution.runners.LanguageConsoleBuilder}
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
  private final MergingUpdateQueue myUpdateQueue;
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
    myProject = project;
    myTitle = title;
    myVirtualFile = lightFile;
    EditorFactory editorFactory = EditorFactory.getInstance();
    myHistoryFile = new LightVirtualFile(getTitle() + ".history.txt", FileTypes.PLAIN_TEXT, "");
    myEditorDocument = FileDocumentManager.getInstance().getDocument(lightFile);
    assert myEditorDocument != null;
    myFile = createFile(myVirtualFile, myEditorDocument, myProject);
    myConsoleEditor = (EditorEx)editorFactory.createEditor(myEditorDocument, myProject);
    myConsoleEditor.addFocusListener(myFocusListener);
    myCurrentEditor = myConsoleEditor;
    myHistoryViewer = (EditorEx)editorFactory.createViewer(((EditorFactoryImpl)editorFactory).createDocument(true), myProject);
    myUpdateQueue = new MergingUpdateQueue("ConsoleUpdateQueue", 300, true, null);
    Disposer.register(this, myUpdateQueue);

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
    final EditorColorsScheme colorsScheme = myConsoleEditor.getColorsScheme();
    final DelegateColorScheme scheme = new DelegateColorScheme(colorsScheme) {
      @NotNull
      @Override
      public Color getDefaultBackground() {
        final Color color = getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
        return color == null ? super.getDefaultBackground() : color;
      }
    };
    myConsoleEditor.setColorsScheme(scheme);
    myHistoryViewer.setColorsScheme(scheme);
    myPanel.add(myHistoryViewer.getComponent());
    myPanel.add(myConsoleEditor.getComponent());
    setupComponents();
    DataManager.registerDataProvider(myPanel, new TypeSafeDataProviderAdapter(this));

    myHistoryViewer.getComponent().addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (myForceScrollToEnd.getAndSet(false)) {
          final JScrollBar scrollBar = myHistoryViewer.getScrollPane().getVerticalScrollBar();
          scrollBar.setValue(scrollBar.getMaximum());
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
    if (isConsoleEditorEnabled() == consoleEditorEnabled) return;
    final FileEditorManagerEx fileManager = FileEditorManagerEx.getInstanceEx(getProject());
    if (consoleEditorEnabled) {
      fileManager.closeFile(myVirtualFile);
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
    myConsoleEditor.addEditorMouseListener(EditorActionUtil.createEditorPopupHandler(IdeActions.GROUP_CONSOLE_EDITOR_POPUP));
    //noinspection PointlessBooleanExpression,ConstantConditions
    if (SEPARATOR_THICKNESS > 0 && myShowSeparatorLine) {
      myHistoryViewer.getComponent().setBorder(new SideBorder(JBColor.LIGHT_GRAY, SideBorder.BOTTOM));
    }
    myHistoryViewer.getComponent().setMinimumSize(new Dimension(0, 0));
    myHistoryViewer.getComponent().setPreferredSize(new Dimension(0, 0));
    myConsoleEditor.getSettings().setAdditionalLinesCount(2);
    myConsoleEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myVirtualFile));
    myHistoryViewer.setCaretEnabled(false);
    myConsoleEditor.setHorizontalScrollbarVisible(true);
    final VisibleAreaListener areaListener = new VisibleAreaListener() {
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
    };
    myConsoleEditor.getScrollingModel().addVisibleAreaListener(areaListener);
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
    for (AnAction action : createActions()) {
      action.registerCustomShortcutSet(action.getShortcutSet(), myConsoleEditor.getComponent());
    }
    EmptyAction.registerActionShortcuts(myHistoryViewer.getComponent(), myConsoleEditor.getComponent());
  }

  public boolean isConsoleEditorEnabled() {
    return myPanel.getComponentCount() > 1;
  }

  @NotNull
  protected AnAction[] createActions() {
    return AnAction.EMPTY_ARRAY;
  }

  public void setTextToEditor(@NotNull final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myConsoleEditor.getDocument().setText(text);
      }
    });
    queueUiUpdate(true);
  }

  private static void setupEditorDefault(@NotNull EditorEx editor) {
    ConsoleViewUtil.setupConsoleEditor(editor, false, false);
    editor.getContentComponent().setFocusCycleRoot(false);
    editor.setHorizontalScrollbarVisible(false);
    editor.setVerticalScrollbarVisible(true);
    editor.setBorder(null);

    final EditorSettings editorSettings = editor.getSettings();
    editorSettings.setAdditionalLinesCount(0);
    editorSettings.setAdditionalColumnsCount(1);
    editorSettings.setRightMarginShown(false);
  }

  public void setUiUpdateRunnable(Runnable uiUpdateRunnable) {
    assert myUiUpdateRunnable == null : "can be set only once";
    myUiUpdateRunnable = uiUpdateRunnable;
  }

  public void flushAllUiUpdates() {
    myUpdateQueue.flush();
  }

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
    this.myTitle = title;
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
    if (StringUtil.isEmpty(text)) {
      return;
    }

    Document history = myHistoryViewer.getDocument();
    MarkupModel markupModel = DocumentMarkupModel.forDocument(history, myProject, true);
    int offset = appendToHistoryDocument(history, text);
    if (attributes == null) return;
    markupModel.addRangeHighlighter(offset, offset + text.length(), HighlighterLayer.SYNTAX, attributes, HighlighterTargetArea.EXACT_RANGE);
  }

  public String addCurrentToHistory(final TextRange textRange, final boolean erase, final boolean preserveMarkup) {
    return addToHistoryInner(textRange, myConsoleEditor, erase, preserveMarkup);
  }

  public String addToHistory(final TextRange textRange, final EditorEx editor, final boolean preserveMarkup) {
    return addToHistoryInner(textRange, editor, false, preserveMarkup);
  }

  @NotNull
  protected String addToHistoryInner(@NotNull final TextRange textRange,
                                     @NotNull final EditorEx editor,
                                     final boolean erase,
                                     final boolean preserveMarkup) {
    final Ref<String> ref = Ref.create("");
    final Runnable action = new Runnable() {
      @Override
      public void run() {
        ref.set(addTextRangeToHistory(textRange, editor, preserveMarkup));
        if (erase) {
          editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
        }
      }
    };
    if (erase) {
      ApplicationManager.getApplication().runWriteAction(action);
    }
    else {
      ApplicationManager.getApplication().runReadAction(action);
    }
    // always scroll to end on user input
    scrollHistoryToEnd();
    queueUiUpdate(true);
    return ref.get();
  }

  public boolean shouldScrollHistoryToEnd() {
    final Rectangle visibleArea = myHistoryViewer.getScrollingModel().getVisibleArea();
    final Dimension contentSize = myHistoryViewer.getContentSize();
    return contentSize.getHeight() - visibleArea.getMaxY() < 2 * myHistoryViewer.getLineHeight();
  }

  private void scrollHistoryToEnd() {
    final int lineCount = myHistoryViewer.getDocument().getLineCount();
    if (lineCount == 0) return;
    myHistoryViewer.getCaretModel().moveToOffset(myHistoryViewer.getDocument().getLineStartOffset(lineCount - 1), false);
    myHistoryViewer.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
  }

  @NotNull
  protected String addTextRangeToHistory(@NotNull TextRange textRange, @NotNull final EditorEx consoleEditor, boolean preserveMarkup) {
    final Document history = myHistoryViewer.getDocument();
    final MarkupModel markupModel = DocumentMarkupModel.forDocument(history, myProject, true);
    doAddPromptToHistory();
    final int localStartOffset = textRange.getStartOffset();
    String text;
    EditorHighlighter highlighter;
    if (consoleEditor instanceof EditorWindow) {
      EditorWindow editorWindow = (EditorWindow)consoleEditor;
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      PsiFile file = editorWindow.getInjectedFile();
      final VirtualFile virtualFile = file.getVirtualFile();
      assert virtualFile != null;
      highlighter = HighlighterFactory.createHighlighter(virtualFile, scheme, getProject());
      String fullText = InjectedLanguageUtil.getUnescapedText(file, null, null);
      highlighter.setText(fullText);
      text = textRange.substring(fullText);
    }
    else {
      text = consoleEditor.getDocument().getText(textRange);
      highlighter = consoleEditor.getHighlighter();
    }
    //offset can be changed after text trimming after insert due to buffer constraints
    int offset = appendToHistoryDocument(history, text);

    final HighlighterIterator iterator = highlighter.createIterator(localStartOffset);
    final int localEndOffset = textRange.getEndOffset();
    while (!iterator.atEnd()) {
      final int itStart = iterator.getStart();
      if (itStart > localEndOffset) break;
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
      duplicateHighlighters(markupModel, DocumentMarkupModel.forDocument(consoleEditor.getDocument(), myProject, true), offset, textRange);
      // don't copy editor markup model, i.e. brace matcher, spell checker, etc.
      // duplicateHighlighters(markupModel, consoleEditor.getMarkupModel(), offset, textRange);
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
      if (!rangeHighlighter.isValid()) continue;
      Object tooltip = rangeHighlighter.getErrorStripeTooltip();
      HighlightInfo highlightInfo = tooltip instanceof HighlightInfo? (HighlightInfo)tooltip : null;
      if (highlightInfo != null) {
        if (highlightInfo.getSeverity() != HighlightSeverity.INFORMATION) continue;
        if (highlightInfo.type.getAttributesKey() == EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES) continue;
      }
      final int localOffset = textRange.getStartOffset();
      final int start = Math.max(rangeHighlighter.getStartOffset(), localOffset) - localOffset;
      final int end = Math.min(rangeHighlighter.getEndOffset(), textRange.getEndOffset()) - localOffset;
      if (start > end) continue;
      final RangeHighlighter h = to.addRangeHighlighter(
        start + offset, end + offset, rangeHighlighter.getLayer(), rangeHighlighter.getTextAttributes(), rangeHighlighter.getTargetArea());
      ((RangeHighlighterEx)h).setAfterEndOfLine(((RangeHighlighterEx)rangeHighlighter).isAfterEndOfLine());
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  public void queueUiUpdate(final boolean forceScrollToEnd) {
    myForceScrollToEnd.compareAndSet(false, forceScrollToEnd);
    myUpdateQueue.queue(new Update("UpdateUi") {
      @Override
      public void run() {
        if (Disposer.isDisposed(LanguageConsoleImpl.this)) return;
        if (isConsoleEditorEnabled()) {
          myPanel.revalidate();
          myPanel.repaint();
        }
        if (myUiUpdateRunnable != null) {
          ApplicationManager.getApplication().runReadAction(myUiUpdateRunnable);
        }
      }
    });
  }

  @Override
  public void dispose() {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myConsoleEditor);
    editorFactory.releaseEditor(myHistoryViewer);

    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    final boolean isOpen = editorManager.isFileOpen(myVirtualFile);
    if (isOpen) {
      editorManager.closeFile(myVirtualFile);
    }
  }

  @Override
  public void calcData(@NotNull DataKey key, @NotNull DataSink sink) {
    if (OpenFileDescriptor.NAVIGATE_IN_EDITOR == key) {
      sink.put(OpenFileDescriptor.NAVIGATE_IN_EDITOR, myConsoleEditor);
    }
    else if (getProject().isInitialized()) {
      FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
      final Object o = ((FileEditorManagerImpl)editorManager).getData(key.getName(), myConsoleEditor, myVirtualFile);
      sink.put(key, o);
    }
  }

  private void installEditorFactoryListener() {
    final FileEditorManagerAdapter fileEditorListener = new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!Comparing.equal(file, myVirtualFile) || myConsoleEditor == null) return;
        Editor selectedTextEditor = source.getSelectedTextEditor();
        for (FileEditor fileEditor : source.getAllEditors(file)) {
          if (!(fileEditor instanceof TextEditor)) continue;
          final EditorEx editor = (EditorEx)((TextEditor)fileEditor).getEditor();
          editor.addFocusListener(myFocusListener);
          if (selectedTextEditor == editor) { // already focused
            myCurrentEditor = editor;
          }
          EmptyAction.registerActionShortcuts(editor.getComponent(), myConsoleEditor.getComponent());
          editor.getCaretModel().addCaretListener(new CaretListener() {
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
        if (!Comparing.equal(file, myVirtualFile)) return;
        if (myUiUpdateRunnable != null && !Boolean.TRUE.equals(file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN))) {
          if (myCurrentEditor != null && myCurrentEditor.isDisposed()) myCurrentEditor = null;
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
    return myCurrentEditor == null? myConsoleEditor : myCurrentEditor;
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
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
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

  private class MyLayout extends AbstractLayoutManager {
    @Override
    public Dimension preferredLayoutSize(final Container parent) {
      return new Dimension(0, 0);
    }

    @Override
    public void layoutContainer(@NotNull final Container parent) {
      final int componentCount = parent.getComponentCount();
      if (componentCount == 0) return;
      final EditorEx history = myHistoryViewer;
      final EditorEx editor = componentCount == 2 ? myConsoleEditor : null;

      if (editor == null) {
        parent.getComponent(0).setBounds(parent.getBounds());
        return;
      }

      final Dimension panelSize = parent.getSize();
      if (panelSize.getHeight() <= 0) return;
      final Dimension historySize = history.getContentSize();
      final Dimension editorSize = editor.getContentSize();
      final Dimension newEditorSize = new Dimension();

      // deal with width
      final int width = Math.max(editorSize.width, historySize.width);
      newEditorSize.width = width + editor.getScrollPane().getHorizontalScrollBar().getHeight();
      history.getSoftWrapModel().forceAdditionalColumnsUsage();
      editor.getSettings().setAdditionalColumnsCount(2 + (width - editorSize.width) / EditorUtil.getSpaceWidth(Font.PLAIN, editor));
      history.getSettings().setAdditionalColumnsCount(2 + (width - historySize.width) / EditorUtil.getSpaceWidth(Font.PLAIN, history));

      // deal with height
      if (historySize.width == 0) historySize.height = 0;
      final int minHistorySize = historySize.height > 0 ? 2 * history.getLineHeight() + (myShowSeparatorLine ? SEPARATOR_THICKNESS : 0) : 0;
      final int minEditorSize = editor.isViewer() ? 0 : editor.getLineHeight();
      final int editorPreferred = editor.isViewer() ? 0 : Math.max(minEditorSize, editorSize.height);
      final int historyPreferred = Math.max(minHistorySize, historySize.height);
      if (panelSize.height < minEditorSize) {
        newEditorSize.height = panelSize.height;
      }
      else if (panelSize.height < editorPreferred) {
        newEditorSize.height = panelSize.height - minHistorySize;
      }
      else if (panelSize.height < editorPreferred + historyPreferred) {
        newEditorSize.height = editorPreferred;
      }
      else {
        newEditorSize.height = editorPreferred == 0 ? 0 : panelSize.height - historyPreferred;
      }
      final Dimension newHistorySize = new Dimension(width, panelSize.height - newEditorSize.height);

      // apply
      editor.getComponent().setBounds(0, newHistorySize.height, panelSize.width, newEditorSize.height);
      myForceScrollToEnd.compareAndSet(false, shouldScrollHistoryToEnd());
      history.getComponent().setBounds(0, 0, panelSize.width, newHistorySize.height);
    }
  }
}
