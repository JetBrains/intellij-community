/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.DocumentEx;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.util.FileContentUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Gregory.Shrago
 */
public class LanguageConsoleImpl implements Disposable, TypeSafeDataProvider {
  private static final Logger LOG = Logger.getInstance("#" + LanguageConsoleImpl.class.getName());
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

  public LanguageConsoleImpl(Project project, String title, Language language) {
    this(project, title, language, true);
  }

  public LanguageConsoleImpl(Project project, String title, Language language, boolean initComponents) {
    this(project, title, new LightVirtualFile(title, language, ""), initComponents);
  }

  public LanguageConsoleImpl(Project project, String title, LightVirtualFile lightFile, boolean initComponents) {
    myProject = project;
    myTitle = title;
    myVirtualFile = lightFile;
    EditorFactory editorFactory = EditorFactory.getInstance();
    myHistoryFile = new LightVirtualFile(getTitle() + ".history.txt", FileTypes.PLAIN_TEXT, "");
    myEditorDocument = FileDocumentManager.getInstance().getDocument(lightFile);
    myFile = ObjectUtils.assertNotNull(PsiManager.getInstance(myProject).findFile(myVirtualFile));
    assert myEditorDocument != null;
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
    });

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

  protected AnAction[] createActions() {
    return AnAction.EMPTY_ARRAY;
  }

  public void setTextToEditor(final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myConsoleEditor.getDocument().setText(text);
      }
    });
    queueUiUpdate(true);
  }

  private static void setupEditorDefault(EditorEx editor) {
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

  private void setPromptInner(final String prompt) {
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

  public PsiFile getFile() {
    return myFile;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public EditorEx getHistoryViewer() {
    return myHistoryViewer;
  }

  public Document getEditorDocument() {
    return myEditorDocument;
  }

  public EditorEx getConsoleEditor() {
    return myConsoleEditor;
  }

  public Project getProject() {
    return myProject;
  }

  public String getTitle() {
    return myTitle;
  }

  public void setTitle(String title) {
    this.myTitle = title;
  }

  public void addToHistory(final String text, final TextAttributes attributes) {
    printToHistory(text, attributes);
  }

  public void printToHistory(@NotNull final List<Pair<String, TextAttributes>> attributedText) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (LOG.isDebugEnabled()) {
      LOG.debug("printToHistory(): " + attributedText.size());
    }
    final boolean scrollToEnd = shouldScrollHistoryToEnd();
    final int[] offsets = new int[attributedText.size() + 1];
    int i = 0;
    offsets[i] = 0;
    final StringBuilder sb = new StringBuilder();
    for (final Pair<String, TextAttributes> pair : attributedText) {
      sb.append(StringUtil.convertLineSeparators(pair.getFirst()));
      offsets[++i] = sb.length();
    }
    final DocumentEx history = myHistoryViewer.getDocument();
    final int oldHistoryLength = history.getTextLength();
    appendToHistoryDocument(history, sb.toString());

    assert oldHistoryLength + offsets[i] >= history.getTextLength()
      : "unexpected history length " + oldHistoryLength + " " + offsets[i] + " " + history.getTextLength();

    if (oldHistoryLength + offsets[i] != history.getTextLength()) {
      // due to usage of cyclic buffer old text can be dropped
      final int correction = oldHistoryLength + offsets[i] - history.getTextLength();
      for (i = 0; i < offsets.length; ++i) {
        offsets[i] -= correction;
      }
    }
    LOG.debug("printToHistory(): text processed");
    final MarkupModel markupModel = DocumentMarkupModel.forDocument(history, myProject, true);
    i = 0;
    for (final Pair<String, TextAttributes> pair : attributedText) {
      if (offsets[i] >= 0) {
        markupModel.addRangeHighlighter(
          oldHistoryLength + offsets[i],
          oldHistoryLength + offsets[i+1],
          HighlighterLayer.SYNTAX,
          pair.getSecond(),
          HighlighterTargetArea.EXACT_RANGE
        );
      }
      ++i;
    }
    LOG.debug("printToHistory(): markup added");
    if (scrollToEnd) {
      scrollHistoryToEnd();
    }
    queueUiUpdate(scrollToEnd);
    LOG.debug("printToHistory(): completed");
  }

  public void printToHistory(String text, final TextAttributes attributes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    text = StringUtil.convertLineSeparators(text);
    final boolean scrollToEnd = shouldScrollHistoryToEnd();
    addTextToHistory(text, attributes);
    if (scrollToEnd) {
      scrollHistoryToEnd();
    }
    queueUiUpdate(scrollToEnd);
  }

  protected void addTextToHistory(@Nullable String text, @Nullable TextAttributes attributes) {
    if (text == null || text.length() == 0) return;
    Document history = myHistoryViewer.getDocument();
    MarkupModel markupModel = DocumentMarkupModel.forDocument(history, myProject, true);
    int offset = history.getTextLength();
    appendToHistoryDocument(history, text);
    if (attributes == null) return;
    markupModel.addRangeHighlighter(offset, offset + text.length(), HighlighterLayer.SYNTAX, attributes, HighlighterTargetArea.EXACT_RANGE);
  }

  public String addCurrentToHistory(final TextRange textRange, final boolean erase, final boolean preserveMarkup) {
    return addToHistoryInner(textRange, myConsoleEditor, erase, preserveMarkup);
  }

  public String addToHistory(final TextRange textRange, final EditorEx editor, final boolean preserveMarkup) {
    return addToHistoryInner(textRange, editor, false, preserveMarkup);
  }

  protected String addToHistoryInner(final TextRange textRange,
                                     final EditorEx editor,
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

  protected String addTextRangeToHistory(TextRange textRange, final EditorEx consoleEditor, boolean preserveMarkup) {
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
    appendToHistoryDocument(history, text);
    int offset = history.getTextLength() - text.length();

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
      duplicateHighlighters(markupModel, consoleEditor.getMarkupModel(), offset, textRange);
    }
    if (!text.endsWith("\n")) {
      appendToHistoryDocument(history, "\n");
    }
    return text;
  }

  protected void doAddPromptToHistory() {
    addTextToHistory(myPrompt, ConsoleViewContentType.USER_INPUT.getAttributes());
  }

  protected void appendToHistoryDocument(@NotNull Document history, @NotNull String text) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    history.insertString(history.getTextLength(), text);
  }

  private static void duplicateHighlighters(MarkupModel to, MarkupModel from, int offset, TextRange textRange) {
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
  public void calcData(DataKey key, DataSink sink) {
    if (OpenFileDescriptor.NAVIGATE_IN_EDITOR == key) {
      sink.put(OpenFileDescriptor.NAVIGATE_IN_EDITOR, myConsoleEditor);
      return;
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
    if (myProject.isDisposed()) {
      return;
    }
    myProject.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorListener);
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    if (editorManager.isFileOpen(myVirtualFile)) {
      fileEditorListener.fileOpened(editorManager, myVirtualFile);
    }
  }

  public Editor getCurrentEditor() {
    return myCurrentEditor == null? myConsoleEditor : myCurrentEditor;
  }

  public void setLanguage(Language language) {
    myVirtualFile.setLanguage(language);
    myVirtualFile.setContent(myEditorDocument, myEditorDocument.getText(), false);
    FileContentUtil.reparseFiles(myProject, Collections.<VirtualFile>singletonList(myVirtualFile), false);
    myFile = ObjectUtils.assertNotNull(PsiManager.getInstance(myProject).findFile(myVirtualFile));
  }

  public void setInputText(final String query) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myConsoleEditor.getDocument().setText(query);
      }
    });
  }

  public static void printToConsole(
    @NotNull final LanguageConsoleImpl console,
    @NotNull final ConsoleViewContentType mainType,
    @NotNull final List<Pair<String, ConsoleViewContentType>> textToPrint)
  {
    final List<Pair<String, TextAttributes>> attributedText = ContainerUtil.map(
      textToPrint,
      new Function<Pair<String, ConsoleViewContentType>, Pair<String, TextAttributes>>() {
        @Override
        public Pair<String, TextAttributes> fun(Pair<String, ConsoleViewContentType> input) {
          final TextAttributes mainAttributes = mainType.getAttributes();
          final TextAttributes attributes;
          if (input.getSecond() == null) {
            attributes = mainAttributes;
          }
          else {
            attributes = input.getSecond().getAttributes().clone();
            attributes.setBackgroundColor(mainAttributes.getBackgroundColor());
          }
          return Pair.create(input.getFirst(), attributes);
        }
      }
    );

    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      console.printToHistory(attributedText);
    }
    else {
      application.invokeLater(new Runnable() {
        @Override
        public void run() {
          console.printToHistory(attributedText);
        }
      }, ModalityState.stateForComponent(console.getComponent()));
    }
  }

  public static void printToConsole(@NotNull final LanguageConsoleImpl console,
                                    @NotNull final String string,
                                    @NotNull final ConsoleViewContentType mainType,
                                    @Nullable ConsoleViewContentType additionalType) {
    final TextAttributes mainAttributes = mainType.getAttributes();
    final TextAttributes attributes;
    if (additionalType == null) {
      attributes = mainAttributes;
    }
    else {
      attributes = additionalType.getAttributes().clone();
      attributes.setBackgroundColor(mainAttributes.getBackgroundColor());
    }

    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      console.printToHistory(string, attributes);
    }
    else {
      application.invokeLater(new Runnable() {
        @Override
        public void run() {
          console.printToHistory(string, attributes);
        }
      }, ModalityState.stateForComponent(console.getComponent()));
    }
  }

  private class MyLayout extends AbstractLayoutManager {
    @Override
    public Dimension preferredLayoutSize(final Container parent) {
      return new Dimension(0, 0);
    }

    @Override
    public void layoutContainer(final Container parent) {
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
