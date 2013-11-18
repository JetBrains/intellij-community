/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.icons.AllIcons;
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
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.SystemProperties;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private static final int DEFAULT_FLUSH_DELAY = SystemProperties.getIntProperty("console.flush.delay.ms", 200);

  public static final Key<ConsoleViewImpl> CONSOLE_VIEW_IN_EDITOR_VIEW = Key.create("CONSOLE_VIEW_IN_EDITOR_VIEW");

  static {
    final EditorActionManager actionManager = EditorActionManager.getInstance();
    final TypedAction typedAction = actionManager.getTypedAction();
    typedAction.setupHandler(new MyTypedHandler(typedAction.getHandler()));
  }


  private final CommandLineFolding myCommandLineFolding = new CommandLineFolding();

  private final DisposedPsiManagerCheck myPsiDisposedCheck;
  private final boolean myIsViewer;

  private ConsoleState myState;

  private final Alarm mySpareTimeAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  @Nullable
  private final Alarm myHeavyAlarm;
  private       int   myHeavyUpdateTicket;

  private final Collection<ChangeListener> myListeners = new CopyOnWriteArraySet<ChangeListener>();
  private final List<AnAction> customActions = new ArrayList<AnAction>();
  private final ConsoleBuffer myBuffer = new ConsoleBuffer();
  private boolean myUpdateFoldingsEnabled = true;
  private EditorHyperlinkSupport myHyperlinks;
  private MyDiffContainer myJLayeredPane;
  private JPanel myMainPanel;
  private final Runnable myFinishProgress;
  private boolean myAllowHeavyFilters = false;
  private static final int myFlushDelay = DEFAULT_FLUSH_DELAY;

  private boolean myInSpareTimeUpdate;
  private boolean myInDocumentUpdate;

  public Editor getEditor() {
    return myEditor;
  }

  public EditorHyperlinkSupport getHyperlinks() {
    return myHyperlinks;
  }

  public void scrollToEnd() {
    if (myEditor == null) return;
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
  }

  public void foldImmediately() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myFlushAlarm.isEmpty()) {
      cancelAllFlushRequests();
      new MyFlushRunnable().run();
    }

    myFoldingAlarm.cancelAllRequests();

    myPendingFoldRegions.clear();
    final FoldingModel model = myEditor.getFoldingModel();
    model.runBatchFoldingOperation(new Runnable() {
      @Override
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
    private final HyperlinkInfo myHyperlinkInfo;

    HyperlinkTokenInfo(final ConsoleViewContentType contentType, final int startOffset, final int endOffset, HyperlinkInfo hyperlinkInfo) {
      super(contentType, startOffset, endOffset);
      myHyperlinkInfo = hyperlinkInfo;
    }

    @Override
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
  private final List<TokenInfo> myTokens = new ArrayList<TokenInfo>();

  private final TIntObjectHashMap<ConsoleFolding> myFolding = new TIntObjectHashMap<ConsoleFolding>();

  private String myHelpId;

  private final Alarm myFlushUserInputAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  private final Alarm myFlushAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  private final Set<MyFlushRunnable> myCurrentRequests = new HashSet<MyFlushRunnable>();

  protected final CompositeFilter myPredefinedMessageFilter;
  protected final CompositeFilter myCustomFilter;

  @Nullable private final InputFilter myInputMessageFilter;

  private final Alarm myFoldingAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  private final List<FoldRegion> myPendingFoldRegions = new ArrayList<FoldRegion>();

  public ConsoleViewImpl(final Project project, boolean viewer) {
    this(project, GlobalSearchScope.allScope(project), viewer, true);
  }

  public ConsoleViewImpl(@NotNull final Project project,
                         @NotNull GlobalSearchScope searchScope,
                         boolean viewer,
                         boolean usePredefinedMessageFilter) {
    this(project, searchScope, viewer,
         new ConsoleState.NotStartedStated() {
           @Override
           public ConsoleState attachTo(ConsoleViewImpl console, ProcessHandler processHandler) {
             return new ConsoleViewRunningState(console, processHandler, this, true, true);
           }
         },
         usePredefinedMessageFilter);
  }

  protected ConsoleViewImpl(@NotNull final Project project,
                            @NotNull GlobalSearchScope searchScope,
                            boolean viewer,
                            @NotNull final ConsoleState initialState,
                            boolean usePredefinedMessageFilter)
  {
    super(new BorderLayout());
    myIsViewer = viewer;
    myState = initialState;
    myPsiDisposedCheck = new DisposedPsiManagerCheck(project);
    myProject = project;

    myCustomFilter = new CompositeFilter(project);
    myPredefinedMessageFilter = new CompositeFilter(project);
    if (usePredefinedMessageFilter) {
      for (ConsoleFilterProvider eachProvider : Extensions.getExtensions(ConsoleFilterProvider.FILTER_PROVIDERS)) {
        Filter[] filters = eachProvider instanceof ConsoleFilterProviderEx
                           ? ((ConsoleFilterProviderEx)eachProvider).getDefaultFilters(project, searchScope)
                           : eachProvider.getDefaultFilters(project);
        for (Filter filter : filters) {
          myPredefinedMessageFilter.addFilter(filter);
        }
      }
    }
    myHeavyUpdateTicket = 0;
    myHeavyAlarm = myPredefinedMessageFilter.isAnyHeavy() ? new Alarm(Alarm.ThreadToUse.SHARED_THREAD, this) : null;

    ConsoleInputFilterProvider[] inputFilters = Extensions.getExtensions(ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS);
    if (inputFilters.length > 0) {
      CompositeInputFilter compositeInputFilter = new CompositeInputFilter(project);
      myInputMessageFilter = compositeInputFilter;
      for (ConsoleInputFilterProvider eachProvider : inputFilters) {
        InputFilter[] filters = eachProvider.getDefaultFilters(project);
        for (final InputFilter filter : filters) {
          compositeInputFilter.addFilter(new InputFilter() {
            boolean isBroken;

            @Nullable
            @Override
            public List<Pair<String, ConsoleViewContentType>> applyFilter(String text, ConsoleViewContentType contentType) {
              if (!isBroken) {
                try {
                  return filter.applyFilter(text, contentType);
                }
                catch (Throwable e) {
                  isBroken = true;
                  LOG.error(e);
                }
              }
              return null;
            }
          });
        }
      }
    }
    else {
      myInputMessageFilter = null;
    }

    Disposer.register(project, this);
    myFinishProgress = new Runnable() {
      @Override
      public void run() {
        myJLayeredPane.finishUpdating();
      }
    };
  }

  @Override
  public void attachToProcess(final ProcessHandler processHandler) {
    myState = myState.attachTo(this, processHandler);
  }

  @Override
  public void clear() {
    if (myEditor == null) return;
    synchronized (LOCK) {
      // real document content will be cleared on next flush;
      myContentSize = 0;
      myBuffer.clear();
      myFolding.clear();

      final EditorHyperlinkSupport hyperlinks = myHyperlinks;
      if (hyperlinks != null) {
        hyperlinks.clearHyperlinks();
      }
    }
    if (myFlushAlarm.isDisposed()) return;
    cancelAllFlushRequests();
    addFlushRequest(new MyClearRunnable());
    cancelHeavyAlarm();
  }

  @Override
  public void scrollTo(final int offset) {
    if (myEditor == null || myFlushAlarm.isDisposed()) return;
    class ScrollRunnable extends MyFlushRunnable {
      private final int myOffset = offset;

      @Override
      public void doRun() {
        flushDeferredText();
        if (myEditor == null) return;
        int moveOffset = Math.min(offset, myEditor.getDocument().getTextLength());
        if (myBuffer.isUseCyclicBuffer() && moveOffset >= myEditor.getDocument().getTextLength()) {
          moveOffset = 0;
        }
        myEditor.getCaretModel().moveToOffset(moveOffset);
        myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }

      @Override
      public boolean equals(Object o) {
        return super.equals(o) && myOffset == ((ScrollRunnable)o).myOffset;
      }
    }
    addFlushRequest(new ScrollRunnable());
  }

  public void requestScrollingToEnd() {
    if (myEditor == null || myFlushAlarm.isDisposed()) return;
    final MyFlushRunnable scrollRunnable = new MyFlushRunnable() {
      @Override
      public void doRun() {
        flushDeferredText();
        if (myEditor == null || myFlushAlarm.isDisposed()) return;
        myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
        myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    };
    addFlushRequest(scrollRunnable);
  }

  private void addFlushRequest(MyFlushRunnable scrollRunnable) {
    addFlushRequest(scrollRunnable, 0);
  }

  private void addFlushRequest(MyFlushRunnable flushRunnable, final int millis) {
    synchronized (myCurrentRequests) {
      if (!myFlushAlarm.isDisposed() && myCurrentRequests.add(flushRunnable)) {
        myFlushAlarm.addRequest(flushRunnable, millis, getStateForUpdate());
      }
    }
  }


  private static void assertIsDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  @Override
  public void setOutputPaused(final boolean value) {
    myOutputPaused = value;
    if (!value) {
      requestFlushImmediately();
    }
  }

  @Override
  public boolean isOutputPaused() {
    return myOutputPaused;
  }

  @Override
  public boolean hasDeferredOutput() {
    synchronized (LOCK) {
      return myBuffer.getLength() > 0;
    }
  }

  @Override
  public void performWhenNoDeferredOutput(final Runnable runnable) {
    //Q: implement in another way without timer?
    if (!hasDeferredOutput()) {
      runnable.run();
    }
    else {
      if (mySpareTimeAlarm.isDisposed()) return;
      mySpareTimeAlarm.addRequest(
        new Runnable() {
          @Override
          public void run() {
            performWhenNoDeferredOutput(runnable);
          }
        },
        100,
        ModalityState.stateForComponent(myJLayeredPane)
      );
    }
  }

  @Override
  public JComponent getComponent() {
    if (myMainPanel == null) {
      myMainPanel = new JPanel(new BorderLayout());
      myJLayeredPane = new MyDiffContainer(myMainPanel, myPredefinedMessageFilter.getUpdateMessage());
      Disposer.register(this, myJLayeredPane);
      add(myJLayeredPane, BorderLayout.CENTER);
    }

    if (myEditor == null) {
      myEditor = createEditor();
      myEditor.getScrollPane().setBorder(null);
      myHyperlinks = new EditorHyperlinkSupport(myEditor, myProject);
      requestFlushImmediately();
      myMainPanel.add(createCenterComponent(), BorderLayout.CENTER);
      myEditor.getScrollingModel().addVisibleAreaListener(new VisibleAreaListener() {
        @Override
        public void visibleAreaChanged(VisibleAreaEvent e) {
          // There is a possible case that the console text is populated while the console is not shown (e.g. we're debugging and
          // 'Debugger' tab is active while 'Console' is not). It's also possible that newly added text contains long lines that
          // are soft wrapped. We want to update viewport position then when the console becomes visible.
          final Rectangle oldRectangle = e.getOldRectangle();
          final Rectangle newRectangle = e.getNewRectangle();
          if (oldRectangle == null) {
            return;
          }

          Editor myEditor = e.getEditor();
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

  @Override
  public void dispose() {
    myState = myState.dispose();
    if (myEditor != null) {
      cancelAllFlushRequests();
      mySpareTimeAlarm.cancelAllRequests();
      disposeEditor();
      synchronized (LOCK) {
        myBuffer.clear();
      }
      myEditor = null;
      myHyperlinks = null;
    }
    }

  private void cancelAllFlushRequests() {
    synchronized (myCurrentRequests) {
      for (MyFlushRunnable request : myCurrentRequests) {
        request.invalidate();
      }
      myCurrentRequests.clear();
      myFlushAlarm.cancelAllRequests();
    }
  }

  protected void disposeEditor() {
    if (!myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
  }

  @Override
  public void print(String s, final ConsoleViewContentType contentType) {
    if (myInputMessageFilter == null) {
      printHyperlink(s, contentType, null);
      return;
    }

    List<Pair<String, ConsoleViewContentType>> result = myInputMessageFilter.applyFilter(s, contentType);
    if (result == null) {
      printHyperlink(s, contentType, null);
    }
    else {
      for (Pair<String, ConsoleViewContentType> pair : result) {
        if (pair.first != null) {
          printHyperlink(pair.first, pair.second == null ? contentType : pair.second, null);
        }
      }
    }
  }

  private void printHyperlink(@NotNull String s, @NotNull ConsoleViewContentType contentType, @Nullable HyperlinkInfo info) {
    synchronized (LOCK) {
      Pair<String, Integer> pair = myBuffer.print(s, contentType, info);
      s = pair.first;
      myContentSize += s.length() - pair.second;

      if (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
        if (contentType == ConsoleViewContentType.USER_INPUT) {
          flushDeferredUserInput();
        }
      }
      if (myEditor != null && !myFlushAlarm.isDisposed()) {
        final boolean shouldFlushNow = myBuffer.isUseCyclicBuffer() && myBuffer.getLength() >= myBuffer.getCyclicBufferSize();
        addFlushRequest(new MyFlushRunnable(), shouldFlushNow ? 0 : myFlushDelay);
      }
    }
  }

  private void addToken(int length, @Nullable HyperlinkInfo info, ConsoleViewContentType contentType) {
    ConsoleUtil.addToken(length, info, contentType, myTokens);
  }

  private static ModalityState getStateForUpdate() {
    return null;//myStateForUpdate != null ? myStateForUpdate.compute() : ModalityState.stateForComponent(this);
  }

  private void requestFlushImmediately() {
    if (myEditor != null && !myFlushAlarm.isDisposed()) {
      addFlushRequest(new MyFlushRunnable());
    }
  }

  @Override
  public int getContentSize() {
    synchronized (LOCK) {
      return myContentSize;
    }
  }

  @Override
  public boolean canPause() {
    return true;
  }

  public void flushDeferredText() {
    flushDeferredText(false);
  }

  private void flushDeferredText(boolean clear) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myProject.isDisposed()) {
      return;
    }
    EditorEx editor = myEditor;
    if (editor == null) {
      //already disposed
      return;
    }
    if (clear) {
      final DocumentEx document;
      synchronized (LOCK) {
        myHyperlinks.clearHyperlinks();
        myTokens.clear();
        editor.getMarkupModel().removeAllHighlighters();
        document = editor.getDocument();
        myFoldingAlarm.cancelAllRequests();
        cancelHeavyAlarm();
      }
      final int documentTextLength = document.getTextLength();
      if (documentTextLength > 0) {
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          @Override
          public void run() {
            document.setInBulkUpdate(true);
            try {
              myInDocumentUpdate = true;
              document.deleteString(0, documentTextLength);
            }
            finally {
              document.setInBulkUpdate(false);
              myInDocumentUpdate = false;
            }
          }
        }, null, DocCommandGroupId.noneGroupId(document));
      }
    }


    final String text;
    final Collection<ConsoleViewContentType> contentTypes;
    int deferredTokensSize;
    synchronized (LOCK) {
      if (myOutputPaused) return;
      if (myBuffer.isEmpty()) return;

      text = myBuffer.getText();

      contentTypes = Collections.unmodifiableCollection(new HashSet<ConsoleViewContentType>(myBuffer.getDeferredTokenTypes()));
      List<TokenInfo> deferredTokens = myBuffer.getDeferredTokens();
      for (TokenInfo deferredToken : deferredTokens) {
        addToken(deferredToken.getLength(), deferredToken.getHyperlinkInfo(), deferredToken.contentType);
      }
      deferredTokensSize = deferredTokens.size();
      myBuffer.clear(false);
      cancelHeavyAlarm();
    }
    final Document document = myEditor.getDocument();
    final int oldLineCount = document.getLineCount();
    final boolean isAtEndOfDocument = myEditor.getCaretModel().getOffset() == document.getTextLength();
    boolean cycleUsed = myBuffer.isUseCyclicBuffer() && document.getTextLength() + text.length() > myBuffer.getCyclicBufferSize();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        int offset = myEditor.getCaretModel().getOffset();
        boolean preserveCurrentVisualArea = offset < document.getTextLength();
        if (preserveCurrentVisualArea) {
          myEditor.getScrollingModel().accumulateViewportChanges();
        }
        try {
          myInDocumentUpdate = true;
          String[] strings = text.split("\\r");
          for (int i = 0; i < strings.length - 1; i++) {
            document.insertString(document.getTextLength(), strings[i]);
            int lastLine = document.getLineCount() - 1;
            if (lastLine >= 0) {
              ConsoleUtil.updateTokensOnTextRemoval(myTokens, document.getTextLength(), document.getTextLength() + 1);
              document.deleteString(document.getLineStartOffset(lastLine), document.getTextLength());
            }
          }
          if (strings.length > 0) {
            document.insertString(document.getTextLength(), strings[strings.length - 1]);
            myContentSize -= strings.length - 1;
          }
        }
        finally {
          myInDocumentUpdate = false;
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
      clearHyperlinkAndFoldings();
      if (!myInSpareTimeUpdate) {
        myInSpareTimeUpdate = true;
        final EditorNotificationPanel comp = new EditorNotificationPanel() {
          {
            myLabel.setIcon(AllIcons.General.ExclMark);
            myLabel.setText("Too much output to process");
          }
        };
        add(comp, BorderLayout.NORTH);
        performWhenNoDeferredOutput(new Runnable() {
          @Override
          public void run() {
            try {
              myHyperlinks.clearHyperlinks();
              clearHyperlinkAndFoldings();
              highlightHyperlinksAndFoldings(0, document.getLineCount() - 1);
            }
            finally {
              myInSpareTimeUpdate = false;
              remove(comp);
            }
          }
        });
      }
    }
    else if (oldLineCount < newLineCount) {
      highlightHyperlinksAndFoldings(oldLineCount - 1, newLineCount - 1);
    }

    if (isAtEndOfDocument) {
      EditorUtil.scrollToTheEnd(myEditor);
    }
  }

  private void clearHyperlinkAndFoldings() {
    for (Iterator<RangeHighlighter> it = myHyperlinks.getHyperlinks().keySet().iterator(); it.hasNext();) {
      if (!it.next().isValid()) {
        it.remove();
      }
    }

    myPendingFoldRegions.clear();
    myFolding.clear();
    myFoldingAlarm.cancelAllRequests();

    cancelHeavyAlarm();
  }

  private void cancelHeavyAlarm() {
    if (myHeavyAlarm != null && !myHeavyAlarm.isDisposed()) {
      myHeavyAlarm.cancelAllRequests();
      ++myHeavyUpdateTicket;
    }
  }

  private void flushDeferredUserInput() {
    final String textToSend = myBuffer.cutFirstUserInputLine();
    if (textToSend == null) {
      return;
    }
    myFlushUserInputAlarm.addRequest(new Runnable() {
      @Override
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

  @Override
  public Object getData(final String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
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

    if (CommonDataKeys.EDITOR.is(dataId)) {
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

  @Override
  public void setHelpId(final String helpId) {
    myHelpId = helpId;
  }

  public void setUpdateFoldingsEnabled(boolean updateFoldingsEnabled) {
    myUpdateFoldingsEnabled = updateFoldingsEnabled;
  }

  @Override
  public void addMessageFilter(final Filter filter) {
    myCustomFilter.addFilter(filter);
  }

  @Override
  public void printHyperlink(final String hyperlinkText, final HyperlinkInfo info) {
    printHyperlink(hyperlinkText, ConsoleViewContentType.NORMAL_OUTPUT, info);
  }

  private EditorEx createEditor() {
    return ApplicationManager.getApplication().runReadAction(new Computable<EditorEx>() {
      @Override
      public EditorEx compute() {
        final EditorEx editor = createRealEditor();

        editor.addEditorMouseListener(new EditorPopupHandler() {
          @Override
          public void invokePopup(final EditorMouseEvent event) {
            popupInvoked(event.getMouseEvent());
          }
        });
        editor.getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void beforeDocumentChange(DocumentEvent event) {
          }

          @Override
          public void documentChanged(DocumentEvent event) {
            onDocumentChanged(event);
          }
        }, ConsoleViewImpl.this);

        int bufferSize = myBuffer.isUseCyclicBuffer() ? myBuffer.getCyclicBufferSize() : 0;
        editor.getDocument().setCyclicBufferSize(bufferSize);

        editor.putUserData(CONSOLE_VIEW_IN_EDITOR_VIEW, ConsoleViewImpl.this);

        editor.getSettings().setAllowSingleLogicalLineFolding(true); // We want to fold long soft-wrapped command lines
        editor.setHighlighter(createHighlighter());

        if (!myIsViewer) {
          registerConsoleEditorActions(editor);
        }
        return editor;
      }
    });
  }

  private void onDocumentChanged(DocumentEvent event) {
    if (event.getNewLength() == 0) {
      // string has been removed, adjust token ranges
      synchronized (LOCK) {
        ConsoleUtil.updateTokensOnTextRemoval(myTokens, event.getOffset(), event.getOffset() + event.getOldLength());
        int toRemoveLen = event.getOldLength();
        myContentSize -= Math.min(myContentSize, toRemoveLen);
      }
    }
    else if (!myInDocumentUpdate) {
      int newFragmentLength = event.getNewFragment().length();
      // track external appends
      if (event.getOldFragment().length() == 0 && newFragmentLength > 0) {
        synchronized (LOCK) {
          myContentSize += newFragmentLength;
          addToken(newFragmentLength, null, ConsoleViewContentType.NORMAL_OUTPUT);
        }
      }
      else {
        LOG.warn("unhandled external change: " + event);
      }
    }
  }

  protected EditorEx createRealEditor() {
    return ConsoleViewUtil.setupConsoleEditor(myProject, true, false);
  }

  protected MyHighlighter createHighlighter() {
    return new MyHighlighter();
  }

  private static void registerConsoleEditorActions(Editor editor) {
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
    final HyperlinkInfo info = myHyperlinks != null ? myHyperlinks.getHyperlinkInfoByPoint(mouseEvent.getPoint()) : null;
    ActionGroup group = null;
    if (info instanceof HyperlinkWithPopupMenuInfo) {
      group = ((HyperlinkWithPopupMenuInfo)info).getPopupMenuGroup(mouseEvent);
    }
    if (group == null) {
      group = (ActionGroup)actionManager.getAction(CONSOLE_VIEW_POPUP_MENU);
    }
    final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group);
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

    if (myAllowHeavyFilters && myPredefinedMessageFilter.isAnyHeavy() && myPredefinedMessageFilter.shouldRunHeavy()) {
      runHeavyFilters(line1, endLine);
    }
    if (myUpdateFoldingsEnabled) {
      updateFoldings(line1, endLine, true);
    }
  }

  private void runHeavyFilters(int line1, int endLine) {
    final int startLine = Math.max(0, line1);

    final Document document = myEditor.getDocument();
    final int startOffset = document.getLineStartOffset(startLine);
    String text = document.getText(new TextRange(startOffset, document.getLineEndOffset(endLine)));
    final Document documentCopy = new DocumentImpl(text,true);
    documentCopy.setReadOnly(true);

    myJLayeredPane.startUpdating();
    final int currentValue = myHeavyUpdateTicket;
    assert myHeavyAlarm != null;
    myHeavyAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (! myPredefinedMessageFilter.shouldRunHeavy()) return;
        try {
          myPredefinedMessageFilter.applyHeavyFilter(documentCopy, startOffset, startLine, new Consumer<FilterMixin.AdditionalHighlight>() {
            @Override
            public void consume(final FilterMixin.AdditionalHighlight additionalHighlight) {
              if (myFlushAlarm.isDisposed()) return;
              addFlushRequest(new MyFlushRunnable() {
                @Override
                public void doRun() {
                  if (myHeavyUpdateTicket != currentValue) return;
                  myHyperlinks.adjustHighlighters(Collections.singletonList(additionalHighlight));
                }

                @Override
                public boolean equals(Object o) {
                  return this == o && super.equals(o);
                }
              });
            }
          });
        }
        finally {
          if (myHeavyAlarm.isEmpty()) {
            SwingUtilities.invokeLater(myFinishProgress);
          }
        }
      }
    }, 0);
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
      @Override
      public void run() {
        if (myEditor == null || myEditor.isDisposed()) {
          return;
        }

        assertIsDispatchThread();
        final FoldingModel model = myEditor.getFoldingModel();
        final Runnable operation = new Runnable() {
          @Override
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

      String placeholder = prevFolding.getPlaceholderText(toFold);
      FoldRegion region = placeholder == null ? null : myEditor.getFoldingModel().createFoldRegion(oStart, oEnd, placeholder, null, false);
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
      super(ExecutionBundle.message("clear.all.from.console.action.name"), "Clear the contents of the console", AllIcons.Actions.GC);
    }

    @Override
    public void update(AnActionEvent e) {
      boolean enabled = e.getData(LangDataKeys.CONSOLE_VIEW) != null;
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null && editor.getDocument().getTextLength() == 0) {
        enabled = false;
      }
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final ConsoleView consoleView = e.getData(LangDataKeys.CONSOLE_VIEW);
      if (consoleView != null) {
        consoleView.clear();
      }
    }
  }

  private class MyHighlighter extends DocumentAdapter implements EditorHighlighter {
    private HighlighterClient myEditor;

    @Override
    public HighlighterIterator createIterator(final int startOffset) {
      final int startIndex = ConsoleUtil.findTokenInfoIndexByOffset(myTokens, startOffset);

      return new HighlighterIterator() {
        private int myIndex = startIndex;

        @Override
        public TextAttributes getTextAttributes() {
          return getTokenInfo() == null ? null : getTokenInfo().attributes;
        }

        @Override
        public int getStart() {
          return getTokenInfo() == null ? 0 : getTokenInfo().startOffset;
        }

        @Override
        public int getEnd() {
          return getTokenInfo() == null ? 0 : getTokenInfo().endOffset;
        }

        @Override
        public IElementType getTokenType() {
          return null;
        }

        @Override
        public void advance() {
          myIndex++;
        }

        @Override
        public void retreat() {
          myIndex--;
        }

        @Override
        public boolean atEnd() {
          return myIndex < 0 || myIndex >= myTokens.size();
        }

        @Override
        public Document getDocument() {
          return myEditor.getDocument();
        }

        private TokenInfo getTokenInfo() {
          return myTokens.get(myIndex);
        }
      };
    }

    @Override
    public void setText(final CharSequence text) {
    }

    @Override
    public void setEditor(final HighlighterClient editor) {
      LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
      myEditor = editor;
    }

    @Override
    public void setColorScheme(EditorColorsScheme scheme) {
    }
  }

  private static class MyTypedHandler extends TypedActionHandlerBase {

    private MyTypedHandler(final TypedActionHandler originalAction) {
      super(originalAction);
    }

    @Override
    public void execute(@NotNull final Editor editor, final char charTyped, @NotNull final DataContext dataContext) {
      final ConsoleViewImpl consoleView = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW);
      if (consoleView == null || !consoleView.myState.isRunning() || consoleView.myIsViewer) {
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
    @Override
    public void actionPerformed(final AnActionEvent e) {
      final DataContext context = e.getDataContext();
      final ConsoleViewImpl console = getRunningConsole(context);
      execute(console, context);
    }

    protected abstract void execute(ConsoleViewImpl console, final DataContext context);

    @Override
    public void update(final AnActionEvent e) {
      final ConsoleViewImpl console = getRunningConsole(e.getDataContext());
      e.getPresentation().setEnabled(console != null);
    }

    @Nullable
    private static ConsoleViewImpl getRunningConsole(final DataContext context) {
      final Editor editor = CommonDataKeys.EDITOR.getData(context);
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
    @Override
    public void execute(final ConsoleViewImpl consoleView, final DataContext context) {
      consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      consoleView.flushDeferredText();
      final Editor editor = consoleView.myEditor;
      editor.getCaretModel().moveToOffset(editor.getDocument().getTextLength());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  private static class PasteHandler extends ConsoleAction {
    @Override
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
      ApplicationManager.getApplication().assertIsDispatchThread();
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
    @Override
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

      ApplicationManager.getApplication().assertIsDispatchThread();

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
    @Override
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

      ApplicationManager.getApplication().assertIsDispatchThread();
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

  @Override
  public JComponent getPreferredFocusableComponent() {
    //ensure editor created
    getComponent();
    return myEditor.getContentComponent();
  }


  // navigate up/down in stack trace
  @Override
  public boolean hasNextOccurence() {
    return calcNextOccurrence(1) != null;
  }

  @Override
  public boolean hasPreviousOccurence() {
    return calcNextOccurrence(-1) != null;
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return calcNextOccurrence(1);
  }

  @Nullable
  protected OccurenceInfo calcNextOccurrence(final int delta) {
    final EditorHyperlinkSupport hyperlinks = myHyperlinks;
    if (hyperlinks == null) {
      return null;
    }

    return EditorHyperlinkSupport.getNextOccurrence(myEditor, hyperlinks.getHyperlinks().keySet(), delta, new Consumer<RangeHighlighter>() {
      @Override
      public void consume(RangeHighlighter next) {
        scrollTo(next.getStartOffset());
        final HyperlinkInfo hyperlinkInfo = hyperlinks.getHyperlinks().get(next);
        if (hyperlinkInfo != null) {
          hyperlinkInfo.navigate(myProject);
        }
      }
    });
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return calcNextOccurrence(-1);
  }

  @Override
  public String getNextOccurenceActionName() {
    return ExecutionBundle.message("down.the.stack.trace");
  }

  @Override
  public String getPreviousOccurenceActionName() {
    return ExecutionBundle.message("up.the.stack.trace");
  }

  public void addCustomConsoleAction(@NotNull AnAction action) {
    customActions.add(action);
  }

  @Override
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
        if (myEditor == null || state && placeholder == null) {
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
    final AnAction[] consoleActions = new AnAction[6 + customActions.size()];
    consoleActions[0] = prevAction;
    consoleActions[1] = nextAction;
    consoleActions[2] = switchSoftWrapsAction;
    consoleActions[3] = autoScrollToTheEndAction;
    consoleActions[4] = ActionManager.getInstance().getAction("Print");
    consoleActions[5] = new ClearAllAction();
    //consoleActions[4] = new ShowRecentlyChanged();
    for (int i = 0; i < customActions.size(); ++i) {
      consoleActions[i + 6] = customActions.get(i);
    }
    ConsoleActionsPostProcessor[] postProcessors = Extensions.getExtensions(ConsoleActionsPostProcessor.EP_NAME);
    AnAction[] result = consoleActions;
    for (ConsoleActionsPostProcessor postProcessor : postProcessors) {
      result = postProcessor.postProcess(this, result);
    }
    return result;
  }

  @Override
  public void allowHeavyFilters() {
    myAllowHeavyFilters = true;
  }

  @Override
  public void addChangeListener(@NotNull final ChangeListener listener, @NotNull final Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    final ConsoleViewImpl consoleView = this;
    final ConsoleBuffer buffer = consoleView.myBuffer;
    final Editor editor = consoleView.myEditor;
    final Document document = editor.getDocument();
    final int startOffset;

    String textToUse = StringUtil.convertLineSeparators(s);
    synchronized (consoleView.LOCK) {
      if (consoleView.myTokens.isEmpty()) return;
      final TokenInfo info = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
      if (info.contentType != ConsoleViewContentType.USER_INPUT && !textToUse.contains("\n")) {
        consoleView.print(textToUse, ConsoleViewContentType.USER_INPUT);
        consoleView.flushDeferredText();
        editor.getCaretModel().moveToOffset(document.getTextLength());
        editor.getSelectionModel().removeSelection();
        return;
      }
      if (info.contentType != ConsoleViewContentType.USER_INPUT) {
        insertUserText("temp", offset);
        final TokenInfo newInfo = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
        replaceUserText(textToUse, newInfo.startOffset, newInfo.endOffset);
        return;
      }

      final int deferredOffset = myContentSize - buffer.getLength() - buffer.getUserInputLength();
      if (offset > info.endOffset) {
        startOffset = info.endOffset;
      }
      else {
        startOffset = Math.max(deferredOffset, Math.max(info.startOffset, offset));
      }

      buffer.addUserText(startOffset - deferredOffset, textToUse);

      int charCountToAdd = textToUse.length();
      info.endOffset += charCountToAdd;
      consoleView.myContentSize += charCountToAdd;
    }

    try {
      myInDocumentUpdate = true;
      document.insertString(startOffset, textToUse);
    }
    finally {
      myInDocumentUpdate = false;
    }
    // Math.max is needed when cyclic buffer is used
    editor.getCaretModel().moveToOffset(Math.min(startOffset + textToUse.length(), document.getTextLength()));
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  /**
   * replace text
   *
   * @param s     text for replace
   * @param start relatively to all document text
   * @param end   relatively to all document text
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
        consoleView.flushDeferredText();
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

    try {
      myInDocumentUpdate = true;
      document.replaceString(startOffset, endOffset, s);
    }
    finally {
      myInDocumentUpdate = false;
    }
    editor.getCaretModel().moveToOffset(startOffset + s.length());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  /**
   * delete text
   *
   * @param offset relatively to all document text
   * @param length length of deleted text
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
    }

    try {
      myInDocumentUpdate = true;
      document.deleteString(startOffset, endOffset);
    }
    finally {
      myInDocumentUpdate = false;
    }
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

      String text = EditorHyperlinkSupport.getLineText(myEditor.getDocument(), 0, false);
      if (text.length() < 1000) {
        return null;
      }
      // Don't fold the first line if no soft wraps are used or if the line is not that big.
      if (!myEditor.getSettings().isUseSoftWraps()) {
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
      assert index <= text.length();
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

  private class MyFlushRunnable implements Runnable {
    private volatile boolean myValid = true;
    @Override
    public final void run() {
      synchronized (myCurrentRequests) {
        myCurrentRequests.remove(this);
      }
      if (myValid) {
        doRun();
      }
    }

    protected void doRun() {
      flushDeferredText();
    }

    public void invalidate() {
      myValid = false;
    }

    public boolean isValid() {
      return myValid;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyFlushRunnable runnable = (MyFlushRunnable)o;

      return myValid == runnable.myValid;
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  private final class MyClearRunnable extends MyFlushRunnable {
    @Override
    public void doRun() {
      flushDeferredText(true);
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }
}

