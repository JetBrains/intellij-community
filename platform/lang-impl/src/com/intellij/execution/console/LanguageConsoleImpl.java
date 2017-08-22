/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.util.DocumentUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collections;

/**
 * @author Gregory.Shrago
 * In case of REPL consider to use {@link LanguageConsoleBuilder}
 */
public class LanguageConsoleImpl extends ConsoleViewImpl implements LanguageConsoleView, DataProvider {
  private final Helper myHelper;

  private final EditorEx myConsoleEditor;
  private final EditorEx myHistoryViewer;
  private final Document myEditorDocument;

  private final JPanel myPanel = new JPanel(new MyLayout());
  private final JScrollBar myScrollBar = new JBScrollBar(Adjustable.HORIZONTAL);
  private final DocumentListener myDocumentAdapter = new DocumentListener() {
    @Override
    public void documentChanged(DocumentEvent event) {
      myPanel.revalidate();
    }
  };

  @Nullable
  private String myPrompt = "> ";
  private ConsoleViewContentType myPromptAttributes = ConsoleViewContentType.USER_INPUT;

  private EditorEx myCurrentEditor;

  private final MessageBusConnection myBusConnection;
  private final FocusChangeListener myFocusListener = new FocusChangeListener() {
    @Override
    public void focusGained(Editor editor) {
      myCurrentEditor = (EditorEx)editor;
      if (GeneralSettings.getInstance().isSaveOnFrameDeactivation()) {
        TransactionGuard.submitTransaction(LanguageConsoleImpl.this, () -> FileDocumentManager.getInstance().saveAllDocuments()); // PY-12487
      }
    }

    @Override
    public void focusLost(Editor editor) {
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
    myEditorDocument = helper.getDocument();
    myConsoleEditor = (EditorEx)editorFactory.createEditor(myEditorDocument, getProject());
    myConsoleEditor.getDocument().addDocumentListener(myDocumentAdapter);
    myConsoleEditor.getScrollPane().getHorizontalScrollBar().setEnabled(false);
    myConsoleEditor.addFocusListener(myFocusListener);
    myCurrentEditor = myConsoleEditor;
    Document historyDocument = ((EditorFactoryImpl)editorFactory).createDocument(true);
    UndoUtil.disableUndoFor(historyDocument);
    myHistoryViewer = (EditorEx)editorFactory.createViewer(historyDocument, getProject(), EditorKind.CONSOLE);
    myHistoryViewer.getDocument().addDocumentListener(myDocumentAdapter);

    myScrollBar.setModel(new MyModel(myScrollBar, myHistoryViewer, myConsoleEditor));
    myScrollBar.putClientProperty(Alignment.class, Alignment.BOTTOM);

    myBusConnection = getProject().getMessageBus().connect();
    // action shortcuts are not yet registered
    ApplicationManager.getApplication().invokeLater(() -> installEditorFactoryListener(), getProject().getDisposed());
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
  public JComponent getPreferredFocusableComponent() {
    return getConsoleEditor().getContentComponent();
  }

  private void initComponents() {
    setupComponents();

    myPanel.add(myHistoryViewer.getComponent());
    myPanel.add(myConsoleEditor.getComponent());
    myPanel.add(myScrollBar);
    myPanel.setBackground(myConsoleEditor.getBackgroundColor());

    DataManager.registerDataProvider(myPanel, this);
    setPromptInner(myPrompt);
  }

  @Override
  public void setConsoleEditorEnabled(boolean consoleEditorEnabled) {
    if (isConsoleEditorEnabled() == consoleEditorEnabled) {
      return;
    }
    if (consoleEditorEnabled) {
      FileEditorManager.getInstance(getProject()).closeFile(getVirtualFile());
      myCurrentEditor = myConsoleEditor;
    }
    setHistoryScrollBarVisible(!consoleEditorEnabled);
    myScrollBar.setVisible(consoleEditorEnabled);
    myConsoleEditor.getComponent().setVisible(consoleEditorEnabled);
  }

  private void setHistoryScrollBarVisible(boolean visible) {
    JScrollBar prev = myHistoryViewer.getScrollPane().getHorizontalScrollBar();
    prev.setEnabled(visible);
  }

  private void setupComponents() {
    myHelper.setupEditor(myConsoleEditor);
    myHelper.setupEditor(myHistoryViewer);

    myHistoryViewer.getComponent().setMinimumSize(JBUI.emptySize());
    myHistoryViewer.getComponent().setPreferredSize(JBUI.emptySize());
    myHistoryViewer.setCaretEnabled(false);

    myConsoleEditor.setContextMenuGroupId(IdeActions.GROUP_CONSOLE_EDITOR_POPUP);
    myConsoleEditor.setHighlighter(
      EditorHighlighterFactory.getInstance().createEditorHighlighter(getVirtualFile(), myConsoleEditor.getColorsScheme(), getProject()));

    setHistoryScrollBarVisible(false);

    myHistoryViewer.getContentComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent event) {
        if (isConsoleEditorEnabled() && UIUtil.isReallyTypedEvent(event)) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myConsoleEditor.getContentComponent(), true));
          myConsoleEditor.processKeyTyped(event);
        }
      }
    });

    EmptyAction.registerActionShortcuts(myHistoryViewer.getComponent(), myConsoleEditor.getComponent());
  }

  @Override
  public final boolean isConsoleEditorEnabled() {
    return myConsoleEditor.getComponent().isVisible();
  }

  @Override
  @Nullable
  public String getPrompt() {
    return myPrompt;
  }

  @Override
  @Nullable
  public ConsoleViewContentType getPromptAttributes() {
    return myPromptAttributes;
  }

  @Override
  public void setPromptAttributes(@NotNull ConsoleViewContentType textAttributes) {
    myPromptAttributes = textAttributes;
  }

  @Override
  public void setPrompt(@Nullable String prompt) {
    // always add space to the prompt otherwise it may look ugly
    myPrompt = prompt != null && !prompt.endsWith(" ") ? prompt + " " : prompt;
    setPromptInner(myPrompt);
  }

  private void setPromptInner(@Nullable final String prompt) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      if (!myConsoleEditor.isDisposed()) {
        myConsoleEditor.setPrefixTextAndAttributes(prompt, myPromptAttributes.getAttributes());
      }
    });
  }

  @Override
  public void setEditable(boolean editable) {
    myConsoleEditor.setRendererMode(!editable);
    setPromptInner(editable ? myPrompt : "");
  }

  @Override
  public boolean isEditable() {
    return !myConsoleEditor.isRendererMode();
  }

  @Override
  @NotNull
  public final PsiFile getFile() {
    return myHelper.getFileSafe();
  }

  @Override
  @NotNull
  public final VirtualFile getVirtualFile() {
    return myHelper.virtualFile;
  }

  @Override
  @NotNull
  public final EditorEx getHistoryViewer() {
    return myHistoryViewer;
  }

  @Override
  @NotNull
  public final Document getEditorDocument() {
    return myEditorDocument;
  }

  @Override
  @NotNull
  public final EditorEx getConsoleEditor() {
    return myConsoleEditor;
  }

  @Override
  @NotNull
  public String getTitle() {
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
      String fullText = InjectedLanguageUtil.getUnescapedText(file, null, null);
      highlighter.setText(fullText);
      text = textRange.substring(fullText);
    }
    else {
      text = inputEditor.getDocument().getText(textRange);
      highlighter = ((EditorEx)inputEditor).getHighlighter();
    }
    SyntaxHighlighter syntax =
      highlighter instanceof LexerEditorHighlighter ? ((LexerEditorHighlighter)highlighter).getSyntaxHighlighter() : null;
    ((LanguageConsoleImpl)console).doAddPromptToHistory();
    if (syntax != null) {
      ConsoleViewUtil.printWithHighlighting(console, text, syntax);
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
    if (myPrompt != null) {
      print(myPrompt, myPromptAttributes);
    }
  }


  //private static void duplicateHighlighters(@NotNull MarkupModel to, @NotNull MarkupModel from, int offset, @NotNull TextRange textRange) {
  //  for (RangeHighlighter rangeHighlighter : from.getAllHighlighters()) {
  //    if (!rangeHighlighter.isValid()) {
  //      continue;
  //    }
  //    Object tooltip = rangeHighlighter.getErrorStripeTooltip();
  //    HighlightInfo highlightInfo = tooltip instanceof HighlightInfo? (HighlightInfo)tooltip : null;
  //    if (highlightInfo != null) {
  //      if (highlightInfo.getSeverity() != HighlightSeverity.INFORMATION) {
  //        continue;
  //      }
  //      if (highlightInfo.type.getAttributesKey() == EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES) {
  //        continue;
  //      }
  //    }
  //    int localOffset = textRange.getStartOffset();
  //    int start = Math.max(rangeHighlighter.getStartOffset(), localOffset) - localOffset;
  //    int end = Math.min(rangeHighlighter.getEndOffset(), textRange.getEndOffset()) - localOffset;
  //    if (start > end) {
  //      continue;
  //    }
  //    RangeHighlighter h = to.addRangeHighlighter(start + offset, end + offset, rangeHighlighter.getLayer(),
  //                                                rangeHighlighter.getTextAttributes(), rangeHighlighter.getTargetArea());
  //    ((RangeHighlighterEx)h).setAfterEndOfLine(((RangeHighlighterEx)rangeHighlighter).isAfterEndOfLine());
  //  }
  //}

  @Override
  public void dispose() {
    super.dispose();
    // double dispose via RunContentDescriptor and ContentImpl
    if (myHistoryViewer.isDisposed()) return;

    myConsoleEditor.getDocument().removeDocumentListener(myDocumentAdapter);
    myHistoryViewer.getDocument().removeDocumentListener(myDocumentAdapter);

    myBusConnection.deliverImmediately();
    Disposer.dispose(myBusConnection);

    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myConsoleEditor);
    editorFactory.releaseEditor(myHistoryViewer);

    if (getProject().isOpen()) {
      FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
      if (editorManager.isFileOpen(getVirtualFile())) {
        editorManager.closeFile(getVirtualFile());
      }
    }
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return super.getData(dataId);
  }

  private void installEditorFactoryListener() {
    FileEditorManagerListener fileEditorListener = new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (myConsoleEditor == null || !Comparing.equal(file, getVirtualFile())) {
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
        }
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!Comparing.equal(file, getVirtualFile())) {
          return;
        }
        if (!Boolean.TRUE.equals(file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN))) {
          if (myCurrentEditor != null && myCurrentEditor.isDisposed()) {
            myCurrentEditor = null;
          }
        }
      }
    };
    myBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorListener);
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    if (editorManager.isFileOpen(getVirtualFile())) {
      fileEditorListener.fileOpened(editorManager, getVirtualFile());
    }
  }

  @Override
  @NotNull
  public EditorEx getCurrentEditor() {
    return ObjectUtils.notNull(myCurrentEditor, myConsoleEditor);
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
    DocumentUtil.writeInRunUndoTransparentAction(() -> myConsoleEditor.getDocument().setText(StringUtil.convertLineSeparators(query)));
  }

  boolean isHistoryViewerForceAdditionalColumnsUsage() {
    return true;
  }

  int getMinHistoryLineCount() {
    return 2;
  }

  public static class Helper {
    public final Project project;
    public final VirtualFile virtualFile;
    String title;
    PsiFile file;

    public Helper(@NotNull Project project, @NotNull VirtualFile virtualFile) {
      this.project = project;
      this.virtualFile = virtualFile;
      title = virtualFile.getName();
    }

    public Helper setTitle(String title) {
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
      ConsoleViewUtil.setupConsoleEditor(editor, false, false);
      editor.getContentComponent().setFocusCycleRoot(false);
      editor.setHorizontalScrollbarVisible(true);
      editor.setVerticalScrollbarVisible(true);
      editor.setBorder(null);

      EditorSettings editorSettings = editor.getSettings();
      editorSettings.setAdditionalLinesCount(1);
      editorSettings.setAdditionalColumnsCount(1);

      DataManager.registerDataProvider(editor.getComponent(), (dataId) -> getEditorData(editor, dataId));
    }

    @NotNull
    PsiFile getFileSafe() {
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
      final EditorEx input = isConsoleEditorEnabled() ? myConsoleEditor : null;
      if (input == null) {
        parent.getComponent(0).setBounds(parent.getBounds());
        return;
      }

      final Dimension panelSize = parent.getSize();
      if (myScrollBar.isVisible()) {
        Dimension size = myScrollBar.getPreferredSize();
        if (panelSize.height < size.height) return;
        panelSize.height -= size.height;
        myScrollBar.setBounds(0, panelSize.height, panelSize.width, size.height);
      }
      if (panelSize.getHeight() <= 0) {
        return;
      }
      final Dimension historySize = history.getContentSize();
      final Dimension inputSize = input.getContentSize();

      // deal with width
      if (isHistoryViewerForceAdditionalColumnsUsage()) {
        history.getSoftWrapModel().forceAdditionalColumnsUsage();

        int minAdditionalColumns = 2;
        // calculate content size without additional columns except minimal amount
        int historySpaceWidth = EditorUtil.getPlainSpaceWidth(history);
        historySize.width += historySpaceWidth * (minAdditionalColumns - history.getSettings().getAdditionalColumnsCount());
        // calculate content size without additional columns except minimal amount
        int inputSpaceWidth = EditorUtil.getPlainSpaceWidth(input);
        inputSize.width += inputSpaceWidth * (minAdditionalColumns - input.getSettings().getAdditionalColumnsCount());
        // calculate additional columns according to the corresponding width
        int max = Math.max(historySize.width, inputSize.width);
        history.getSettings().setAdditionalColumnsCount(minAdditionalColumns + (max - historySize.width) / historySpaceWidth);
        input.getSettings().setAdditionalColumnsCount(minAdditionalColumns + (max - inputSize.width) / inputSpaceWidth);
      }

      int newInputHeight;
      // deal with height, WEB-11122 we cannot trust editor width - it could be 0 in case of soft wrap even if editor has text
      if (history.getDocument().getLineCount() == 0) {
        historySize.height = 0;
      }

      int minHistoryHeight = historySize.height > 0 ? getMinHistoryLineCount() * history.getLineHeight() : 0;
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

      int oldHistoryHeight = history.getComponent().getHeight();
      int newHistoryHeight = panelSize.height - newInputHeight;
      int delta = newHistoryHeight - ((newHistoryHeight / history.getLineHeight()) * history.getLineHeight());
      newHistoryHeight -= delta;
      newInputHeight += delta;

      // apply new bounds & scroll history viewer
      input.getComponent().setBounds(0, newHistoryHeight, panelSize.width, newInputHeight);
      history.getComponent().setBounds(0, 0, panelSize.width, newHistoryHeight);
      input.getComponent().doLayout();
      history.getComponent().doLayout();
      if (newHistoryHeight < oldHistoryHeight) {
        JViewport viewport = history.getScrollPane().getViewport();
        Point position = viewport.getViewPosition();
        position.translate(0, oldHistoryHeight - newHistoryHeight);
        viewport.setViewPosition(position);
      }
    }
  }

  private static final class MyModel extends DefaultBoundedRangeModel {
    private volatile boolean myInternalChange;
    private final JScrollBar myBar;
    private final EditorEx myFirstEditor;
    private final EditorEx mySecondEditor;
    private int myFirstValue;
    private int mySecondValue;

    private MyModel(JScrollBar bar, EditorEx first, EditorEx second) {
      myBar = bar;
      myFirstEditor = first;
      mySecondEditor = second;
      addChangeListener(event -> onChange());
      first.getScrollPane().getViewport().addChangeListener(event -> onUpdate(event.getSource()));
      second.getScrollPane().getViewport().addChangeListener(event -> onUpdate(event.getSource()));
    }

    private boolean isInternal() {
      return myInternalChange || !myFirstEditor.getComponent().isVisible() || !mySecondEditor.getComponent().isVisible();
    }

    private void onChange() {
      if (isInternal()) return;
      myInternalChange = true;
      setValue(myFirstEditor.getScrollPane().getViewport(), getValue());
      setValue(mySecondEditor.getScrollPane().getViewport(), getValue());
      myInternalChange = false;
    }

    private void onUpdate(Object source) {
      if (isInternal()) return;
      JViewport first = myFirstEditor.getScrollPane().getViewport();
      JViewport second = mySecondEditor.getScrollPane().getViewport();
      int value = getValue();
      if (source == first) {
        Point position = first.getViewPosition();
        if (position.x != myFirstValue) {
          myFirstValue = value = position.x;
        }
      }
      else {
        Point position = second.getViewPosition();
        if (position.x != mySecondValue) {
          mySecondValue = value = position.x;
        }
      }
      int ext = Math.min(first.getExtentSize().width, second.getExtentSize().width);
      int max = Math.max(first.getViewSize().width, second.getViewSize().width);
      setRangeProperties(value, ext, 0, max, false);
      myBar.setEnabled(ext < max);
    }

    private static void setValue(JViewport viewport, int value) {
      Point position = viewport.getViewPosition();
      position.x = Math.max(0, Math.min(value, viewport.getViewSize().width - viewport.getExtentSize().width));
      viewport.setViewPosition(position);
    }
  }
}
