/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.impl;

import com.intellij.codeInsight.navigation.IncrementalSearchHandler;
import com.intellij.execution.ConsoleFolding;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.console.FoldLinesWithSubstring;
import com.intellij.execution.filters.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.actions.DiffActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Alarm;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class ConsoleViewImpl extends JPanel implements ConsoleView, ObservableConsoleView, DataProvider, OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.ConsoleViewImpl");

  private static final int FLUSH_DELAY = 200; //TODO : make it an option

  private static final Key<ConsoleViewImpl> CONSOLE_VIEW_IN_EDITOR_VIEW = Key.create("CONSOLE_VIEW_IN_EDITOR_VIEW");
  static {
    final EditorActionManager actionManager = EditorActionManager.getInstance();
    final TypedAction typedAction = actionManager.getTypedAction();
    typedAction.setupHandler(new MyTypedHandler(typedAction.getHandler()));
  }

  private final DisposedPsiManagerCheck myPsiDisposedCheck;
  private ConsoleState myState = ConsoleState.NOT_STARTED;
  private final int CYCLIC_BUFFER_SIZE = getCycleBufferSize();
  private final boolean isViewer;
  private Computable<ModalityState> myStateForUpdate;

  private static int getCycleBufferSize() {
    final String cycleBufferSizeProperty = System.getProperty("idea.cycle.buffer.size");
    if (cycleBufferSizeProperty == null) return 1024 * 1024;
    try {
      return Integer.parseInt(cycleBufferSizeProperty) * 1024;
    }
    catch (NumberFormatException e) {
      return 1024 * 1024;
    }
  }

  private final boolean USE_CYCLIC_BUFFER = useCycleBuffer();

  private static boolean useCycleBuffer() {
    final String useCycleBufferProperty = System.getProperty("idea.cycle.buffer.size");
    return useCycleBufferProperty == null || !"disabled".equalsIgnoreCase(useCycleBufferProperty);
  }

  private static final int HYPERLINK_LAYER = HighlighterLayer.SELECTION - 123;
  private final Alarm mySpareTimeAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  private final CopyOnWriteArraySet<ChangeListener> myListeners = new CopyOnWriteArraySet<ChangeListener>();
  private final Set<ConsoleViewContentType> myDeferredTypes = new HashSet<ConsoleViewContentType>();
  private final ArrayList<AnAction> customActions = new ArrayList<AnAction>();

  @TestOnly
  public Editor getEditor() {
    return myEditor;
  }

  public void scrollToEnd() {
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
  }

  private static class TokenInfo{
    private final ConsoleViewContentType contentType;
    private int startOffset;
    private int endOffset;
    private final TextAttributes attributes;

    private TokenInfo(final ConsoleViewContentType contentType, final int startOffset, final int endOffset) {
      this.contentType = contentType;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      attributes = contentType.getAttributes();
    }
  }

  private final Project myProject;

  private boolean myOutputPaused;

  private Editor myEditor;

  private final Object LOCK = new Object();

  private int myContentSize;
  private StringBuffer myDeferredOutput = new StringBuffer();
  private StringBuffer myDeferredUserInput = new StringBuffer();

  private ArrayList<TokenInfo> myTokens = new ArrayList<TokenInfo>();
  private final Hyperlinks myHyperlinks = new Hyperlinks();
  private final TIntObjectHashMap<ConsoleFolding> myFolding = new TIntObjectHashMap<ConsoleFolding>();

  private String myHelpId;

  private final Alarm myFlushUserInputAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, this);
  private final Alarm myFlushAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  private final Runnable myFlushDeferredRunnable = new Runnable() {
    public void run() {
      flushDeferredText();
    }
  };

  protected final CompositeFilter myPredefinedMessageFilter;
  protected final CompositeFilter myCustomFilter;

  private final ArrayList<String> myHistory = new ArrayList<String>();
  private int myHistorySize = 20;

  private final ArrayList<ConsoleInputListener> myConsoleInputListeners = new ArrayList<ConsoleInputListener>();

  private final Alarm myFoldingAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  private final List<FoldRegion> myPendingFoldRegions = new ArrayList<FoldRegion>();

  public void addConsoleUserInputListener(ConsoleInputListener consoleInputListener) {
    myConsoleInputListeners.add(consoleInputListener);
  }

  /**
   * By default history works for one session. If
   * you want to import previous session, set it up here.
   * @param history where you can save history
   */
  public void importHistory(Collection<String> history) {
    this.myHistory.clear();
    this.myHistory.addAll(history);
    while (this.myHistory.size() > myHistorySize) {
      this.myHistory.remove(0);
    }
  }

  public List<String> getHistory() {
    return Collections.unmodifiableList(myHistory);
  }

  public void setHistorySize(int historySize) {
    this.myHistorySize = historySize;
  }

  public int getHistorySize() {
    return myHistorySize;
  }

  private FileType myFileType;

  /**
   * Use it for custom highlighting for user text.
   * This will be highlighted as appropriate file to this file type.
   * @param fileType according to which use highlighting
   */
  public void setFileType(FileType fileType) {
    myFileType = fileType;
  }

  public ConsoleViewImpl(final Project project, boolean viewer) {
    this(project, viewer, null);
  }

  public ConsoleViewImpl(final Project project, boolean viewer, FileType fileType) {
    this(project, GlobalSearchScope.allScope(project), viewer, fileType);
  }


  public ConsoleViewImpl(final Project project, GlobalSearchScope searchScope, boolean viewer, FileType fileType) {
    super(new BorderLayout());
    isViewer = viewer;
    myPsiDisposedCheck = new DisposedPsiManagerCheck(project);
    myProject = project;
    myFileType = fileType;

    myCustomFilter = new CompositeFilter(project);
    myPredefinedMessageFilter = new CompositeFilter(project);
    for (ConsoleFilterProvider eachProvider : Extensions.getExtensions(ConsoleFilterProvider.FILTER_PROVIDERS)) {
      Filter[] filters = eachProvider instanceof ConsoleFilterProviderEx
                         ? ((ConsoleFilterProviderEx)eachProvider).getDefaultFilters(project, searchScope)
                         : eachProvider.getDefaultFilters(project);
      for (Filter filter : filters) {
        myPredefinedMessageFilter.addFilter(filter);
      }
    }

    Disposer.register(project, this);
  }

  public void attachToProcess(final ProcessHandler processHandler){
    myState = myState.attachTo(this, processHandler);
  }

  public void clear() {
    assertIsDispatchThread();

    final Document document;
    synchronized(LOCK){
      myContentSize = 0;
      if (USE_CYCLIC_BUFFER) {
        myDeferredOutput = new StringBuffer(Math.min(myDeferredOutput.length(), CYCLIC_BUFFER_SIZE));
      }
      else {
        myDeferredOutput = new StringBuffer();
      }
      myDeferredTypes.clear();
      myDeferredUserInput = new StringBuffer();
      myHyperlinks.clear();
      myTokens.clear();
      if (myEditor == null) return;
      myEditor.getMarkupModel().removeAllHighlighters();
      document = myEditor.getDocument();
      myFoldingAlarm.cancelAllRequests();
    }
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        document.deleteString(0, document.getTextLength());
      }
    }, null, DocCommandGroupId.noneGroupId(document));
  }

  public void scrollTo(final int offset) {
    assertIsDispatchThread();
    flushDeferredText();
    if (myEditor == null) return;
    int moveOffset = offset;
    if (USE_CYCLIC_BUFFER && moveOffset >= myEditor.getDocument().getTextLength()) {
      moveOffset = 0;
    }
    myEditor.getCaretModel().moveToOffset(moveOffset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
  }

  private static void assertIsDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  public void setOutputPaused(final boolean value) {
    myOutputPaused = value;
    if (!value){
      requestFlushImmediately();
    }
  }

  public boolean isOutputPaused() {
    return myOutputPaused;
  }

  public boolean hasDeferredOutput() {
    synchronized(LOCK){
      return myDeferredOutput.length() > 0;
    }
  }

  public void performWhenNoDeferredOutput(final Runnable runnable) {
    //Q: implement in another way without timer?
    if (!hasDeferredOutput()){
      runnable.run();
    }
    else{
      mySpareTimeAlarm.addRequest(
        new Runnable() {
          public void run() {
            performWhenNoDeferredOutput(runnable);
          }
        },
        100
      );
    }
  }

  public JComponent getComponent() {
    if (myEditor == null){
      myEditor = createEditor();
      requestFlushImmediately();
      add(createCenterComponent(), BorderLayout.CENTER);

      myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          if (e.getNewLength() == 0 && e.getOffset() == 0) {
            // string has beeen removed from the beginning, move tokens down
            synchronized (LOCK) {
              int toRemoveLen = e.getOldLength();
              int tIndex = findTokenInfoIndexByOffset(toRemoveLen);
              ArrayList<TokenInfo> newTokens = new ArrayList<TokenInfo>(myTokens.subList(tIndex, myTokens.size()));
              for (TokenInfo token : newTokens) {
                token.startOffset -= toRemoveLen;
                token.endOffset -= toRemoveLen;
              }
              if (!newTokens.isEmpty()) {
                newTokens.get(0).startOffset = 0;
              }
              myContentSize -= Math.min(myContentSize, toRemoveLen);
              myTokens = newTokens;
            }
          }
        }
      });
    }
    return this;
  }

  protected JComponent createCenterComponent() {
    return myEditor.getComponent();
  }

  public void setModalityStateForUpdate(Computable<ModalityState> stateComputable) {
    myStateForUpdate = stateComputable;
  }



  public void dispose(){
    myState = myState.dispose();
    if (myEditor != null){
      myFlushAlarm.cancelAllRequests();
      mySpareTimeAlarm.cancelAllRequests();
      disposeEditor();
      synchronized (LOCK) {
        myDeferredOutput = new StringBuffer();
      }
      myEditor = null;
    }
  }

  protected void disposeEditor() {
    if (!myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
  }

  public void print(String s, final ConsoleViewContentType contentType) {
    synchronized(LOCK){
      myDeferredTypes.add(contentType);

      s = StringUtil.convertLineSeparators(s);
      myContentSize += s.length();
      myDeferredOutput.append(s);
      if (contentType == ConsoleViewContentType.USER_INPUT){
        myDeferredUserInput.append(s);
      }

      boolean needNew = true;
      if (!myTokens.isEmpty()){
        final TokenInfo lastToken = myTokens.get(myTokens.size() - 1);
        if (lastToken.contentType == contentType){
          lastToken.endOffset = myContentSize; // optimization
          needNew = false;
        }
      }
      if (needNew){
        myTokens.add(new TokenInfo(contentType, myContentSize - s.length(), myContentSize));
      }

      if (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
        if (contentType == ConsoleViewContentType.USER_INPUT) {
          flushDeferredUserInput();
        }
      }
      if (myFlushAlarm.getActiveRequestCount() == 0 && myEditor != null) {
        final boolean shouldFlushNow = USE_CYCLIC_BUFFER && myDeferredOutput.length() > CYCLIC_BUFFER_SIZE;
        myFlushAlarm.addRequest(myFlushDeferredRunnable, shouldFlushNow? 0 : FLUSH_DELAY, getStateForUpdate());
      }
    }
  }

  private ModalityState getStateForUpdate() {
    return myStateForUpdate != null ? myStateForUpdate.compute() : ModalityState.stateForComponent(this);
  }

  private void requestFlushImmediately() {
    if (myEditor != null) {
      myFlushAlarm.addRequest(myFlushDeferredRunnable, 0, getStateForUpdate());
    }
  }

  public int getContentSize() { return myContentSize; }

  public boolean canPause() {
    return true;
  }

  private void flushDeferredText() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myProject.isDisposed()) {
      return;
    }

    final String text;
    synchronized (LOCK) {
      if (myOutputPaused) return;
      if (myDeferredOutput.length() == 0) return;
      if (myEditor == null) return;

      text = myDeferredOutput.substring(0, myDeferredOutput.length());
      if (USE_CYCLIC_BUFFER) {
        myDeferredOutput = new StringBuffer(Math.min(myDeferredOutput.length(), CYCLIC_BUFFER_SIZE));
      }
      else {
        myDeferredOutput.setLength(0);
      }
    }
    final Document document = myEditor.getDocument();
    final int oldLineCount = document.getLineCount();
    final boolean isAtEndOfDocument = myEditor.getCaretModel().getOffset() == document.getTextLength();
    boolean cycleUsed = USE_CYCLIC_BUFFER && document.getTextLength() + text.length() > CYCLIC_BUFFER_SIZE;
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        document.insertString(document.getTextLength(), text);
        synchronized (LOCK) {
          fireChange();
        }
      }
    }, null, DocCommandGroupId.noneGroupId(document));
    myPsiDisposedCheck.performCheck();
    final int newLineCount = document.getLineCount();
    if (cycleUsed) {
      final int lineCount = LineTokenizer.calcLineCount(text, true);
      for (Iterator<RangeHighlighter> it = myHyperlinks.getRanges().keySet().iterator(); it.hasNext();) {
        if (!it.next().isValid()) {
          it.remove();
        }
      }
      highlightHyperlinksAndFoldings(newLineCount >= lineCount + 1 ? newLineCount - lineCount - 1 : 0, newLineCount - 1);
    } 
    else if (oldLineCount < newLineCount) {
      highlightHyperlinksAndFoldings(oldLineCount - 1, newLineCount - 2);
    }

    if (isAtEndOfDocument) {
      myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
      myEditor.getSelectionModel().removeSelection();
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  private void flushDeferredUserInput() {
    final String text = myDeferredUserInput.substring(0, myDeferredUserInput.length());
    final int index = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));
    if (index < 0) return;
    final String textToSend = text.substring(0, index + 1);
    myDeferredUserInput.setLength(0);
    myDeferredUserInput.append(text.substring(index + 1));
    myFlushUserInputAlarm.addRequest(new Runnable() {
      public void run() {
        if (myState.isRunning()) {
          try {
            // this may block forever, see IDEA-54340
            myState.sendUserInput(textToSend);
          }
          catch (IOException e) {
            return;
          }
        }
      }
    }, 0);
  }

  public Object getData(final String dataId) {
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)){
      if (myEditor == null) {
        return null;
      }
      final LogicalPosition pos = myEditor.getCaretModel().getLogicalPosition();
      final HyperlinkInfo info = getHyperlinkInfoByLineAndCol(pos.line, pos.column);
      final OpenFileDescriptor openFileDescriptor = info instanceof FileHyperlinkInfo ? ((FileHyperlinkInfo)info).getDescriptor() : null;
      if (openFileDescriptor == null || !openFileDescriptor.getFile().isValid()) {
        return null;
      }
      return openFileDescriptor;
    }

    if (PlatformDataKeys.EDITOR.is(dataId)) {
      return myEditor;
    }
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return myHelpId;
    }
    return null;
  }

  public void setHelpId(final String helpId) {
    myHelpId = helpId;
  }

  public void addMessageFilter(final Filter filter) {
    myCustomFilter.addFilter(filter);
  }

  public void printHyperlink(final String hyperlinkText, final HyperlinkInfo info) {
    if (myEditor == null) return;
    print(hyperlinkText, ConsoleViewContentType.NORMAL_OUTPUT);
    flushDeferredText();
    final int textLength = myEditor.getDocument().getTextLength();
    addHyperlink(textLength - hyperlinkText.length(), textLength, null, info, getHyperlinkAttributes());
  }

  public static TextAttributes getHyperlinkAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
  }

  public static TextAttributes getFollowedHyperlinkAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES);
  }
  
  private Editor createEditor() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Editor>() {
      public Editor compute() {
        return doCreateEditor();
      }
    });
  }

  private Editor doCreateEditor() {
    final EditorEx editor = createRealEditor();
    editor.addEditorMouseListener(new EditorMouseAdapter() {
      public void mouseReleased(final EditorMouseEvent e) {
        final MouseEvent mouseEvent = e.getMouseEvent();
        if (!mouseEvent.isPopupTrigger()) {
          navigate(e);
        }
      }
    });

    editor.addEditorMouseListener(new EditorPopupHandler() {
      public void invokePopup(final EditorMouseEvent event) {
        final MouseEvent mouseEvent = event.getMouseEvent();
        popupInvoked(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
      }
    });


    final int bufferSize = USE_CYCLIC_BUFFER ? CYCLIC_BUFFER_SIZE : 0;
    editor.getDocument().setCyclicBufferSize(bufferSize);

    editor.putUserData(CONSOLE_VIEW_IN_EDITOR_VIEW, this);

    editor.getContentComponent().addMouseMotionListener(
      new MouseMotionAdapter(){
        public void mouseMoved(final MouseEvent e){
          final HyperlinkInfo info = getHyperlinkInfoByPoint(e.getPoint());
          if (info != null){
            editor.getContentComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          }
          else{
            editor.getContentComponent().setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
          }
        }
      }
    );
    return editor;
  }

  protected EditorEx createRealEditor() {
    final EditorFactoryImpl document = (EditorFactoryImpl) EditorFactory.getInstance();
    final Document editorDocument = document.createDocument(true);
    editorDocument.addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        if (myFileType != null) {
          highlightUserTokens();
        }
      }
    });
    final EditorEx editor = (EditorEx)document.createViewer(editorDocument, myProject);
    final EditorHighlighter highlighter = new MyHighlighter();
    editor.setHighlighter(highlighter);

    final EditorSettings editorSettings = editor.getSettings();
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(true);
    editorSettings.setAdditionalPageAtBottom(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(0);

    final EditorColorsScheme scheme = editor.getColorsScheme();
    editor.setBackgroundColor(scheme.getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY));
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);
    scheme.setColor(EditorColors.RIGHT_MARGIN_COLOR, null);

    final ConsoleViewImpl consoleView = this;
    editor.getContentComponent().addKeyListener(new KeyListener() {
      private int historyPosition = myHistory.size();

      public void keyTyped(KeyEvent e) {

      }

      public void keyPressed(KeyEvent e) {
      }

      public void keyReleased(KeyEvent e) {
        if (e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
          if (e.getKeyCode() == KeyEvent.VK_UP) {
            historyPosition--;
            if (historyPosition < 0) historyPosition = 0;
            replaceString();
            e.consume();
          }
          else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            historyPosition++;
            if (historyPosition > myHistory.size()) historyPosition = myHistory.size();
            replaceString();
            e.consume();
          }
        }
        else {
          historyPosition = myHistory.size();
        }
      }

      private void replaceString() {
        final String str;

        if (myHistory.size() == historyPosition) str = "";
        else str = myHistory.get(historyPosition);
        synchronized (LOCK) {
          if (myTokens.isEmpty()) return;
          final TokenInfo info = myTokens.get(myTokens.size() - 1);
          if (info.contentType != ConsoleViewContentType.USER_INPUT) {
            consoleView.insertUserText(str, 0);
          }
          else {
            consoleView.replaceUserText(str, info.startOffset, info.endOffset);
          }
        }
      }
    });

    setEditorUpActions(editor);
    return editor;
  }

  private void highlightUserTokens() {
    if (myTokens.isEmpty()) return;
    final TokenInfo token = myTokens.get(myTokens.size() - 1);
    if (token.contentType == ConsoleViewContentType.USER_INPUT) {
      String text = myEditor.getDocument().getText().substring(token.startOffset, token.endOffset);
      PsiFile file = PsiFileFactory.getInstance(myProject).
        createFileFromText("dummy", myFileType, text, LocalTimeCounter.currentTime(), true);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
      assert document != null;
      Editor editor = EditorFactory.getInstance().createEditor(document, myProject, myFileType, false);
      try {
        RangeHighlighter[] allHighlighters = myEditor.getMarkupModel().getAllHighlighters();
        for (RangeHighlighter highlighter : allHighlighters) {
          if (highlighter.getStartOffset() >= token.startOffset) {
            myEditor.getMarkupModel().removeHighlighter(highlighter);
          }
        }
        HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(0);
        while (!iterator.atEnd()) {
          myEditor.getMarkupModel().addRangeHighlighter(iterator.getStart() + token.startOffset, iterator.getEnd() + token.startOffset, HighlighterLayer.SYNTAX,
                                                        iterator.getTextAttributes(),
                                                        HighlighterTargetArea.EXACT_RANGE);
          iterator.advance();
        }
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    }
  }

  private static void setEditorUpActions(final Editor editor) {
    new EnterHandler().registerCustomShortcutSet(CommonShortcuts.ENTER, editor.getContentComponent());
    registerActionHandler(editor, IdeActions.ACTION_EDITOR_PASTE, new PasteHandler());
    registerActionHandler(editor, IdeActions.ACTION_EDITOR_BACKSPACE, new BackSpaceHandler());
    registerActionHandler(editor, IdeActions.ACTION_EDITOR_DELETE, new DeleteHandler());
  }

  private static void registerActionHandler(final Editor editor, final String actionId, final AnAction action) {
    final Keymap keymap=KeymapManager.getInstance().getActiveKeymap();
    final Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    action.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), editor.getContentComponent());
  }

  private void popupInvoked(final Component component, final int x, final int y){
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ClearAllAction());
    group.add(new CopyAction());
    group.add(new FoldLinesWithSubstring(myEditor));
    group.addSeparator();
    final ActionManager actionManager = ActionManager.getInstance();
    group.add(actionManager.getAction(DiffActions.COMPARE_WITH_CLIPBOARD));
    final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group);
    menu.getComponent().show(component, x, y);
  }

  private void navigate(final EditorMouseEvent event){
    if (event.getMouseEvent().isPopupTrigger()) return;
    final Point p = event.getMouseEvent().getPoint();
    final HyperlinkInfo info = getHyperlinkInfoByPoint(p);
    if (info != null){
      info.navigate(myProject);
      linkFollowed(info);
    }
  }

  public static final Key<TextAttributes> OLD_HYPERLINK_TEXT_ATTRIBUTES = Key.create("OLD_HYPERLINK_TEXT_ATTRIBUTES");
  private void linkFollowed(final HyperlinkInfo info) {
    linkFollowed(myEditor, myHyperlinks, info);
  }
  public static void linkFollowed(final Editor editor, final Hyperlinks hyperlinks, final HyperlinkInfo info) {
    MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
    for (Map.Entry<RangeHighlighter,HyperlinkInfo> entry : hyperlinks.getRanges().entrySet()) {
      RangeHighlighter range = entry.getKey();
      TextAttributes oldAttr = range.getUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES);
      if (oldAttr != null) {
        markupModel.setRangeHighlighterAttributes(range, oldAttr);
        range.putUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES, null);
      }
      if (entry.getValue() == info) {
        TextAttributes oldAttributes = range.getTextAttributes();
        range.putUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES, oldAttributes);
        TextAttributes attributes = getFollowedHyperlinkAttributes().clone();
        assert oldAttributes != null;
        attributes.setFontType(oldAttributes.getFontType());
        attributes.setEffectType(oldAttributes.getEffectType());
        attributes.setEffectColor(oldAttributes.getEffectColor());
        attributes.setForegroundColor(oldAttributes.getForegroundColor());
        markupModel.setRangeHighlighterAttributes(range, attributes);
      }
    }
    //refresh highlighter text attributes
    RangeHighlighter dummy = markupModel.addRangeHighlighter(0, 0, HYPERLINK_LAYER, getHyperlinkAttributes(), HighlighterTargetArea.EXACT_RANGE);
    markupModel.removeHighlighter(dummy);
  }
  public HyperlinkInfo getHyperlinkInfoByPoint(final Point p){
    return getHyperlinkInfoByPoint(myEditor, myHyperlinks, p);
  }

  public static HyperlinkInfo getHyperlinkInfoByPoint(final Editor editor, final Hyperlinks hyperlinks, final Point p){
    final LogicalPosition pos = editor.xyToLogicalPosition(new Point(p.x, p.y));
    return getHyperlinkInfoByLineAndCol(editor, hyperlinks, pos.line, pos.column);
  }

  private HyperlinkInfo getHyperlinkInfoByLineAndCol(final int line, final int col) {
    return getHyperlinkInfoByLineAndCol(myEditor, myHyperlinks, line, col);
  }

  public static HyperlinkInfo getHyperlinkInfoByLineAndCol(final Editor editor, final Hyperlinks hyperlinks, final int line, final int col) {
    final int offset = editor.logicalPositionToOffset(new LogicalPosition(line, col));
    return hyperlinks.getHyperlinkAt(offset);
  }

  private void highlightHyperlinksAndFoldings(final int line1, final int endLine){
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    highlightHyperlinks(myEditor, myHyperlinks, myCustomFilter, myPredefinedMessageFilter, line1, endLine);
    updateFoldings(line1, endLine);
  }

  private void updateFoldings(final int line1, final int endLine) {
    final Document document = myEditor.getDocument();
    final CharSequence chars = document.getCharsSequence();
    final int startLine = Math.max(0, line1);
    final List<FoldRegion> toAdd = new ArrayList<FoldRegion>();
    for(int line = startLine; line <= endLine; line++) {
      addFolding(document, chars, line, toAdd);
    }
    if (!toAdd.isEmpty()) {
      doUpdateFolding(toAdd);
    }
  }

  public static void highlightHyperlinks(final Editor editor,
                                         final Hyperlinks hyperlinks,
                                         final Filter myCustomFilter,
                                         final Filter myPredefinedMessageFilter,
                                         final int line1, final int endLine){
    final Document document = editor.getDocument();
    final CharSequence chars = document.getCharsSequence();
    final TextAttributes hyperlinkAttributes = getHyperlinkAttributes();

    final int startLine = Math.max(0, line1);

    for(int line = startLine; line <= endLine; line++) {
      int endOffset = document.getLineEndOffset(line);
      if (endOffset < document.getTextLength()) {
        endOffset++; // add '\n'
      }
      final String text = getLineText(document, line, true);
      Filter.Result result = myCustomFilter.applyFilter(text, endOffset);
      if (result == null) {
        result = myPredefinedMessageFilter.applyFilter(text, endOffset);
      }
      if (result != null){
        final int highlightStartOffset = result.highlightStartOffset;
        final int highlightEndOffset = result.highlightEndOffset;
        final HyperlinkInfo hyperlinkInfo = result.hyperlinkInfo;
        addHyperlink(editor, hyperlinks, highlightStartOffset, highlightEndOffset, result.highlightAttributes, hyperlinkInfo, hyperlinkAttributes);
      }
    }
  }

  private void doUpdateFolding(final List<FoldRegion> toAdd) {
    assertIsDispatchThread();
    myPendingFoldRegions.addAll(toAdd);

    myFoldingAlarm.cancelAllRequests();
    final Runnable runnable = new Runnable() {
      public void run() {
        assertIsDispatchThread();
        final FoldingModel model = myEditor.getFoldingModel();
        model.runBatchFoldingOperationDoNotCollapseCaret(new Runnable() {
          public void run() {
            assertIsDispatchThread();
            for (FoldRegion region : myPendingFoldRegions) {
              region.setExpanded(false);
              model.addFoldRegion(region);
            }
            myPendingFoldRegions.clear();
          }
        });
      }
    };
    if (myPendingFoldRegions.size() > 100) {
      runnable.run();
    } else {
      myFoldingAlarm.addRequest(runnable, 50);
    }
  }

  private void addFolding(Document document, CharSequence chars, int line, List<FoldRegion> toAdd) {
    ConsoleFolding current = foldingForLine(getLineText(document, line, false));
    myFolding.put(line, current);

    final ConsoleFolding prevFolding = myFolding.get(line - 1);
    if (current == null && prevFolding != null) {
      final int lEnd = line - 1;
      int lStart = lEnd;
      while (prevFolding.equals(myFolding.get(lStart - 1))) lStart--;
      if (lStart == lEnd) {
        return;
      }

      List<String> toFold = new ArrayList<String>(lEnd - lStart + 1);
      for (int i = lStart; i <= lEnd; i++) {
        toFold.add(getLineText(document, i, false));
      }

      int oStart = document.getLineStartOffset(lStart);
//      oStart = CharArrayUtil.shiftForward(chars, oStart, " \t");
      if (oStart > 0) oStart--;
      int oEnd = CharArrayUtil.shiftBackward(chars, document.getLineEndOffset(lEnd) - 1, " \t") + 1;

      FoldRegion region = ((FoldingModelEx)myEditor.getFoldingModel()).createFoldRegion(oStart, oEnd, prevFolding.getPlaceholderText(toFold), null);
      toAdd.add(region); 
    }
  }

  public static String getLineText(Document document, int lineNumber, boolean includeEol) {
    int endOffset = document.getLineEndOffset(lineNumber);
    if (includeEol && endOffset < document.getTextLength()) {
      endOffset++;
    }
    return document.getCharsSequence().subSequence(document.getLineStartOffset(lineNumber), endOffset).toString();
  }

  @Nullable
  private static ConsoleFolding foldingForLine(String lineText) {
    for (ConsoleFolding folding : ConsoleFolding.EP_NAME.getExtensions()) {
      if (folding.shouldFoldLine(lineText)) {
        return folding;
      }
    }
    return null;
  }

  private void addHyperlink(final int highlightStartOffset,
                            final int highlightEndOffset,
                            final TextAttributes highlightAttributes,
                            final HyperlinkInfo hyperlinkInfo,
                            final TextAttributes hyperlinkAttributes) {
    addHyperlink(myEditor, myHyperlinks, highlightStartOffset, highlightEndOffset, highlightAttributes, hyperlinkInfo, hyperlinkAttributes);
  }
  private static void addHyperlink(final Editor editor,
                                   final Hyperlinks hyperlinks,
                                   final int highlightStartOffset,
                                   final int highlightEndOffset,
                                   final TextAttributes highlightAttributes,
                                   final HyperlinkInfo hyperlinkInfo,
                                   final TextAttributes hyperlinkAttributes) {
    TextAttributes textAttributes = highlightAttributes != null ? highlightAttributes : hyperlinkAttributes;
    final RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(highlightStartOffset,
                                                                                       highlightEndOffset,
                                                                                       HYPERLINK_LAYER,
                                                                                       textAttributes,
                                                                                       HighlighterTargetArea.EXACT_RANGE);
    hyperlinks.add(highlighter, hyperlinkInfo);
  }

  private class ClearAllAction extends AnAction implements DumbAware {
    private ClearAllAction(){
      super(ExecutionBundle.message("clear.all.from.console.action.name"));
    }

    public void actionPerformed(final AnActionEvent e){
      clear();
    }
  }

  private class CopyAction extends AnAction implements DumbAware {
    private CopyAction(){
      super(myEditor != null && myEditor.getSelectionModel().hasSelection() ? ExecutionBundle.message("copy.selected.content.action.name") : ExecutionBundle.message("copy.content.action.name"));
    }

    public void actionPerformed(final AnActionEvent e){
      if (myEditor == null) return;
      if (myEditor.getSelectionModel().hasSelection()){
        myEditor.getSelectionModel().copySelectionToClipboard();
      }
      else{
        myEditor.getSelectionModel().setSelection(0, myEditor.getDocument().getTextLength());
        myEditor.getSelectionModel().copySelectionToClipboard();
        myEditor.getSelectionModel().removeSelection();
      }
    }
  }

  private class MyHighlighter extends DocumentAdapter implements EditorHighlighter {
    private HighlighterClient myEditor;

    public HighlighterIterator createIterator(final int startOffset) {
      final int startIndex = findTokenInfoIndexByOffset(startOffset);

      return new HighlighterIterator(){
        private int myIndex = startIndex;

        public TextAttributes getTextAttributes() {
          if (myFileType != null && getTokenInfo().contentType == ConsoleViewContentType.USER_INPUT) {
            return ConsoleViewContentType.NORMAL_OUTPUT.getAttributes();
          }
          return getTokenInfo() == null ? null : getTokenInfo().attributes;
        }

        public int getStart() {
          return getTokenInfo() == null ? 0 : getTokenInfo().startOffset;
        }

        public int getEnd() {
          return getTokenInfo() == null ? 0 : getTokenInfo().endOffset;
        }

        public IElementType getTokenType() {
          return null;
        }

        public void advance() {
          myIndex++;
        }

        public void retreat() {
          myIndex--;
        }

        public boolean atEnd() {
          return myIndex < 0 || myIndex >= myTokens.size();
        }

        public Document getDocument() {
          return myEditor.getDocument();
        }

        private TokenInfo getTokenInfo() {
          return myTokens.get(myIndex);
        }
      };
    }

    public void setText(final CharSequence text) {
    }

    public void setEditor(final HighlighterClient editor) {
      LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
      myEditor = editor;
    }

    public void setColorScheme(EditorColorsScheme scheme) {
    }
  }

  private int findTokenInfoIndexByOffset(final int offset) {
    int low = 0;
    int high = myTokens.size() - 1;

    while(low <= high){
      final int mid = (low + high) / 2;
      final TokenInfo midVal = myTokens.get(mid);
      if (offset < midVal.startOffset){
        high = mid - 1;
      }
      else if (offset >= midVal.endOffset){
        low = mid + 1;
      }
      else{
        return mid;
      }
    }
    return myTokens.size();
  }

  private static class MyTypedHandler implements TypedActionHandler {
    private final TypedActionHandler myOriginalHandler;

    private MyTypedHandler(final TypedActionHandler originalAction) {
      myOriginalHandler = originalAction;
    }

    public void execute(@NotNull final Editor editor, final char charTyped, @NotNull final DataContext dataContext) {
      final ConsoleViewImpl consoleView = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW);
      if (consoleView == null || !consoleView.myState.isRunning() || consoleView.isViewer){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      else{
        final String s = String.valueOf(charTyped);
        SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasSelection()) {
          consoleView.replaceUserText(s, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
        } else {
          consoleView.insertUserText(s, editor.getCaretModel().getOffset());
        }
      }
    }
  }

  private abstract static class ConsoleAction extends AnAction implements DumbAware {
    public void actionPerformed(final AnActionEvent e) {
      final DataContext context = e.getDataContext();
      final ConsoleViewImpl console = getRunningConsole(context);
      execute(console, context);
    }

    protected abstract void execute(ConsoleViewImpl console, final DataContext context);

    public void update(final AnActionEvent e) {
      final ConsoleViewImpl console = getRunningConsole(e.getDataContext());
      e.getPresentation().setEnabled(console != null);
    }

    @Nullable
    private static ConsoleViewImpl getRunningConsole(final DataContext context) {
      final Editor editor = PlatformDataKeys.EDITOR.getData(context);
      if (editor != null) {
        final ConsoleViewImpl console = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW);
        if (console != null && console.myState.isRunning()) {
          return console;
        }
      }
      return null;
    }
  }

  private static class EnterHandler extends ConsoleAction {
    public void execute(final ConsoleViewImpl consoleView, final DataContext context) {
      synchronized (consoleView.LOCK) {
        String str = consoleView.myDeferredUserInput.toString();
        if (StringUtil.isNotEmpty(str)) {
          consoleView.myHistory.remove(str);
          consoleView.myHistory.add(str);
          if (consoleView.myHistory.size() > consoleView.myHistorySize) consoleView.myHistory.remove(0);
        }
        for (ConsoleInputListener listener : consoleView.myConsoleInputListeners) {
          listener.textEntered(str);
        }
      }
      consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      consoleView.flushDeferredText();
      final Editor editor = consoleView.myEditor;
      editor.getCaretModel().moveToOffset(editor.getDocument().getTextLength());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  private static class PasteHandler extends ConsoleAction {
    public void execute(final ConsoleViewImpl consoleView, final DataContext context) {
      final Transferable content = CopyPasteManager.getInstance().getContents();
      if (content == null) return;
      String s = null;
      try {
        s = (String)content.getTransferData(DataFlavor.stringFlavor);
      }
      catch(Exception e) {
        consoleView.getToolkit().beep();
      }
      if (s == null) return;
      Editor editor = consoleView.myEditor;
      SelectionModel selectionModel = editor.getSelectionModel();
      if (selectionModel.hasSelection()) {
        consoleView.replaceUserText(s, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      } else {
        consoleView.insertUserText(s, editor.getCaretModel().getOffset());
      }
    }
  }

  private static class BackSpaceHandler extends ConsoleAction {
    public void execute(final ConsoleViewImpl consoleView, final DataContext context) {
      final Editor editor = consoleView.myEditor;

      if (IncrementalSearchHandler.isHintVisible(editor)) {
        getDefaultActionHandler().execute(editor, context);
        return;
      }

      final Document document = editor.getDocument();
      final int length = document.getTextLength();
      if (length == 0) {
        return;
      }

      SelectionModel selectionModel = editor.getSelectionModel();
      if (selectionModel.hasSelection()) {
        consoleView.deleteUserText(selectionModel.getSelectionStart(),
                                   selectionModel.getSelectionEnd() - selectionModel.getSelectionStart());
      } else if (editor.getCaretModel().getOffset() > 0) {
        consoleView.deleteUserText(editor.getCaretModel().getOffset() - 1, 1);
      }
    }

    private static EditorActionHandler getDefaultActionHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
    }
  }

  private static class DeleteHandler extends ConsoleAction {
    public void execute(final ConsoleViewImpl consoleView, final DataContext context) {
      final Editor editor = consoleView.myEditor;

      if (IncrementalSearchHandler.isHintVisible(editor)) {
        getDefaultActionHandler().execute(editor, context);
        return;
      }

      final Document document = editor.getDocument();
      final int length = document.getTextLength();
      if (length == 0) {
        return;
      }

      SelectionModel selectionModel = editor.getSelectionModel();
      if (selectionModel.hasSelection()) {
        consoleView.deleteUserText(selectionModel.getSelectionStart(),
                                   selectionModel.getSelectionEnd() - selectionModel.getSelectionStart());
      } else {
        consoleView.deleteUserText(editor.getCaretModel().getOffset(), 1);
      }
    }

    private static EditorActionHandler getDefaultActionHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
    }
  }

  public static class Hyperlinks {
    private static final int NO_INDEX = Integer.MIN_VALUE;
    private final Map<RangeHighlighter,HyperlinkInfo> myHighlighterToMessageInfoMap = new HashMap<RangeHighlighter, HyperlinkInfo>();
    private int myLastIndex = NO_INDEX;

    public void clear() {
      myHighlighterToMessageInfoMap.clear();
      myLastIndex = NO_INDEX;
    }

    public HyperlinkInfo getHyperlinkAt(final int offset) {
      for (final RangeHighlighter highlighter : myHighlighterToMessageInfoMap.keySet()) {
        if (highlighter.isValid() && containsOffset(offset, highlighter)) {
          return myHighlighterToMessageInfoMap.get(highlighter);
        }
      }
      return null;
    }

    private static boolean containsOffset(final int offset, final RangeHighlighter highlighter) {
      return highlighter.getStartOffset() <= offset && offset <= highlighter.getEndOffset();
    }

    public void add(final RangeHighlighter highlighter, final HyperlinkInfo hyperlinkInfo) {
      myHighlighterToMessageInfoMap.put(highlighter, hyperlinkInfo);
      if (myLastIndex != NO_INDEX && containsOffset(myLastIndex, highlighter)) myLastIndex = NO_INDEX;
    }

    public Map<RangeHighlighter,HyperlinkInfo> getRanges() {
      return myHighlighterToMessageInfoMap;
    }
  }

  public JComponent getPreferredFocusableComponent() {
    //ensure editor created
    getComponent();
    return myEditor.getContentComponent();
  }


  // navigate up/down in stack trace
  public boolean hasNextOccurence() {
    return next(1, false) != null;
  }

  public boolean hasPreviousOccurence() {
    return next(-1, false) != null;
  }

  public OccurenceInfo goNextOccurence() {
    return next(1, true);
  }

  private OccurenceInfo next(final int delta, boolean doMove) {
    List<RangeHighlighter> ranges = new ArrayList<RangeHighlighter>(myHyperlinks.getRanges().keySet());
    for (Iterator<RangeHighlighter> iterator = ranges.iterator(); iterator.hasNext();) {
      RangeHighlighter highlighter = iterator.next();
      if (myEditor.getFoldingModel().getCollapsedRegionAtOffset(highlighter.getStartOffset()) != null) {
        iterator.remove();
      }
    }
    Collections.sort(ranges, new Comparator<RangeHighlighter>() {
      public int compare(final RangeHighlighter o1, final RangeHighlighter o2) {
        return o1.getStartOffset() - o2.getStartOffset();
      }
    });
    int i;
    for (i = 0; i<ranges.size(); i++) {
      RangeHighlighter range = ranges.get(i);
      if (range.getUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES) != null) {
        break;
      }
    }
    int newIndex = ranges.isEmpty() ? -1 : i == ranges.size() ? 0 : (i + delta + ranges.size()) % ranges.size();
    RangeHighlighter next = newIndex < ranges.size() && newIndex >= 0 ? ranges.get(newIndex) : null;
    if (next == null) return null;
    if (doMove) {
      scrollTo(next.getStartOffset());
    }
    final HyperlinkInfo hyperlinkInfo = myHyperlinks.getRanges().get(next);
    return new OccurenceInfo(new Navigatable.Adapter() {
      public void navigate(final boolean requestFocus) {
        hyperlinkInfo.navigate(myProject);
        linkFollowed(hyperlinkInfo);
      }
    }, i, ranges.size());
  }

  public OccurenceInfo goPreviousOccurence() {
    return next(-1, true);
  }

  public String getNextOccurenceActionName() {
    return ExecutionBundle.message("down.the.stack.trace");
  }

  public String getPreviousOccurenceActionName() {
    return ExecutionBundle.message("up.the.stack.trace");
  }

  public void addCustomConsoleAction(@NotNull AnAction action) {
    customActions.add(action);
  }

  @NotNull
  public AnAction[] createConsoleActions() {
    //Initializing prev and next occurrences actions
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    AnAction prevAction = actionsManager.createPrevOccurenceAction(this);
    prevAction.getTemplatePresentation().setText(getPreviousOccurenceActionName());
    AnAction nextAction = actionsManager.createNextOccurenceAction(this);
    nextAction.getTemplatePresentation().setText(getNextOccurenceActionName());
    //Initializing custom actions
    AnAction[] consoleActions = new AnAction[2 + customActions.size()];
    consoleActions[0] = prevAction;
    consoleActions[1] = nextAction;
    for (int i = 0; i < customActions.size(); ++i) {
      consoleActions[i + 2] = customActions.get(i);
    }
    return consoleActions;
  }

  public void setEditorEnabled(boolean enabled) {
    myEditor.getContentComponent().setEnabled(enabled);
  }

  private void fireChange() {
    if (myDeferredTypes.isEmpty()) return;
    Collection<ConsoleViewContentType> types = Collections.unmodifiableCollection(myDeferredTypes);

    for (ChangeListener each : myListeners) {
      each.contentAdded(types);
    }

    myDeferredTypes.clear();
  }

  public void addChangeListener(final ChangeListener listener, final Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  /**
   * insert text to document
   * @param s inserted text
   * @param offset relatively to all document text
   */
  private void insertUserText(final String s, int offset) {
    final ConsoleViewImpl consoleView = this;
    final Editor editor = consoleView.myEditor;
    final Document document = editor.getDocument();
    final int startOffset;

    synchronized (consoleView.LOCK) {
      if (consoleView.myTokens.isEmpty()) return;
      final TokenInfo info = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
      if (info.contentType != ConsoleViewContentType.USER_INPUT && !s.contains("\n")) {
        consoleView.print(s, ConsoleViewContentType.USER_INPUT);
        consoleView.flushDeferredText();
        editor.getCaretModel().moveToOffset(document.getTextLength());
        editor.getSelectionModel().removeSelection();
        return;
      } else if (info.contentType != ConsoleViewContentType.USER_INPUT) {
        insertUserText("temp", offset);
        final TokenInfo newInfo = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
        replaceUserText(s, newInfo.startOffset, newInfo.endOffset);
        return;
      }
      int charCountToAdd;

      final int deferredOffset = myContentSize - consoleView.myDeferredUserInput.length();
      if (offset > info.endOffset) {
        startOffset = info.endOffset;
      }
      else if (offset < info.startOffset) {
        startOffset = Math.max(info.startOffset, deferredOffset);
      } else {
        startOffset = Math.max(offset, deferredOffset);
      }

      consoleView.myDeferredUserInput.insert(startOffset - deferredOffset, s);

      charCountToAdd = s.length();
      info.endOffset += charCountToAdd;
      consoleView.myContentSize += charCountToAdd;
    }

    document.insertString(startOffset, s);
    // Math.max is needed when cyclic buffer is used
    editor.getCaretModel().moveToOffset(Math.min(startOffset + s.length(), document.getTextLength()));
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  /**
   * replace text
   * @param s text for replace
   * @param start relativly to all document text
   * @param end relativly to all document text
   */
  private void replaceUserText(final String s, int start, int end) {
    if (start == end) {
      insertUserText(s, start);
      return;
    }
    final ConsoleViewImpl consoleView = this;
    final Editor editor = consoleView.myEditor;
    final Document document = editor.getDocument();
    final int startOffset;
    final int endOffset;

    synchronized (consoleView.LOCK) {
      if (consoleView.myTokens.isEmpty()) return;
      final TokenInfo info = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
      if (info.contentType != ConsoleViewContentType.USER_INPUT) {
        consoleView.print(s, ConsoleViewContentType.USER_INPUT);
        consoleView.flushDeferredText();
        editor.getCaretModel().moveToOffset(document.getTextLength());
        editor.getSelectionModel().removeSelection();
        return;
      }
      if (consoleView.myDeferredUserInput.length() == 0) return;
      int charCountToReplace;

      final int deferredOffset = myContentSize - consoleView.myDeferredUserInput.length();

      startOffset = getStartOffset(start, info, deferredOffset);
      endOffset = getEndOffset(end, info);

      if (startOffset == -1 ||
          endOffset == -1 ||
          endOffset <= startOffset) {
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToOffset(start);
        return;
      }
      charCountToReplace = s.length() - endOffset + startOffset;

      consoleView.myDeferredUserInput.replace(startOffset - deferredOffset, endOffset - deferredOffset, s);

      info.endOffset += charCountToReplace;
      if (info.startOffset == info.endOffset) {
        consoleView.myTokens.remove(consoleView.myTokens.size() - 1);
      }
      consoleView.myContentSize += charCountToReplace;
    }

    document.replaceString(startOffset, endOffset, s);
    editor.getCaretModel().moveToOffset(startOffset + s.length());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  /**
   * delete text
   * @param offset relativly to all document text
   * @param length lenght of deleted text
   */
  private void deleteUserText(int offset, int length) {
    ConsoleViewImpl consoleView = this;
    final Editor editor = consoleView.myEditor;
    final Document document = editor.getDocument();
    final int startOffset;
    final int endOffset;

    synchronized (consoleView.LOCK) {
      if (consoleView.myTokens.isEmpty()) return;
      final TokenInfo info = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
      if (info.contentType != ConsoleViewContentType.USER_INPUT) return;
      if (consoleView.myDeferredUserInput.length() == 0) return;
      int charCountToDelete;

      final int deferredOffset = myContentSize - consoleView.myDeferredUserInput.length();
      startOffset = getStartOffset(offset, info, deferredOffset);
      endOffset = getEndOffset(offset + length, info);
      if (startOffset == -1 ||
          endOffset == -1 ||
          endOffset <= startOffset ||
          startOffset < deferredOffset) {
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToOffset(offset);
        return;
      }

      consoleView.myDeferredUserInput.delete(startOffset - deferredOffset, endOffset - deferredOffset);
      charCountToDelete = endOffset - startOffset;

      info.endOffset -= charCountToDelete;
      if (info.startOffset == info.endOffset) {
        consoleView.myTokens.remove(consoleView.myTokens.size() - 1);
      }
      consoleView.myContentSize -= charCountToDelete;
    }

    document.deleteString(startOffset, endOffset);
    editor.getCaretModel().moveToOffset(startOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  //util methods for add, replace, delete methods
  private static int getStartOffset(int offset, TokenInfo info, int deferredOffset) {
    int startOffset;
    if (offset >= info.startOffset && offset < info.endOffset) {
      startOffset = Math.max(offset, deferredOffset);
    } else if (offset < info.startOffset) {
      startOffset = Math.max(info.startOffset, deferredOffset);
    } else {
      startOffset = -1;
    }
    return startOffset;
  }

  private static int getEndOffset(int offset, TokenInfo info) {
    int endOffset;
    if (offset > info.endOffset) {
      endOffset = info.endOffset;
    } else if (offset <= info.startOffset) {
      endOffset = -1;
    } else {
      endOffset = offset;
    }
    return endOffset;
  }
}

