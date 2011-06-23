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
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.execution.ConsoleFolding;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ConsoleActionsPostProcessor;
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
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
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
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class ConsoleViewImpl extends JPanel implements ConsoleView, ObservableConsoleView, DataProvider, OccurenceNavigator {
  @NonNls private static final String CONSOLE_VIEW_POPUP_MENU = "ConsoleView.PopupMenu";
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.ConsoleViewImpl");

  private static final int FLUSH_DELAY = 200; //TODO : make it an option

  private static final Key<ConsoleViewImpl> CONSOLE_VIEW_IN_EDITOR_VIEW = Key.create("CONSOLE_VIEW_IN_EDITOR_VIEW");

  static {
    final EditorActionManager actionManager = EditorActionManager.getInstance();
    final TypedAction typedAction = actionManager.getTypedAction();
    typedAction.setupHandler(new MyTypedHandler(typedAction.getHandler()));
  }

  private final CommandLineFolding myCommandLineFolding = new CommandLineFolding();

  private final DisposedPsiManagerCheck myPsiDisposedCheck;
  private final boolean isViewer;

  private ConsoleState myState;

  private Computable<ModalityState> myStateForUpdate;

  private final Alarm mySpareTimeAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  private final CopyOnWriteArraySet<ChangeListener> myListeners = new CopyOnWriteArraySet<ChangeListener>();
  private final ArrayList<AnAction> customActions = new ArrayList<AnAction>();
  private final ConsoleBuffer myBuffer = new ConsoleBuffer();
  private boolean myUpdateFoldingsEnabled = true;
  private EditorHyperlinkSupport myHyperlinks;

  @TestOnly
  public Editor getEditor() {
    return myEditor;
  }

  public void scrollToEnd() {
    if (myEditor == null) return;
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
  }

  public void foldImmediately() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myFlushAlarm.getActiveRequestCount() > 0) {
      myFlushAlarm.cancelAllRequests();
      myFlushDeferredRunnable.run();
    }

    myFoldingAlarm.cancelAllRequests();

    myPendingFoldRegions.clear();
    final FoldingModel model = myEditor.getFoldingModel();
    model.runBatchFoldingOperation(new Runnable() {
      public void run() {
        for (FoldRegion region : model.getAllFoldRegions()) {
          model.removeFoldRegion(region);
        }
      }
    });
    myFolding.clear();

    updateFoldings(0, myEditor.getDocument().getLineCount() - 1, true);
  }

  static class TokenInfo {
    final ConsoleViewContentType contentType;
    int startOffset;
    int endOffset;
    private final TextAttributes attributes;

    TokenInfo(final ConsoleViewContentType contentType, final int startOffset, final int endOffset) {
      this.contentType = contentType;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      attributes = contentType.getAttributes();
    }

    public int getLength() {
      return endOffset - startOffset;
    }

    @Override
    public String toString() {
      return contentType + "[" + startOffset + ";" + endOffset + "]";
    }

    @Nullable
    public HyperlinkInfo getHyperlinkInfo() {
      return null;
    }
  }

  static class HyperlinkTokenInfo extends TokenInfo {
    private HyperlinkInfo myHyperlinkInfo;

    HyperlinkTokenInfo(final ConsoleViewContentType contentType, final int startOffset, final int endOffset, HyperlinkInfo hyperlinkInfo) {
      super(contentType, startOffset, endOffset);
      myHyperlinkInfo = hyperlinkInfo;
    }

    public HyperlinkInfo getHyperlinkInfo() {
      return myHyperlinkInfo;
    }
  }

  private final Project myProject;

  private boolean myOutputPaused;

  private EditorEx myEditor;

  private final Object LOCK = new Object();

  /**
   * Holds number of symbols managed by the current console.
   * <p/>
   * Total number is assembled as a sum of symbols that are already pushed to the document and number of deferred symbols that
   * are awaiting to be pushed to the document.
   */
  private int myContentSize;

  /**
   * Holds information about lexical division by offsets of the text already pushed to document.
   * <p/>
   * Target offsets are anchored to the document here.
   */
  private ArrayList<TokenInfo> myTokens = new ArrayList<TokenInfo>();

  private final TIntObjectHashMap<ConsoleFolding> myFolding = new TIntObjectHashMap<ConsoleFolding>();

  private String myHelpId;

  private final Alarm myFlushUserInputAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, this);
  private final Alarm myFlushAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  private final MyFlushDeferredRunnable myFlushDeferredRunnable = new MyFlushDeferredRunnable(false);
  private final MyFlushDeferredRunnable myClearRequest = new MyFlushDeferredRunnable(true);

  protected final CompositeFilter myPredefinedMessageFilter;
  protected final CompositeFilter myCustomFilter;

  private final ArrayList<ConsoleInputListener> myConsoleInputListeners = new ArrayList<ConsoleInputListener>();

  private final Alarm myFoldingAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  private final List<FoldRegion> myPendingFoldRegions = new ArrayList<FoldRegion>();

  public void addConsoleUserInputListener(ConsoleInputListener consoleInputListener) {
    myConsoleInputListeners.add(consoleInputListener);
  }

  private FileType myFileType;

  /**
   * Use it for custom highlighting for user text.
   * This will be highlighted as appropriate file to this file type.
   *
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
    this(project, searchScope, viewer, fileType,
         new ConsoleState.NotStartedStated() {
           @Override
           public ConsoleState attachTo(ConsoleViewImpl console, ProcessHandler processHandler) {
             return new ConsoleViewRunningState(console, processHandler, this, true, true);
           }
         });
  }

  protected ConsoleViewImpl(final Project project, GlobalSearchScope searchScope, boolean viewer, FileType fileType,
                            @NotNull final ConsoleState initialState) {
    super(new BorderLayout());
    isViewer = viewer;
    myState = initialState;
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

  public void attachToProcess(final ProcessHandler processHandler) {
    myState = myState.attachTo(this, processHandler);
  }

  public void clear() {
    if (myEditor == null) return;
    synchronized (LOCK) {
      // let's decrease myContentSize by size of deferred output text
      // then in EDT we will clear already flushed output (editor content)
      // end it will induce document changed event which will
      // also decrease myContentSize by flushed output size.
      //
      // P.S: We cannot set myContentSize to '0' here because between this
      // code and alarm clear request (in EDT) my occur print event in non-EDT thread
      // and unfortunately it is a real usecase and happens when switching active test in
      // tests console.
      myContentSize = Math.max(0, myContentSize - myBuffer.getLength());
      myBuffer.clear();
    }
    myFlushAlarm.cancelAllRequests();
    myFlushAlarm.addRequest(myClearRequest, 0, getStateForUpdate());
  }

  public void scrollTo(final int offset) {
    if (myEditor == null) return;
    final Runnable scrollRunnable = new Runnable() {
      public void run() {
        flushDeferredText(false);
        if (myEditor == null) return;
        int moveOffset = offset;
        if (myBuffer.isUseCyclicBuffer() && moveOffset >= myEditor.getDocument().getTextLength()) {
          moveOffset = 0;
        }
        myEditor.getCaretModel().moveToOffset(moveOffset);
        myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    };
    myFlushAlarm.addRequest(scrollRunnable, 0, getStateForUpdate());
  }

  private static void assertIsDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  public void setOutputPaused(final boolean value) {
    myOutputPaused = value;
    if (!value) {
      requestFlushImmediately();
    }
  }

  public boolean isOutputPaused() {
    return myOutputPaused;
  }

  public boolean hasDeferredOutput() {
    synchronized (LOCK) {
      return myBuffer.getLength() > 0;
    }
  }

  public void performWhenNoDeferredOutput(final Runnable runnable) {
    //Q: implement in another way without timer?
    if (!hasDeferredOutput()) {
      runnable.run();
    }
    else {
      mySpareTimeAlarm.addRequest(
        new Runnable() {
          public void run() {
            performWhenNoDeferredOutput(runnable);
          }
        },
        100,
        ModalityState.stateForComponent(this)
      );
    }
  }

  public JComponent getComponent() {
    if (myEditor == null) {
      myEditor = createEditor();
      myHyperlinks = new EditorHyperlinkSupport(myEditor, myProject);
      requestFlushImmediately();
      add(createCenterComponent(), BorderLayout.CENTER);

      myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          if (e.getNewLength() == 0 && e.getOffset() == 0) {
            // string has been removed from the beginning, move tokens down
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
      
      myEditor.getScrollingModel().addVisibleAreaListener(new VisibleAreaListener() {
        @Override
        public void visibleAreaChanged(VisibleAreaEvent e) {
          // There is a possible case that the console text is populated while the console is not shown (e.g. we're debugging and
          // 'Debugger' tab is active while 'Console' is not). It's also possible that newly added text contains long lines that
          // are soft wrapped. We want to update viewport position then when the console becomes visible.
          final Rectangle oldRectangle = e.getOldRectangle();
          final Rectangle newRectangle = e.getNewRectangle();
          if (oldRectangle == null || newRectangle == null) {
            return;
          }

          if (oldRectangle.height <= 0 && newRectangle.height > 0 && myEditor.getSoftWrapModel().isSoftWrappingEnabled()
              && myEditor.getCaretModel().getOffset() == myEditor.getDocument().getTextLength()) {
            EditorUtil.scrollToTheEnd(myEditor);
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


  public void dispose() {
    myState = myState.dispose();
    if (myEditor != null) {
      myFlushAlarm.cancelAllRequests();
      mySpareTimeAlarm.cancelAllRequests();
      disposeEditor();
      synchronized (LOCK) {
        myBuffer.clear();
      }
      myEditor = null;
      myHyperlinks = null;
    }
  }

  protected void disposeEditor() {
    if (!myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
  }

  public void print(String s, final ConsoleViewContentType contentType) {
    printHyperlink(s, contentType, null);
  }

  private void printHyperlink(String s, ConsoleViewContentType contentType, HyperlinkInfo info) {
    synchronized (LOCK) {
      Pair<String, Integer> pair = myBuffer.print(s, contentType, info);
      s = pair.first;
      myContentSize += s.length() - pair.second;

      if (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
        if (contentType == ConsoleViewContentType.USER_INPUT) {
          flushDeferredUserInput();
        }
      }
      if (myFlushAlarm.getActiveRequestCount() == 0 && myEditor != null && !myFlushAlarm.isDisposed()) {
        final boolean shouldFlushNow = myBuffer.isUseCyclicBuffer() && myBuffer.getLength() >= myBuffer.getCyclicBufferSize();
        myFlushAlarm.addRequest(myFlushDeferredRunnable, shouldFlushNow ? 0 : FLUSH_DELAY, getStateForUpdate());
      }
    }
  }

  protected void beforeExternalAddContentToDocument(int length, ConsoleViewContentType contentType) {
    myContentSize += length;
    addToken(length, null, contentType);
  }

  private void addToken(int length, @Nullable HyperlinkInfo info, ConsoleViewContentType contentType) {
    ConsoleUtil.addToken(length, info, contentType, myTokens);
  }

  private ModalityState getStateForUpdate() {
    return myStateForUpdate != null ? myStateForUpdate.compute() : ModalityState.stateForComponent(this);
  }

  private void requestFlushImmediately() {
    if (myEditor != null) {
      myFlushAlarm.addRequest(myFlushDeferredRunnable, 0, getStateForUpdate());
    }
  }

  public int getContentSize() {
    return myContentSize;
  }

  public boolean canPause() {
    return true;
  }

  @TestOnly
  public void flushDeferredText() {
    flushDeferredText(false);
  }

  private void flushDeferredText(boolean clear) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myProject.isDisposed()) {
      return;
    }
    if (clear) {
      final Document document;
      synchronized (LOCK) {
        myHyperlinks.clearHyperlinks();
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


    final String text;
    final Collection<ConsoleViewContentType> contentTypes;
    int deferredTokensSize;
    synchronized (LOCK) {
      if (myOutputPaused) return;
      if (myBuffer.isEmpty()) return;
      if (myEditor == null) return;

      text = myBuffer.getText();

      contentTypes = Collections.unmodifiableCollection(new HashSet<ConsoleViewContentType>(myBuffer.getDeferredTokenTypes()));
      List<TokenInfo> deferredTokens = myBuffer.getDeferredTokens();
      for (TokenInfo deferredToken : deferredTokens) {
        addToken(deferredToken.getLength(), deferredToken.getHyperlinkInfo(), deferredToken.contentType);
      }
      deferredTokensSize = deferredTokens.size();
      myBuffer.clear(false);
    }
    final Document document = myEditor.getDocument();
    final int oldLineCount = document.getLineCount();
    final boolean isAtEndOfDocument = myEditor.getCaretModel().getOffset() == document.getTextLength();
    boolean cycleUsed = myBuffer.isUseCyclicBuffer() && document.getTextLength() + text.length() > myBuffer.getCyclicBufferSize();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        int offset = myEditor.getCaretModel().getOffset();
        boolean preserveCurrentVisualArea = offset < document.getTextLength();
        if (preserveCurrentVisualArea) {
          myEditor.getScrollingModel().accumulateViewportChanges();
        }
        try {
          String[] strings = text.split("\\r");
          for (int i = 0; i < strings.length - 1; i++) {
            document.insertString(document.getTextLength(), strings[i]);
            int lastLine = document.getLineCount() - 1;
            if (lastLine >= 0) {
              document.deleteString(document.getLineStartOffset(lastLine), document.getTextLength());
            }
          }
          document.insertString(document.getTextLength(), strings[strings.length - 1]);
        }
        finally {
          if (preserveCurrentVisualArea) {
            myEditor.getScrollingModel().flushViewportChanges();
          }
        }
        if (!contentTypes.isEmpty()) {
          for (ChangeListener each : myListeners) {
            each.contentAdded(contentTypes);
          }
        }
      }
    }, null, DocCommandGroupId.noneGroupId(document));
    synchronized (LOCK) {
      for (int i = myTokens.size() - 1; i >= 0 && deferredTokensSize > 0; i--, deferredTokensSize--) {
        TokenInfo token = myTokens.get(i);
        final HyperlinkInfo info = token.getHyperlinkInfo();
        if (info != null) {
          myHyperlinks.addHyperlink(token.startOffset, token.endOffset, null, info);
        }
      }
    }
    myPsiDisposedCheck.performCheck();
    final int newLineCount = document.getLineCount();
    if (cycleUsed) {
      final int lineCount = LineTokenizer.calcLineCount(text, true);
      for (Iterator<RangeHighlighter> it = myHyperlinks.getHyperlinks().keySet().iterator(); it.hasNext();) {
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
      EditorUtil.scrollToTheEnd(myEditor);
    }
  }

  private void flushDeferredUserInput() {
    final String textToSend = myBuffer.cutFirstUserInputLine();
    if (textToSend == null) {
      return;
    }
    myFlushUserInputAlarm.addRequest(new Runnable() {
      public void run() {
        if (myState.isRunning()) {
          try {
            // this may block forever, see IDEA-54340
            myState.sendUserInput(textToSend);
          }
          catch (IOException ignored) {
          }
        }
      }
    }, 0);
  }

  public Object getData(final String dataId) {
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      if (myEditor == null) {
        return null;
      }
      final LogicalPosition pos = myEditor.getCaretModel().getLogicalPosition();
      final HyperlinkInfo info = myHyperlinks.getHyperlinkInfoByLineAndCol(pos.line, pos.column);
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
    if (LangDataKeys.CONSOLE_VIEW.is(dataId)) {
      return this;
    }
    return null;
  }

  public void setHelpId(final String helpId) {
    myHelpId = helpId;
  }

  public void setUpdateFoldingsEnabled(boolean updateFoldingsEnabled) {
    myUpdateFoldingsEnabled = updateFoldingsEnabled;
  }

  public void addMessageFilter(final Filter filter) {
    myCustomFilter.addFilter(filter);
  }

  public void printHyperlink(final String hyperlinkText, final HyperlinkInfo info) {
    printHyperlink(hyperlinkText, ConsoleViewContentType.NORMAL_OUTPUT, info);
  }

  private EditorEx createEditor() {
    return ApplicationManager.getApplication().runReadAction(new Computable<EditorEx>() {
      public EditorEx compute() {
        return doCreateEditor();
      }
    });
  }

  private EditorEx doCreateEditor() {
    final EditorEx editor = createRealEditor();

    editor.addEditorMouseListener(new EditorPopupHandler() {
      public void invokePopup(final EditorMouseEvent event) {
        final MouseEvent mouseEvent = event.getMouseEvent();
        popupInvoked(mouseEvent);
      }
    });


    final int bufferSize = myBuffer.isUseCyclicBuffer() ? myBuffer.getCyclicBufferSize() : 0;
    editor.getDocument().setCyclicBufferSize(bufferSize);

    editor.putUserData(CONSOLE_VIEW_IN_EDITOR_VIEW, this);

    return editor;
  }

  protected EditorEx createRealEditor() {
    final EditorEx editor = ConsoleViewUtil.setupConsoleEditor(myProject, true, false);

    editor.getDocument().addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        if (myFileType != null) {
          highlightUserTokens();
        }
      }
    });

    editor.getSettings().setAllowSingleLogicalLineFolding(true); // We want to fold long soft-wrapped command lines
    editor.setHighlighter(createHighlighter());

    if (!isViewer) {
      setEditorUpActions(editor);
    }

    return editor;
  }

  protected MyHighlighter createHighlighter() {
    return new MyHighlighter();
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
            highlighter.dispose();
          }
        }
        HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(0);
        while (!iterator.atEnd()) {
          myEditor.getMarkupModel()
            .addRangeHighlighter(iterator.getStart() + token.startOffset, iterator.getEnd() + token.startOffset, HighlighterLayer.SYNTAX,
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
    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    final Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    action.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), editor.getContentComponent());
  }

  private void popupInvoked(MouseEvent mouseEvent) {
    final ActionManager actionManager = ActionManager.getInstance();
    final HyperlinkInfo info = myHyperlinks.getHyperlinkInfoByPoint(mouseEvent.getPoint());
    ActionGroup group = null;
    if (info instanceof HyperlinkWithPopupMenuInfo) {
      group = ((HyperlinkWithPopupMenuInfo)info).getPopupMenuGroup(mouseEvent);
    }
    if (group == null) {
      group = (ActionGroup)actionManager.getAction(CONSOLE_VIEW_POPUP_MENU);
    }
    final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group);
    menu.getComponent().show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
  }

  private void highlightHyperlinksAndFoldings(final int line1, final int endLine) {
    boolean canHighlightHyperlinks = !myCustomFilter.isEmpty() || !myPredefinedMessageFilter.isEmpty();

    if (!canHighlightHyperlinks && myUpdateFoldingsEnabled) {
      return;
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    if (canHighlightHyperlinks) {
      myHyperlinks.highlightHyperlinks(myCustomFilter, myPredefinedMessageFilter, line1, endLine);
    }
    if (myUpdateFoldingsEnabled) {
      updateFoldings(line1, endLine, true);
    }
  }

  private void updateFoldings(final int line1, final int endLine, boolean immediately) {
    final Document document = myEditor.getDocument();
    final CharSequence chars = document.getCharsSequence();
    final int startLine = Math.max(0, line1);
    final List<FoldRegion> toAdd = new ArrayList<FoldRegion>();
    for (int line = startLine; line <= endLine; line++) {
      addFolding(document, chars, line, toAdd);
    }
    if (!toAdd.isEmpty()) {
      doUpdateFolding(toAdd, immediately);
    }
  }

  private void doUpdateFolding(final List<FoldRegion> toAdd, final boolean immediately) {
    assertIsDispatchThread();
    myPendingFoldRegions.addAll(toAdd);

    myFoldingAlarm.cancelAllRequests();
    final Runnable runnable = new Runnable() {
      public void run() {
        if (myEditor == null || myEditor.isDisposed()) {
          return;
        }

        assertIsDispatchThread();
        final FoldingModel model = myEditor.getFoldingModel();
        final Runnable operation = new Runnable() {
          public void run() {
            assertIsDispatchThread();
            for (FoldRegion region : myPendingFoldRegions) {
              region.setExpanded(false);
              model.addFoldRegion(region);
            }
            myPendingFoldRegions.clear();
          }
        };
        if (immediately) {
          model.runBatchFoldingOperation(operation);
        }
        else {
          model.runBatchFoldingOperationDoNotCollapseCaret(operation);
        }
      }
    };
    if (immediately || myPendingFoldRegions.size() > 100) {
      runnable.run();
    }
    else {
      myFoldingAlarm.addRequest(runnable, 50);
    }
  }

  private void addFolding(Document document, CharSequence chars, int line, List<FoldRegion> toAdd) {
    String commandLinePlaceholder = myCommandLineFolding.getPlaceholder(line);
    if (commandLinePlaceholder != null) {
      FoldRegion region = myEditor.getFoldingModel().createFoldRegion(
        document.getLineStartOffset(line), document.getLineEndOffset(line), commandLinePlaceholder, null, false
      );
      toAdd.add(region);
      return;
    }
    ConsoleFolding current = foldingForLine(EditorHyperlinkSupport.getLineText(document, line, false));
    if (current != null) {
      myFolding.put(line, current);
    }

    final ConsoleFolding prevFolding = myFolding.get(line - 1);
    if (current == null && prevFolding != null) {
      final int lEnd = line - 1;
      int lStart = lEnd;
      while (prevFolding.equals(myFolding.get(lStart - 1))) lStart--;
      if (lStart == lEnd) {
        return;
      }

      for (int i = lStart; i <= lEnd; i++) {
        myFolding.remove(i);
      }

      List<String> toFold = new ArrayList<String>(lEnd - lStart + 1);
      for (int i = lStart; i <= lEnd; i++) {
        toFold.add(EditorHyperlinkSupport.getLineText(document, i, false));
      }

      int oStart = document.getLineStartOffset(lStart);
      if (oStart > 0) oStart--;
      int oEnd = CharArrayUtil.shiftBackward(chars, document.getLineEndOffset(lEnd) - 1, " \t") + 1;

      FoldRegion region =
        myEditor.getFoldingModel().createFoldRegion(oStart, oEnd, prevFolding.getPlaceholderText(toFold), null, false);
      if (region != null) {
        toAdd.add(region);
      }
    }
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


  public static class ClearAllAction extends DumbAwareAction {
    public ClearAllAction() {
      super(ExecutionBundle.message("clear.all.from.console.action.name"));
    }

    @Override
    public void update(AnActionEvent e) {
      final boolean enabled = e.getData(LangDataKeys.CONSOLE_VIEW) != null;
      e.getPresentation().setEnabled(enabled);
      e.getPresentation().setVisible(enabled);
    }

    public void actionPerformed(final AnActionEvent e) {
      final ConsoleView consoleView = e.getData(LangDataKeys.CONSOLE_VIEW);
      if (consoleView != null) {
        consoleView.clear();
      }
    }
  }

  public static class CopyAction extends EditorCopyAction {
    @Override
    protected boolean isEnabled(AnActionEvent e) {
      return super.isEnabled(e) && e.getData(LangDataKeys.CONSOLE_VIEW) != null;
    }
  }

  private class MyHighlighter extends DocumentAdapter implements EditorHighlighter {
    private HighlighterClient myEditor;

    public HighlighterIterator createIterator(final int startOffset) {
      final int startIndex = findTokenInfoIndexByOffset(startOffset);

      return new HighlighterIterator() {
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

    while (low <= high) {
      final int mid = (low + high) / 2;
      final TokenInfo midVal = myTokens.get(mid);
      if (offset < midVal.startOffset) {
        high = mid - 1;
      }
      else if (offset >= midVal.endOffset) {
        low = mid + 1;
      }
      else {
        return mid;
      }
    }
    return myTokens.size();
  }

  private static class MyTypedHandler extends TypedActionHandlerBase {

    private MyTypedHandler(final TypedActionHandler originalAction) {
      super(originalAction);
    }

    public void execute(@NotNull final Editor editor, final char charTyped, @NotNull final DataContext dataContext) {
      final ConsoleViewImpl consoleView = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW);
      if (consoleView == null || !consoleView.myState.isRunning() || consoleView.isViewer) {
        if (myOriginalHandler != null) myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      else {
        final String s = String.valueOf(charTyped);
        SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasSelection()) {
          consoleView.replaceUserText(s, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
        }
        else {
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
        String str = consoleView.myBuffer.getUserInput();
        for (ConsoleInputListener listener : consoleView.myConsoleInputListeners) {
          listener.textEntered(str);
        }
      }
      consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      consoleView.flushDeferredText(false);
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
      catch (Exception e) {
        consoleView.getToolkit().beep();
      }
      if (s == null) return;
      Editor editor = consoleView.myEditor;
      SelectionModel selectionModel = editor.getSelectionModel();
      if (selectionModel.hasSelection()) {
        consoleView.replaceUserText(s, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      }
      else {
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
      }
      else if (editor.getCaretModel().getOffset() > 0) {
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
      }
      else {
        consoleView.deleteUserText(editor.getCaretModel().getOffset(), 1);
      }
    }

    private static EditorActionHandler getDefaultActionHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
    }
  }

  public JComponent getPreferredFocusableComponent() {
    //ensure editor created
    getComponent();
    return myEditor.getContentComponent();
  }


  // navigate up/down in stack trace
  public boolean hasNextOccurence() {
    return calcNextOccurrence(1) != null;
  }

  public boolean hasPreviousOccurence() {
    return calcNextOccurrence(-1) != null;
  }

  public OccurenceInfo goNextOccurence() {
    return calcNextOccurrence(1);
  }

  @Nullable
  protected OccurenceInfo calcNextOccurrence(final int delta) {
    return EditorHyperlinkSupport.getNextOccurrence(myEditor, myHyperlinks.getHyperlinks().keySet(), delta, new Consumer<RangeHighlighter>() {
      @Override
      public void consume(RangeHighlighter next) {
        scrollTo(next.getStartOffset());
        final HyperlinkInfo hyperlinkInfo = myHyperlinks.getHyperlinks().get(next);
        if (hyperlinkInfo != null) {
          hyperlinkInfo.navigate(myProject);
        }
      }
    });
  }

  public OccurenceInfo goPreviousOccurence() {
    return calcNextOccurrence(-1);
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
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    final AnAction prevAction = actionsManager.createPrevOccurenceAction(this);
    prevAction.getTemplatePresentation().setText(getPreviousOccurenceActionName());
    final AnAction nextAction = actionsManager.createNextOccurenceAction(this);
    nextAction.getTemplatePresentation().setText(getNextOccurenceActionName());

    final AnAction switchSoftWrapsAction = new ToggleUseSoftWrapsToolbarAction(SoftWrapAppliancePlaces.CONSOLE) {

      /**
       * There is a possible case that more than console is open and user toggles soft wraps mode at one of them. We want
       * to update another console(s) representation as well when they are switched on after that. Hence, we remember last 
       * used soft wraps mode and perform update if we see that the current value differs from the stored.
       */
      private boolean myLastIsSelected;

      @Override
      protected Editor getEditor(AnActionEvent e) {
        return myEditor;
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        boolean result = super.isSelected(e);
        if (result ^ myLastIsSelected) {
          setSelected(null, result);
        }
        return myLastIsSelected = result;
      }

      @Override
      public void setSelected(AnActionEvent e, final boolean state) {
        super.setSelected(e, state);

        final String placeholder = myCommandLineFolding.getPlaceholder(0);
        if (myEditor == null || (state && placeholder == null)) {
          return;
        }

        final FoldingModel foldingModel = myEditor.getFoldingModel();
        FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
        Runnable foldTask = null;

        final int endFoldRegionOffset = myEditor.getDocument().getLineEndOffset(0);
        Runnable addCollapsedFoldRegionTask = new Runnable() {
          @Override
          public void run() {
            FoldRegion foldRegion = foldingModel.addFoldRegion(0, endFoldRegionOffset, placeholder == null ? "..." : placeholder);
            if (foldRegion != null) {
              foldRegion.setExpanded(false);
            }
          }
        };
        if (foldRegions.length <= 0) {
          if (!state) {
            return;
          }
          foldTask = addCollapsedFoldRegionTask;
        }
        else {
          final FoldRegion foldRegion = foldRegions[0];
          if (foldRegion.getStartOffset() == 0 && foldRegion.getEndOffset() == endFoldRegionOffset) {
            foldTask = new Runnable() {
              @Override
              public void run() {
                foldRegion.setExpanded(!state);
              }
            };
          }
          else if (state) {
            foldTask = addCollapsedFoldRegionTask;
          }
        }

        if (foldTask != null) {
          foldingModel.runBatchFoldingOperation(foldTask);
        }
      }
    };
    final AnAction autoScrollToTheEndAction = new ScrollToTheEndToolbarAction(myEditor);

    //Initializing custom actions
    final AnAction[] consoleActions = new AnAction[4 + customActions.size()];
    consoleActions[0] = prevAction;
    consoleActions[1] = nextAction;
    consoleActions[2] = switchSoftWrapsAction;
    consoleActions[3] = autoScrollToTheEndAction;
    for (int i = 0; i < customActions.size(); ++i) {
      consoleActions[i + 4] = customActions.get(i);
    }
    ConsoleActionsPostProcessor[] postProcessors = Extensions.getExtensions(ConsoleActionsPostProcessor.EP_NAME);
    AnAction[] result = consoleActions;
    if (postProcessors != null) {
      for (ConsoleActionsPostProcessor postProcessor : postProcessors) {
        result = postProcessor.postProcess(this, result);
      }
    }
    return result;
  }

  protected void scrollToTheEnd() {
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
    myEditor.getSelectionModel().removeSelection();
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  public void setEditorEnabled(boolean enabled) {
    myEditor.getContentComponent().setEnabled(enabled);
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
   *
   * @param s      inserted text
   * @param offset relatively to all document text
   */
  private void insertUserText(final String s, int offset) {
    final ConsoleViewImpl consoleView = this;
    final ConsoleBuffer buffer = consoleView.myBuffer;
    final Editor editor = consoleView.myEditor;
    final Document document = editor.getDocument();
    final int startOffset;

    synchronized (consoleView.LOCK) {
      if (consoleView.myTokens.isEmpty()) return;
      final TokenInfo info = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
      if (info.contentType != ConsoleViewContentType.USER_INPUT && !s.contains("\n")) {
        consoleView.print(s, ConsoleViewContentType.USER_INPUT);
        consoleView.flushDeferredText(false);
        editor.getCaretModel().moveToOffset(document.getTextLength());
        editor.getSelectionModel().removeSelection();
        return;
      }
      else if (info.contentType != ConsoleViewContentType.USER_INPUT) {
        insertUserText("temp", offset);
        final TokenInfo newInfo = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
        replaceUserText(s, newInfo.startOffset, newInfo.endOffset);
        return;
      }

      final int deferredOffset = myContentSize - buffer.getLength() - buffer.getUserInputLength();
      if (offset > info.endOffset) {
        startOffset = info.endOffset;
      }
      else {
        startOffset = Math.max(deferredOffset, Math.max(info.startOffset, offset));
      }

      buffer.addUserText(startOffset - deferredOffset, s);

      int charCountToAdd = s.length();
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
   *
   * @param s     text for replace
   * @param start relativly to all document text
   * @param end   relativly to all document text
   */
  private void replaceUserText(final String s, int start, int end) {
    if (start == end) {
      insertUserText(s, start);
      return;
    }
    final ConsoleViewImpl consoleView = this;
    final ConsoleBuffer buffer = consoleView.myBuffer;
    final Editor editor = consoleView.myEditor;
    final Document document = editor.getDocument();
    final int startOffset;
    final int endOffset;

    synchronized (consoleView.LOCK) {
      if (consoleView.myTokens.isEmpty()) return;
      final TokenInfo info = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
      if (info.contentType != ConsoleViewContentType.USER_INPUT) {
        consoleView.print(s, ConsoleViewContentType.USER_INPUT);
        consoleView.flushDeferredText(false);
        editor.getCaretModel().moveToOffset(document.getTextLength());
        editor.getSelectionModel().removeSelection();
        return;
      }
      if (buffer.getUserInputLength() <= 0) return;

      final int deferredOffset = myContentSize - buffer.getLength() - buffer.getUserInputLength();

      startOffset = getStartOffset(start, info, deferredOffset);
      endOffset = getEndOffset(end, info);

      if (startOffset == -1 ||
          endOffset == -1 ||
          endOffset <= startOffset) {
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToOffset(start);
        return;
      }
      int charCountToReplace = s.length() - endOffset + startOffset;

      buffer.replaceUserText(startOffset - deferredOffset, endOffset - deferredOffset, s);

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
   *
   * @param offset relativly to all document text
   * @param length lenght of deleted text
   */
  private void deleteUserText(int offset, int length) {
    ConsoleViewImpl consoleView = this;
    ConsoleBuffer buffer = consoleView.myBuffer;
    final Editor editor = consoleView.myEditor;
    final Document document = editor.getDocument();
    final int startOffset;
    final int endOffset;

    synchronized (consoleView.LOCK) {
      if (consoleView.myTokens.isEmpty()) return;
      final TokenInfo info = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
      if (info.contentType != ConsoleViewContentType.USER_INPUT) return;
      if (myBuffer.getUserInputLength() == 0) return;

      final int deferredOffset = myContentSize - buffer.getLength() - buffer.getUserInputLength();
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

      buffer.removeUserText(startOffset - deferredOffset, endOffset - deferredOffset);
      int charCountToDelete = endOffset - startOffset;

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
    }
    else if (offset < info.startOffset) {
      startOffset = Math.max(info.startOffset, deferredOffset);
    }
    else {
      startOffset = -1;
    }
    return startOffset;
  }

  private static int getEndOffset(int offset, TokenInfo info) {
    int endOffset;
    if (offset > info.endOffset) {
      endOffset = info.endOffset;
    }
    else if (offset <= info.startOffset) {
      endOffset = -1;
    }
    else {
      endOffset = offset;
    }
    return endOffset;
  }

  public boolean isRunning() {
    return myState.isRunning();
  }

  /**
   * Command line used to launch application/test from idea is quite a big most of the time (it's likely that classpath definition
   * takes a lot of space). Hence, it takes many visual lines during representation if soft wraps are enabled.
   * <p/>
   * Our point is to fold such long command line and represent it as a single visual line by default.
   */
  private class CommandLineFolding extends ConsoleFolding {

    /**
     * Checks if target line should be folded and returns its placeholder if the examination succeeds.
     *
     * @param line index of line to check
     * @return placeholder text if given line should be folded; <code>null</code> otherwise
     */
    @Nullable
    public String getPlaceholder(int line) {
      if (myEditor == null || line != 0 || !myEditor.getSettings().isUseSoftWraps()) {
        return null;
      }

      String text = EditorHyperlinkSupport.getLineText(myEditor.getDocument(), line, false);
      if (text.length() < 1000) {
        return null;
      }
      // Don't fold the first line if no soft wraps are used or if the line is not that big.
      if (!myEditor.getSettings().isUseSoftWraps() || text.length() < 1000) {
        return null;
      }
      boolean nonWhiteSpaceFound = false;
      int index = 0;
      for (; index < text.length(); index++) {
        char c = text.charAt(index);
        if (c != ' ' && c != '\t') {
          nonWhiteSpaceFound = true;
          continue;
        }
        if (nonWhiteSpaceFound) {
          break;
        }
      }
      if (index > text.length()) {
        // Don't expect to be here
        return "<...>";
      }
      return text.substring(0, index) + " ...";
    }

    @Override
    public boolean shouldFoldLine(String line) {
      return false;
    }

    @Override
    public String getPlaceholderText(List<String> lines) {
      // Is not expected to be called.
      return "<...>";
    }
  }

  private final class MyFlushDeferredRunnable implements Runnable {
    private final boolean myClear;

    private MyFlushDeferredRunnable(boolean clear) {
      myClear = clear;
    }

    public void run() {
      flushDeferredText(myClear);
    }
  }
}

