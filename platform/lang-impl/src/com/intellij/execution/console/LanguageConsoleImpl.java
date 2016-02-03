/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBScrollBar;
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
import javax.swing.border.Border;
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
        FileDocumentManager.getInstance().saveAllDocuments(); // PY-12487
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
    myConsoleEditor.addFocusListener(myFocusListener);
    myCurrentEditor = myConsoleEditor;
    Document historyDocument = ((EditorFactoryImpl)editorFactory).createDocument(true);
    UndoUtil.disableUndoFor(historyDocument);
    myHistoryViewer = (EditorEx)editorFactory.createViewer(historyDocument, getProject());

    myBusConnection = getProject().getMessageBus().connect();
    // action shortcuts are not yet registered
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        installEditorFactoryListener();
      }
    }, getProject().getDisposed());
  }

  @Override
  protected final EditorEx doCreateConsoleEditor() {
    return myHistoryViewer;
  }

  @Override
  protected final void disposeEditor() {
  }

  @Override
  protected final JComponent createCenterComponent() {
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

    DataManager.registerDataProvider(myPanel, this);
    setPromptInner(myPrompt);
  }

  public void setConsoleEditorEnabled(boolean consoleEditorEnabled) {
    if (isConsoleEditorEnabled() == consoleEditorEnabled) {
      return;
    }

    myPanel.removeAll();

    if (consoleEditorEnabled) {
      FileEditorManager.getInstance(getProject()).closeFile(getVirtualFile());

      setHistoryScrollBarVisible(false);
      myPanel.add(myHistoryViewer.getComponent());
      myPanel.add(myConsoleEditor.getComponent());

      myCurrentEditor = myConsoleEditor;
    }
    else {
      setHistoryScrollBarVisible(true);
      myPanel.add(myHistoryViewer.getComponent(), BorderLayout.CENTER);
    }
  }

  private void setHistoryScrollBarVisible(boolean visible) {
    JScrollBar prev = myHistoryViewer.getScrollPane().getHorizontalScrollBar();
    JScrollBar next;
    if (visible) {
      next = ((EmptyScrollBar)prev).original;
    }
    else {
      next = new EmptyScrollBar(prev);
    }
    myHistoryViewer.getScrollPane().setHorizontalScrollBar(next);
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

    myConsoleEditor.getScrollPane().getHorizontalScrollBar().setModel(
      myHistoryViewer.getScrollPane().getHorizontalScrollBar().getModel());
    setHistoryScrollBarVisible(false);

    myHistoryViewer.getContentComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent event) {
        if (isConsoleEditorEnabled() && UIUtil.isReallyTypedEvent(event)) {
          myConsoleEditor.getContentComponent().requestFocus();
          myConsoleEditor.processKeyTyped(event);
        }
      }
    });

    EmptyAction.registerActionShortcuts(myHistoryViewer.getComponent(), myConsoleEditor.getComponent());
  }

  public final boolean isConsoleEditorEnabled() {
    return myPanel.getComponentCount() > 1;
  }

  @Nullable
  public String getPrompt() {
    return myPrompt;
  }

  @Nullable
  public ConsoleViewContentType getPromptAttributes() {
    return myPromptAttributes;
  }

  public void setPromptAttributes(@NotNull ConsoleViewContentType textAttributes) {
    myPromptAttributes = textAttributes;
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
        if (!myConsoleEditor.isDisposed()) {
          myConsoleEditor.setPrefixTextAndAttributes(prompt, myPromptAttributes.getAttributes());
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
  public final PsiFile getFile() {
    return myHelper.getFileSafe();
  }

  @NotNull
  public final VirtualFile getVirtualFile() {
    return myHelper.virtualFile;
  }

  @NotNull
  public final EditorEx getHistoryViewer() {
    return myHistoryViewer;
  }

  @NotNull
  public final Document getEditorDocument() {
    return myEditorDocument;
  }

  @NotNull
  public final EditorEx getConsoleEditor() {
    return myConsoleEditor;
  }

  @NotNull
  public String getTitle() {
    return myHelper.title;
  }

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
      DocumentUtil.writeInRunUndoTransparentAction(new Runnable() {
        @Override
        public void run() {
          editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
        }
      });
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
    Object data = super.getData(dataId);
    if (data != null) {
      return data;
    }
    else if (OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
      return myConsoleEditor;
    }
    else if (getProject().isInitialized()) {
      Caret caret = myConsoleEditor.getCaretModel().getCurrentCaret();
      return FileEditorManagerEx.getInstanceEx(getProject()).getData(dataId, myConsoleEditor, caret);
    }
    return null;
  }

  private void installEditorFactoryListener() {
    FileEditorManagerAdapter fileEditorListener = new FileEditorManagerAdapter() {
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

  @NotNull
  public EditorEx getCurrentEditor() {
    return ObjectUtils.notNull(myCurrentEditor, myConsoleEditor);
  }

  @NotNull
  public Language getLanguage() {
    return getFile().getLanguage();
  }

  public void setLanguage(@NotNull Language language) {
    myHelper.setLanguage(language);
    myHelper.getFileSafe();
  }

  public void setInputText(@NotNull final String query) {
    DocumentUtil.writeInRunUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        myConsoleEditor.getDocument().setText(query);
      }
    });
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
      return ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
        @Override
        public PsiFile compute() {
          return PsiUtilCore.getPsiFile(project, virtualFile);
        }
      });
    }

    @NotNull
    public Document getDocument() {
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null) {
        throw new AssertionError("no document for: " + virtualFile);
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
    }

    @NotNull
    PsiFile getFileSafe() {
      return file == null || !file.isValid() ? file = getFile() : file;
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

  private static class EmptyScrollBar extends JBScrollBar {

    final JScrollBar original;

    public EmptyScrollBar(JScrollBar original) {
      this.original = original;
      setModel(original.getModel());
    }

    @Override
    public Dimension getPreferredSize() {
      return JBUI.emptySize();
    }

    @Override
    public Dimension getMinimumSize() {
      return JBUI.emptySize();
    }

    @Override
    public Dimension getMaximumSize() {
      return JBUI.emptySize();
    }

    @Override
    public Border getBorder() {
      return null;
    }

    @Override
    public void paint(Graphics g) {
    }
  }
}
