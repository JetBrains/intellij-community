/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.SideBorder;
import com.intellij.util.FileContentUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.FocusManager;
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
 */
public class LanguageConsoleImpl implements Disposable, TypeSafeDataProvider {
  private static final int SEPARATOR_THICKNESS = 1;

  private final Project myProject;

  private final EditorEx myConsoleEditor;
  private final EditorEx myHistoryViewer;
  private final Document myEditorDocument;
  protected PsiFile myFile;

  private final JPanel myPanel = new JPanel(new MyLayout());

  private String myTitle;
  private String myPrompt = "> ";
  private final LightVirtualFile myHistoryFile;

  private Editor myCurrentEditor;

  private final AtomicBoolean myForceScrollToEnd = new AtomicBoolean(false);
  private final MergingUpdateQueue myUpdateQueue;
  private Runnable myUiUpdateRunnable;

  private Editor myFullEditor;
  private ActionGroup myFullEditorActions;

  public LanguageConsoleImpl(final Project project, String title, final Language language) {
    myProject = project;
    myTitle = title;
    installEditorFactoryListener();
    final EditorFactory editorFactory = EditorFactory.getInstance();
    myHistoryFile = new LightVirtualFile(getTitle() + ".history.txt", StdFileTypes.PLAIN_TEXT, "");
    myEditorDocument = editorFactory.createDocument("");
    setLanguage(language);
    myConsoleEditor = (EditorEx)editorFactory.createEditor(myEditorDocument, myProject);

    myCurrentEditor = myConsoleEditor;
    myHistoryViewer = (EditorEx)editorFactory.createViewer(((EditorFactoryImpl)editorFactory).createDocument(true), myProject);
    final EditorColorsScheme colorsScheme = myConsoleEditor.getColorsScheme();
    final DelegateColorScheme scheme = new DelegateColorScheme(colorsScheme) {
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
    myUpdateQueue = new MergingUpdateQueue("ConsoleUpdateQueue", 300, true, null);
    Disposer.register(this, myUpdateQueue);
    myHistoryViewer.getComponent().addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        if (myForceScrollToEnd.getAndSet(false)) {
          final JScrollBar scrollBar = myHistoryViewer.getScrollPane().getVerticalScrollBar();
          scrollBar.setValue(scrollBar.getMaximum());
        }
      }

      public void componentShown(ComponentEvent e) {
        componentResized(e);
      }
    });
    setPromptInner(myPrompt);
  }

  public void setFullEditorMode(boolean fullEditorMode) {
    if (myFullEditor != null == fullEditorMode) return;
    final VirtualFile virtualFile = myFile.getVirtualFile();
    assert virtualFile != null;
    final FileEditorManagerEx fileManager = FileEditorManagerEx.getInstanceEx(getProject());
    if (!fullEditorMode) {
      fileManager.closeFile(virtualFile);
      myFullEditor = null;
      myPanel.removeAll();
      myPanel.add(myHistoryViewer.getComponent());
      myPanel.add(myConsoleEditor.getComponent());

      myHistoryViewer.setHorizontalScrollbarVisible(false);
      myCurrentEditor = myConsoleEditor;
    }
    else {
      myPanel.removeAll();
      myPanel.add(myHistoryViewer.getComponent(), BorderLayout.CENTER);
      myFullEditor = fileManager.openTextEditor(new OpenFileDescriptor(getProject(), virtualFile, 0), true);
      configureFullEditor();
      setConsoleFilePinned(fileManager);

      myHistoryViewer.setHorizontalScrollbarVisible(true);
      myCurrentEditor = myFullEditor;
    }
  }

  private void setConsoleFilePinned(FileEditorManagerEx fileManager) {
    if (myFullEditor == null) return;
    EditorWindow editorWindow = EditorWindow.DATA_KEY.getData(DataManager.getInstance().getDataContext(myFullEditor.getComponent()));
    if (editorWindow == null) {
      editorWindow = fileManager.getCurrentWindow();
    }
    if (editorWindow != null) {
      editorWindow.setFilePinned(myFile.getVirtualFile(), true);
    }
  }

  public void setFullEditorActions(ActionGroup actionGroup) {
    myFullEditorActions = actionGroup;
    configureFullEditor();
  }

  private void setupComponents() {
    setupEditorDefault(myConsoleEditor);
    setupEditorDefault(myHistoryViewer);
    myConsoleEditor.addEditorMouseListener(EditorActionUtil.createEditorPopupHandler(IdeActions.GROUP_CUT_COPY_PASTE));
    if (SEPARATOR_THICKNESS > 0) {
      myHistoryViewer.getComponent().setBorder(new SideBorder(Color.LIGHT_GRAY, SideBorder.BOTTOM));
    }
    myHistoryViewer.getComponent().setMinimumSize(new Dimension(0, 0));
    myHistoryViewer.getComponent().setPreferredSize(new Dimension(0, 0));
    myConsoleEditor.getSettings().setAdditionalLinesCount(2);
    myConsoleEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myFile.getVirtualFile()));
    myHistoryViewer.setCaretEnabled(false);
    myConsoleEditor.setHorizontalScrollbarVisible(true);
    final VisibleAreaListener areaListener = new VisibleAreaListener() {
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
      public void keyTyped(KeyEvent event) {
        if (myFullEditor == null && UIUtil.isReallyTypedEvent(event)) {
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

  protected AnAction[] createActions() {
    return AnAction.EMPTY_ARRAY;
  }

  public void addTextToCurrentEditor(final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        getCurrentEditor().getDocument().insertString(0, text);
      }
    });
    queueUiUpdate(true);
  }

  private static void setupEditorDefault(EditorEx editor) {
    editor.getContentComponent().setFocusCycleRoot(false);
    editor.setHorizontalScrollbarVisible(false);
    editor.setVerticalScrollbarVisible(true);
    editor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, null);
    editor.setBorder(null);
    editor.getContentComponent().setFocusCycleRoot(false);

    final EditorSettings editorSettings = editor.getSettings();
    editorSettings.setAdditionalLinesCount(0);
    editorSettings.setAdditionalColumnsCount(1);
    editorSettings.setRightMarginShown(false);
    editorSettings.setFoldingOutlineShown(true);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineCursorWidth(1);
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

  public String getPrompt() {
    return myPrompt;
  }

  public void setPrompt(String prompt) {
    // always add space to the prompt otherwise it may look ugly
    myPrompt = prompt != null && !prompt.endsWith(" ")? prompt + " " : prompt;
    setPromptInner(myPrompt);
  }

  private void setPromptInner(final String prompt) {
    ((EditorImpl)myConsoleEditor).setPrefixTextAndAttributes(prompt, ConsoleViewContentType.USER_INPUT.getAttributes());
    if (myPanel.isVisible()) {
      queueUiUpdate(false);
    }
  }

  public void setEditable(boolean editable) {
    myConsoleEditor.setRendererMode(!editable);
    setPromptInner(editable? myPrompt : "");
  }

  public boolean isEditable() {
    return !myConsoleEditor.isRendererMode();
  }

  public PsiFile getFile() {
    return myFile;
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

  public Editor getFullEditor() {
    return myFullEditor;
  }

  public void printToHistory(String text, final TextAttributes attributes) {
    text = StringUtil.convertLineSeparators(text);
    final boolean scrollToEnd = shouldScrollHistoryToEnd();
    final Document history = myHistoryViewer.getDocument();
    final MarkupModel markupModel = history.getMarkupModel(myProject);
    final int offset = history.getTextLength();
    appendToHistoryDocument(history, text);
    markupModel.addRangeHighlighter(offset,
                                    history.getTextLength(),
                                    HighlighterLayer.SYNTAX,
                                    attributes,
                                    HighlighterTargetArea.EXACT_RANGE);
    if (scrollToEnd) {
      scrollHistoryToEnd();
    }
    queueUiUpdate(scrollToEnd);
  }

  public String addCurrentToHistory(final TextRange textRange, final boolean erase, final boolean preserveMarkup) {
    final Ref<String> ref = Ref.create("");
    final Runnable action = new Runnable() {
      public void run() {
        ref.set(addTextRangeToHistory(textRange, myConsoleEditor, preserveMarkup));
        if (erase) {
          myConsoleEditor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
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
    return contentSize.getHeight() - visibleArea.getMaxY() < 2*myHistoryViewer.getLineHeight();
  }

  private void scrollHistoryToEnd() {
    final int lineCount = myHistoryViewer.getDocument().getLineCount();
    if (lineCount == 0) return;
    myHistoryViewer.getCaretModel().moveToOffset(myHistoryViewer.getDocument().getLineStartOffset(lineCount - 1), false);
    myHistoryViewer.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
  }

  protected String addTextRangeToHistory(TextRange textRange, final EditorEx consoleEditor, boolean preserveMarkup) {
    final DocumentImpl history = (DocumentImpl)myHistoryViewer.getDocument();
    final MarkupModel markupModel = history.getMarkupModel(myProject);
    appendToHistoryDocument(history, myPrompt);
    markupModel.addRangeHighlighter(history.getTextLength() - myPrompt.length(), history.getTextLength(), HighlighterLayer.SYNTAX,
                                    ConsoleViewContentType.USER_INPUT.getAttributes(),
                                    HighlighterTargetArea.EXACT_RANGE);

    final String text = consoleEditor.getDocument().getText(textRange);
    //offset can be changed after text trimming after insert due to buffer constraints
    appendToHistoryDocument(history, text);
    int offset = history.getTextLength() - text.length();
    final int localStartOffset = textRange.getStartOffset();
    final HighlighterIterator iterator = consoleEditor.getHighlighter().createIterator(localStartOffset);
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
      duplicateHighlighters(markupModel, consoleEditor.getDocument().getMarkupModel(myProject), offset, textRange);
      duplicateHighlighters(markupModel, consoleEditor.getMarkupModel(), offset, textRange);
    }
    if (!text.endsWith("\n")) {
      appendToHistoryDocument(history, "\n");
    }
    return text;
  }

  protected void appendToHistoryDocument(@NotNull Document history, @NotNull String text) {
    history.insertString(history.getTextLength(), text);
  }

  private static void duplicateHighlighters(MarkupModel to, MarkupModel from, int offset, TextRange textRange) {
    for (RangeHighlighter rangeHighlighter : from.getAllHighlighters()) {
      if (!rangeHighlighter.isValid()) continue;
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
      public void run() {
        if (Disposer.isDisposed(LanguageConsoleImpl.this)) return;
        updateSizes();
        if (myUiUpdateRunnable != null) {
          ApplicationManager.getApplication().runReadAction(myUiUpdateRunnable);
        }
      }
    });
  }

  private void updateSizes() {
    if (myFullEditor != null) return;
    myPanel.revalidate();
    myPanel.repaint();
  }

  public void dispose() {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myConsoleEditor);
    editorFactory.releaseEditor(myHistoryViewer);

    final VirtualFile virtualFile = myFile.getVirtualFile();
    assert virtualFile != null;
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    final boolean isOpen = editorManager.isFileOpen(virtualFile);
    if (isOpen) {
      editorManager.closeFile(virtualFile);
    }
  }

  public void calcData(DataKey key, DataSink sink) {
    if (OpenFileDescriptor.NAVIGATE_IN_EDITOR == key) {
      sink.put(OpenFileDescriptor.NAVIGATE_IN_EDITOR, myConsoleEditor);
      return;
    }
    final Object o =
      ((FileEditorManagerImpl)FileEditorManager.getInstance(getProject())).getData(key.getName(), myConsoleEditor, myFile.getVirtualFile());
    sink.put(key, o);
  }

  private void installEditorFactoryListener() {
    myProject.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(FileEditorManager source, VirtualFile file) {
        if (file != myFile.getVirtualFile()) return;
        if (myConsoleEditor != null) {
          queueUiUpdate(false);
          for (FileEditor fileEditor : source.getAllEditors(file)) {
            if (!(fileEditor instanceof TextEditor)) continue;
            final Editor editor = ((TextEditor)fileEditor).getEditor();
            EmptyAction.registerActionShortcuts(editor.getComponent(), myConsoleEditor.getComponent());
            editor.getCaretModel().addCaretListener(new CaretListener() {
              public void caretPositionChanged(CaretEvent e) {
                queueUiUpdate(false);
              }
            });
          }
        }
      }

      @Override
      public void fileClosed(FileEditorManager source, VirtualFile file) {
        if (file != myFile.getVirtualFile()) return;
        if (myUiUpdateRunnable != null) {
          ApplicationManager.getApplication().runReadAction(myUiUpdateRunnable);
        }
      }
    });
  }

  public Editor getCurrentEditor() {
    return myCurrentEditor;
  }

  public void setLanguage(Language language) {
    final PsiFile prevFile = myFile;
    if (prevFile != null) {
      final VirtualFile file = prevFile.getVirtualFile();
      assert file instanceof LightVirtualFile;
      ((LightVirtualFile)file).setValid(false);
      ((PsiManagerEx)prevFile.getManager()).getFileManager().setViewProvider(file, null);
    }

    @NonNls final String name = getTitle();
    final LightVirtualFile newVFile = new LightVirtualFile(name, language, "");
    myFile = setDocumentFileAndInitPsi(myProject, myEditorDocument, newVFile);

    if (prevFile != null) {
      final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(getProject());
      final VirtualFile file = prevFile.getVirtualFile();
      if (file != null && myFullEditor != null) {
        myFullEditor = null;
        final FileEditor prevEditor = editorManager.getSelectedEditor(file);
        final boolean focusEditor;
        final int offset;
        if (prevEditor != null) {
          offset = prevEditor instanceof TextEditor ? ((TextEditor)prevEditor).getEditor().getCaretModel().getOffset() : 0;
          final Component owner = FocusManager.getCurrentManager().getFocusOwner();
          focusEditor = owner != null && SwingUtilities.isDescendingFrom(owner, prevEditor.getComponent());
        }
        else {
          focusEditor = false;
          offset = 0;
        }
        editorManager.closeFile(file);
        myFullEditor = editorManager.openTextEditor(new OpenFileDescriptor(getProject(), newVFile, offset), focusEditor);
        configureFullEditor();
        setConsoleFilePinned(editorManager);
      }
    }
  }

  private void configureFullEditor() {
    if (myFullEditor == null || myFullEditorActions == null) return;
    final JPanel header = new JPanel(new BorderLayout());
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, myFullEditorActions, true);
    actionToolbar.setTargetComponent(myFullEditor.getContentComponent());
    header.add(actionToolbar.getComponent(), BorderLayout.EAST);
    myFullEditor.setHeaderComponent(header);
    myFullEditor.getSettings().setLineMarkerAreaShown(false);
  }

  public void setInputText(final String query) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myConsoleEditor.getDocument().setText(query);
      }
    });
  }

  public static void printToConsole(final LanguageConsoleImpl console,
                                    final String string,
                                    final ConsoleViewContentType mainType,
                                    ConsoleViewContentType additionalType) {
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
        public void run() {
          console.printToHistory(string, attributes);
        }
      }, ModalityState.stateForComponent(console.getComponent()));
    }
  }

  // hack-utility method for setting PSI for existing document
  public static PsiFile setDocumentFileAndInitPsi(final Project project, final Document document, final LightVirtualFile newVFile) {
    newVFile.setContent(document, document.getText(), false);
    FileDocumentManagerImpl.registerDocument(document, newVFile);
    final PsiFile psiFile = ((PsiFileFactoryImpl)PsiFileFactory.getInstance(project)).trySetupPsiForFile(newVFile, newVFile.getLanguage(), true, false);
    if (psiFile == null) {
      throw new AssertionError("PSI=null for light file: name=" + newVFile.getName() + ", language=" + newVFile.getLanguage().getDisplayName());
    }
    PsiDocumentManagerImpl.cachePsi(document, psiFile);
    FileContentUtil.reparseFiles(project, Collections.<VirtualFile>singletonList(newVFile), false);
    return psiFile;
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
      final EditorEx editor = componentCount == 2? myConsoleEditor : null;

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
      editor.getSettings().setAdditionalColumnsCount(2 + (width - editorSize.width) / EditorUtil.getSpaceWidth(Font.PLAIN, editor));
      history.getSettings().setAdditionalColumnsCount(2 + (width - historySize.width) / EditorUtil.getSpaceWidth(Font.PLAIN, history));

      // deal with height
      if (historySize.width == 0) historySize.height = 0;
      final int minHistorySize = historySize.height > 0 ? 2 * history.getLineHeight() + SEPARATOR_THICKNESS : 0;
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
