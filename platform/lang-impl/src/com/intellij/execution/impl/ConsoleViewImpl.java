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

package com.intellij.execution.impl;

import com.google.common.base.CharMatcher;
import com.intellij.codeInsight.navigation.IncrementalSearchHandler;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.execution.ConsoleFolding;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ConsoleActionsPostProcessor;
import com.intellij.execution.actions.EOFAction;
import com.intellij.execution.filters.*;
import com.intellij.execution.filters.Filter.ResultItem;
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
import com.intellij.openapi.command.undo.UndoUtil;
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
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.*;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class ConsoleViewImpl extends JPanel implements ConsoleView, ObservableConsoleView, DataProvider, OccurenceNavigator {
  @NonNls private static final String CONSOLE_VIEW_POPUP_MENU = "ConsoleView.PopupMenu";
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.ConsoleViewImpl");

  private static final int DEFAULT_FLUSH_DELAY = SystemProperties.getIntProperty("console.flush.delay.ms", 200);

  private static final CharMatcher NEW_LINE_MATCHER = CharMatcher.anyOf("\n\r");

  public static final Key<ConsoleViewImpl> CONSOLE_VIEW_IN_EDITOR_VIEW = Key.create("CONSOLE_VIEW_IN_EDITOR_VIEW");

  private static boolean ourTypedHandlerInitialized;

  private static synchronized void initTypedHandler() {
    if (ourTypedHandlerInitialized) return;
    final EditorActionManager actionManager = EditorActionManager.getInstance();
    final TypedAction typedAction = actionManager.getTypedAction();
    typedAction.setupHandler(new MyTypedHandler(typedAction.getHandler()));
    ourTypedHandlerInitialized = true;
  }


  private final CommandLineFolding myCommandLineFolding = new CommandLineFolding();

  private final DisposedPsiManagerCheck myPsiDisposedCheck;
  private final boolean myIsViewer;

  private ConsoleState myState;

  private final Alarm mySpareTimeAlarm = new Alarm(this);
  @Nullable
  private final Alarm myHeavyAlarm;
  private volatile int myHeavyUpdateTicket;

  private final Collection<ChangeListener> myListeners = new CopyOnWriteArraySet<>();
  private final List<AnAction> customActions = new ArrayList<>();
  private final ConsoleBuffer myBuffer = new ConsoleBuffer();
  private boolean myUpdateFoldingsEnabled = true;
  private EditorHyperlinkSupport myHyperlinks;
  private MyDiffContainer myJLayeredPane;
  private JPanel myMainPanel;
  private boolean myAllowHeavyFilters;
  private boolean myLastStickingToEnd;
  private boolean myCancelStickToEnd;

  private boolean myTooMuchOfOutput;
  private boolean myInDocumentUpdate;

  // If true, then a document is being cleared right now.
  // Should be accessed in EDT only.
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private boolean myDocumentClearing;
  private int myLastAddedTextLength;
  private int consoleTooMuchTextBufferRatio;

  public Editor getEditor() {
    return myEditor;
  }

  public EditorHyperlinkSupport getHyperlinks() {
    return myHyperlinks;
  }

  public void scrollToEnd() {
    if (myEditor == null) return;
    EditorUtil.scrollToTheEnd(myEditor);
    myCancelStickToEnd = false;
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
    model.runBatchFoldingOperation(() -> {
      for (FoldRegion region : model.getAllFoldRegions()) {
        model.removeFoldRegion(region);
      }
    });
    myFolding.clear();

    updateFoldings(0, myEditor.getDocument().getLineCount() - 1, true);
  }

  static class TokenInfo {
    final ConsoleViewContentType contentType;
    int startOffset;
    int endOffset;

    TokenInfo(ConsoleViewContentType contentType, int startOffset, int endOffset) {
      this.contentType = contentType;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
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
  private final List<TokenInfo> myTokens = new ArrayList<>();

  private final TIntObjectHashMap<ConsoleFolding> myFolding = new TIntObjectHashMap<>();

  private String myHelpId;

  private final Alarm myFlushUserInputAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  private final Alarm myFlushAlarm = new Alarm(this);

  private final Set<MyFlushRunnable> myCurrentRequests = new HashSet<>();

  protected final CompositeFilter myFilters;

  @Nullable private final InputFilter myInputMessageFilter;

  private final Alarm myFoldingAlarm = new Alarm(this);
  private final List<FoldRegion> myPendingFoldRegions = new ArrayList<>();

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
    initTypedHandler();
    myIsViewer = viewer;
    myState = initialState;
    myPsiDisposedCheck = new DisposedPsiManagerCheck(project);
    myProject = project;

    myFilters = new CompositeFilter(project);
    if (usePredefinedMessageFilter) {
      for (ConsoleFilterProvider eachProvider : Extensions.getExtensions(ConsoleFilterProvider.FILTER_PROVIDERS)) {
        Filter[] filters;
        if (eachProvider instanceof ConsoleDependentFilterProvider) {
          filters = ((ConsoleDependentFilterProvider)eachProvider).getDefaultFilters(this, project, searchScope);
        }
        else if (eachProvider instanceof ConsoleFilterProviderEx) {
          filters = ((ConsoleFilterProviderEx)eachProvider).getDefaultFilters(project, searchScope);
        }
        else {
          filters = eachProvider.getDefaultFilters(project);
        }
        for (Filter filter : filters) {
          myFilters.addFilter(filter);
        }
      }
    }
    myFilters.setForceUseAllFilters(true);
    myHeavyUpdateTicket = 0;
    myHeavyAlarm = myFilters.isAnyHeavy() ? new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this) : null;


    ConsoleInputFilterProvider[] inputFilters = Extensions.getExtensions(ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS);
    if (inputFilters.length > 0) {
      CompositeInputFilter compositeInputFilter = new CompositeInputFilter(project);
      myInputMessageFilter = compositeInputFilter;
      for (ConsoleInputFilterProvider eachProvider : inputFilters) {
        InputFilter[] filters = eachProvider.getDefaultFilters(project);
        for (InputFilter filter : filters) {
          compositeInputFilter.addFilter(filter);
        }
      }
    }
    else {
      myInputMessageFilter = null;
    }

    consoleTooMuchTextBufferRatio = Registry.intValue("console.too.much.text.buffer.ratio");

    project.getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      private long myLastStamp;

      @Override
      public void enteredDumbMode() {
        if (myEditor == null) return;
        myLastStamp = myEditor.getDocument().getModificationStamp();

      }

      @Override
      public void exitDumbMode() {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (myEditor == null || project.isDisposed() || DumbService.getInstance(project).isDumb()) return;

          DocumentEx document = myEditor.getDocument();
          if (myLastStamp != document.getModificationStamp()) {
            clearHyperlinkAndFoldings();
            highlightHyperlinksAndFoldings(document.createRangeMarker(0, 0));
          }
        });
      }
    });

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
    }
    if (myFlushAlarm.isDisposed()) return;
    cancelAllFlushRequests();
    addFlushRequest(new MyClearRunnable());
    cancelHeavyAlarm();
  }

  @Override
  public void scrollTo(final int offset) {
    if (myEditor == null) return;
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
    if (myEditor == null) {
      return;
    }

    addFlushRequest(new MyFlushRunnable() {
      @Override
      public void doRun() {
        flushDeferredText();
        if (myEditor == null || myFlushAlarm.isDisposed()) {
          return;
        }

        scrollToEnd();
      }
    });
  }

  private void addFlushRequest(MyFlushRunnable scrollRunnable) {
    addFlushRequest(scrollRunnable, 0);
  }

  private void addFlushRequest(@NotNull MyFlushRunnable flushRunnable, final int millis) {
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

  public void setEmulateCarriageReturn(boolean emulate) {
    myBuffer.setKeepSlashR(emulate);
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
      performLaterWhenNoDeferredOutput(runnable);
    }
  }

  private void performLaterWhenNoDeferredOutput(final Runnable runnable) {
    if (mySpareTimeAlarm.isDisposed()) return;
    mySpareTimeAlarm.addRequest(
      () -> performWhenNoDeferredOutput(runnable),
      100,
      ModalityState.stateForComponent(myJLayeredPane)
    );
  }

  @Override
  public JComponent getComponent() {
    if (myMainPanel == null) {
      myMainPanel = new JPanel(new BorderLayout());
      myJLayeredPane = new MyDiffContainer(myMainPanel, myFilters.getUpdateMessage());
      Disposer.register(this, myJLayeredPane);
      add(myJLayeredPane, BorderLayout.CENTER);
    }

    if (myEditor == null) {
      initConsoleEditor();
      requestFlushImmediately();
      myMainPanel.add(createCenterComponent(), BorderLayout.CENTER);
    }
    return this;
  }

  /**
   * Adds transparent (actually, non-opaque) component over console.
   * It will be as big as console. Use it to draw on console because it does not prevent user from console usage.
   *
   * @param component component to add
   */
  public final void addLayerToPane(@NotNull final JComponent component) {
    getComponent(); // Make sure component exists
    component.setOpaque(false);
    component.setVisible(true);
    myJLayeredPane.add(component, 0);
  }

  private void initConsoleEditor() {
    myEditor = createConsoleEditor();
    registerConsoleEditorActions();
    myEditor.getScrollPane().setBorder(null);
    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        updateStickToEndState(true);
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        updateStickToEndState(false);
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        updateStickToEndState(false);
      }
    };
    myEditor.getScrollPane().addMouseWheelListener(mouseListener);
    myEditor.getScrollPane().getVerticalScrollBar().addMouseListener(mouseListener);
    myEditor.getScrollPane().getVerticalScrollBar().addMouseMotionListener(mouseListener);
    myHyperlinks = new EditorHyperlinkSupport(myEditor, myProject);
    myEditor.getScrollingModel().addVisibleAreaListener(new VisibleAreaListener() {
      @Override
      public void visibleAreaChanged(VisibleAreaEvent e) {
        // There is a possible case that the console text is populated while the console is not shown (e.g. we're debugging and
        // 'Debugger' tab is active while 'Console' is not). It's also possible that newly added text contains long lines that
        // are soft wrapped. We want to update viewport position then when the console becomes visible.
        Rectangle oldR = e.getOldRectangle();

        if (oldR != null && oldR.height <= 0 &&
            e.getNewRectangle().height > 0 &&
            isStickingToEnd()) {
          scrollToEnd();
        }
      }
    });
  }

  private void updateStickToEndState(boolean useImmediatePosition) {
    if (myEditor == null) return;

    JScrollBar scrollBar = myEditor.getScrollPane().getVerticalScrollBar();
    int scrollBarPosition = useImmediatePosition ? scrollBar.getValue() :
                            myEditor.getScrollingModel().getVisibleAreaOnScrollingFinished().y;
    boolean vscrollAtBottom = scrollBarPosition == scrollBar.getMaximum() - scrollBar.getVisibleAmount();
    boolean stickingToEnd = isStickingToEnd();

    if (!vscrollAtBottom && stickingToEnd) {
      myCancelStickToEnd = true;
    } else if (vscrollAtBottom && !stickingToEnd) {
      scrollToEnd();
    }
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
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (!myEditor.isDisposed()) {
          EditorFactory.getInstance().releaseEditor(myEditor);
        }
      }
    });
  }

  @Override
  public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
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

      if (contentType == ConsoleViewContentType.USER_INPUT && NEW_LINE_MATCHER.indexIn(s) >= 0) {
        flushDeferredUserInput();
      }
      if (myEditor != null) {
        final boolean shouldFlushNow = myBuffer.isUseCyclicBuffer() && myBuffer.getLength() >= myBuffer.getCyclicBufferSize();
        addFlushRequest(new MyFlushRunnable(), shouldFlushNow ? 0 : DEFAULT_FLUSH_DELAY);
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
    if (myEditor != null) {
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
    final boolean shouldStickToEnd = clear || !myCancelStickToEnd && isStickingToEnd();
    myCancelStickToEnd = false; // Cancel only needs to last for one update. Next time, isStickingToEnd() will be false.
    if (clear) {
      final DocumentEx document = editor.getDocument();
      synchronized (LOCK) {
        myTokens.clear();
        clearHyperlinkAndFoldings();
      }
      final int documentTextLength = document.getTextLength();
      if (documentTextLength > 0) {
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          document.setInBulkUpdate(true);
          try {
            myInDocumentUpdate = true;
            myDocumentClearing = true;
            document.deleteString(0, documentTextLength);
          }
          finally {
            document.setInBulkUpdate(false);
            myDocumentClearing = false;
            myInDocumentUpdate = false;
          }
        }, null, DocCommandGroupId.noneGroupId(document));
      }
      return;
    }


    final String addedText;
    final Collection<ConsoleViewContentType> contentTypes;
    int deferredTokensSize;
    synchronized (LOCK) {
      if (myOutputPaused) return;
      if (myBuffer.isEmpty()) return;

      addedText = myBuffer.getText();

      contentTypes = Collections.unmodifiableCollection(new HashSet<>(myBuffer.getDeferredTokenTypes()));
      List<TokenInfo> deferredTokens = myBuffer.getDeferredTokens();
      for (TokenInfo deferredToken : deferredTokens) {
        addToken(deferredToken.getLength(), deferredToken.getHyperlinkInfo(), deferredToken.contentType);
      }
      deferredTokensSize = deferredTokens.size();
      myBuffer.clear(false);
      cancelHeavyAlarm();
    }
    final Document document = myEditor.getDocument();
    final RangeMarker lastProcessedOutput = document.createRangeMarker(document.getTextLength(), document.getTextLength());

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      if (!shouldStickToEnd) {
        myEditor.getScrollingModel().accumulateViewportChanges();
      }
      try {
        myInDocumentUpdate = true;
        String[] strings = addedText.split("\\r", -1); // limit must be any negative number to avoid discarding of trailing empty strings
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
        if (!shouldStickToEnd) {
          myEditor.getScrollingModel().flushViewportChanges();
        }
      }
      if (!contentTypes.isEmpty()) {
        for (ChangeListener each : myListeners) {
          each.contentAdded(contentTypes);
        }
      }
    }, null, DocCommandGroupId.noneGroupId(document));
    synchronized (LOCK) {
      for (int i = myTokens.size() - 1; i >= 0 && deferredTokensSize > 0; i--, deferredTokensSize--) {
        TokenInfo token = myTokens.get(i);
        final HyperlinkInfo info = token.getHyperlinkInfo();
        if (info != null) {
          myHyperlinks.createHyperlink(token.startOffset, token.endOffset, null, info);
        }
      }
    }
    myPsiDisposedCheck.performCheck();
    myLastAddedTextLength = addedText.length();
    if (!myTooMuchOfOutput) {
      if (isTheAmountOfTextTooBig(myLastAddedTextLength)) { // disable hyperlinks and folding until new output arriving slows down again
        myTooMuchOfOutput = true;
        final EditorNotificationPanel comp =
          new EditorNotificationPanel().text("Too much output to process").icon(AllIcons.General.ExclMark);
        final Alarm tooMuchOutputAlarm = new Alarm();
        //show the notification with a delay to avoid blinking when "too much output" ceases quickly
        tooMuchOutputAlarm.addRequest(() -> add(comp, BorderLayout.NORTH), 300);
        performWhenNoDeferredOutput(new Runnable() {
          @Override
          public void run() {
            if (!isTheAmountOfTextTooBig(myLastAddedTextLength)) {
              try {
                highlightHyperlinksAndFoldings(lastProcessedOutput);
              }
              finally {
                myTooMuchOfOutput = false;
                remove(comp);
                tooMuchOutputAlarm.cancelAllRequests();
              }
            }
            else {
              myLastAddedTextLength = 0;
              performLaterWhenNoDeferredOutput(this);
            }
          }
        });
      }
      else {
        highlightHyperlinksAndFoldings(lastProcessedOutput);
      }
    }

    if (shouldStickToEnd) {
      scrollToEnd();
    }
  }

  private boolean isStickingToEnd() {
    if (myEditor == null) return myLastStickingToEnd;
    Document document = myEditor.getDocument();
    int caretOffset = myEditor.getCaretModel().getOffset();
    myLastStickingToEnd = document.getLineNumber(caretOffset) >= document.getLineCount() - 1;
    return myLastStickingToEnd;
  }

  private boolean isTheAmountOfTextTooBig(final int textLength) {
    return myBuffer.isUseCyclicBuffer() && textLength > myBuffer.getCyclicBufferSize() / consoleTooMuchTextBufferRatio;
  }

  private void clearHyperlinkAndFoldings() {
    myEditor.getMarkupModel().removeAllHighlighters();

    myPendingFoldRegions.clear();
    myFolding.clear();
    myFoldingAlarm.cancelAllRequests();
    myEditor.getFoldingModel().runBatchFoldingOperation(() -> myEditor.getFoldingModel().clearFoldRegions());

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
    myFlushUserInputAlarm.addRequest(() -> {
      if (myState.isRunning()) {
        try {
          // this may block forever, see IDEA-54340
          myState.sendUserInput(textToSend);
        }
        catch (IOException ignored) {
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
    myFilters.addFilter(filter);
  }

  @Override
  public void printHyperlink(final String hyperlinkText, final HyperlinkInfo info) {
    printHyperlink(hyperlinkText, ConsoleViewContentType.NORMAL_OUTPUT, info);
  }

  private EditorEx createConsoleEditor() {
    return ApplicationManager.getApplication().runReadAction(new Computable<EditorEx>() {
      @Override
      public EditorEx compute() {
        EditorEx editor = doCreateConsoleEditor();
        LOG.assertTrue(UndoUtil.isUndoDisabledFor(editor.getDocument()));
        editor.setContextMenuGroupId(null); // disabling default context menu
        editor.addEditorMouseListener(new EditorPopupHandler() {
          @Override
          public void invokePopup(final EditorMouseEvent event) {
            popupInvoked(event.getMouseEvent());
          }
        });
        editor.getDocument().addDocumentListener(new DocumentAdapter() {
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
        if (!myDocumentClearing) {
          // If document is being cleared now, then this event has been occurred as a result of calling clear() method.
          // At start clear() method sets 'myContentSize' to 0, so there is no need to perform update again.
          // Moreover, performing update of 'myContentSize' breaks executing "console.print();" immediately after "console.clear();".
          myContentSize -= Math.min(myContentSize, toRemoveLen);
        }
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
        if (ConsoleViewUtil.isReplaceActionEnabledForConsoleViewEditor(myEditor)) {
          clearHyperlinkAndFoldings();
          highlightHyperlinksAndFoldings(event.getDocument().createRangeMarker(0, 0));
        }
        else {
          LOG.warn("unhandled external change: " + event);
        }
      }
    }
  }

  protected EditorEx doCreateConsoleEditor() {
    return ConsoleViewUtil.setupConsoleEditor(myProject, true, false);
  }

  protected MyHighlighter createHighlighter() {
    return new MyHighlighter();
  }

  private void registerConsoleEditorActions() {
    Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_GOTO_DECLARATION);
    CustomShortcutSet shortcutSet = new CustomShortcutSet(ArrayUtil.mergeArrays(shortcuts, CommonShortcuts.ENTER.getShortcuts()));
    new HyperlinkNavigationAction().registerCustomShortcutSet(shortcutSet, myEditor.getContentComponent());


    if (!myIsViewer) {
      new EnterHandler().registerCustomShortcutSet(CommonShortcuts.ENTER, myEditor.getContentComponent());
      registerActionHandler(myEditor, IdeActions.ACTION_EDITOR_PASTE, new PasteHandler());
      registerActionHandler(myEditor, IdeActions.ACTION_EDITOR_BACKSPACE, new BackSpaceHandler());
      registerActionHandler(myEditor, IdeActions.ACTION_EDITOR_DELETE, new DeleteHandler());

      registerActionHandler(myEditor, EOFAction.ACTION_ID);
    }
  }

  private static void registerActionHandler(final Editor editor, final String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    action.registerCustomShortcutSet(action.getShortcutSet(), editor.getContentComponent());
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
    final ConsoleActionsPostProcessor[] postProcessors = Extensions.getExtensions(ConsoleActionsPostProcessor.EP_NAME);
    AnAction[] result = group.getChildren(null);

    for (ConsoleActionsPostProcessor postProcessor : postProcessors) {
      result = postProcessor.postProcessPopupActions(this, result);
    }
    final DefaultActionGroup processedGroup = new DefaultActionGroup(result);
    final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.EDITOR_POPUP, processedGroup);
    menu.getComponent().show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
  }

  private void highlightHyperlinksAndFoldings(RangeMarker lastProcessedOutput) {
    boolean canHighlightHyperlinks = !myFilters.isEmpty();

    if (!canHighlightHyperlinks && myUpdateFoldingsEnabled) {
      return;
    }
    final int line1 = lastProcessedOutput.isValid() ? myEditor.getDocument().getLineNumber(lastProcessedOutput.getEndOffset()) : 0;
    lastProcessedOutput.dispose();
    int endLine = myEditor.getDocument().getLineCount() - 1;
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (canHighlightHyperlinks) {
      myHyperlinks.highlightHyperlinks(myFilters, line1, endLine);
    }

    if (myAllowHeavyFilters && myFilters.isAnyHeavy() && myFilters.shouldRunHeavy()) {
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
        if (!myFilters.shouldRunHeavy()) return;
        try {
          myFilters.applyHeavyFilter(documentCopy, startOffset, startLine, new Consumer<FilterMixin.AdditionalHighlight>() {
            @Override
            public void consume(final FilterMixin.AdditionalHighlight additionalHighlight) {
              addFlushRequest(new MyFlushRunnable() {
                @Override
                public void doRun() {
                  if (myHeavyUpdateTicket != currentValue) return;
                  TextAttributes additionalAttributes = additionalHighlight.getTextAttributes(null);
                  if (additionalAttributes != null) {
                    ResultItem item = additionalHighlight.getResultItems().get(0);
                    myHyperlinks.addHighlighter(item.getHighlightStartOffset(), item.getHighlightEndOffset(),
                        additionalAttributes);
                  }
                  else {
                    myHyperlinks.highlightHyperlinks(additionalHighlight);
                  }
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
            SwingUtilities.invokeLater(() -> myJLayeredPane.finishUpdating());
          }
        }
      }
    }, 0);
  }

  private void updateFoldings(final int line1, final int endLine, boolean immediately) {
    final Document document = myEditor.getDocument();
    final CharSequence chars = document.getCharsSequence();
    final int startLine = Math.max(0, line1);
    final List<FoldRegion> toAdd = new ArrayList<>();
    for (int line = startLine; line <= endLine; line++) {
      boolean flushOnly = line == endLine;
      /*
      Grep Console plugin allows to fold empty lines. We need to handle this case in a special way.

      Multiple lines are grouped into one folding, but to know when you can create the folding,
      you need a line which does not belong to that folding.
      When a new line, or a chunk of lines is printed, #addFolding is called for that lines + for an empty string
      (which basically does only one thing, gets a folding displayed).
      We do not want to process that empty string, but also we do not want to wait for another line
      which will create and display the folding - we'd see an unfolded stacktrace until another text came and flushed it.
      So therefore the condition, the last line(empty string) should still flush, but not be processed by
      com.intellij.execution.ConsoleFolding.
       */
      addFolding(document, chars, line, toAdd, flushOnly);
    }
    if (!toAdd.isEmpty()) {
      doUpdateFolding(toAdd, immediately);
    }
  }

  private void doUpdateFolding(final List<FoldRegion> toAdd, final boolean immediately) {
    assertIsDispatchThread();
    myPendingFoldRegions.addAll(toAdd);

    myFoldingAlarm.cancelAllRequests();
    final Runnable runnable = () -> {
      if (myEditor == null || myEditor.isDisposed()) {
        return;
      }

      assertIsDispatchThread();
      final FoldingModel model = myEditor.getFoldingModel();
      final Runnable operation = () -> {
        assertIsDispatchThread();
        for (FoldRegion region : myPendingFoldRegions) {
          region.setExpanded(false);
          model.addFoldRegion(region);
        }
        myPendingFoldRegions.clear();
      };
      if (immediately) {
        model.runBatchFoldingOperation(operation);
      }
      else {
        model.runBatchFoldingOperationDoNotCollapseCaret(operation);
      }
    };
    if (immediately || myPendingFoldRegions.size() > 100) {
      runnable.run();
    }
    else {
      myFoldingAlarm.addRequest(runnable, 50);
    }
  }

  private void addFolding(Document document, CharSequence chars, int line, List<FoldRegion> toAdd, boolean flushOnly) {
    ConsoleFolding current = null;
    if (!flushOnly) {
      String commandLinePlaceholder = myCommandLineFolding.getPlaceholder(line);
      if (commandLinePlaceholder != null) {
        FoldRegion region = myEditor.getFoldingModel()
          .createFoldRegion(document.getLineStartOffset(line), document.getLineEndOffset(line), commandLinePlaceholder, null, false);
        toAdd.add(region);
        return;
      }
      current = foldingForLine(EditorHyperlinkSupport.getLineText(document, line, false));
      if (current != null) {
        myFolding.put(line, current);
      }
    }

    final ConsoleFolding prevFolding = myFolding.get(line - 1);
    if (current == null && prevFolding != null) {
      final int lEnd = line - 1;
      int lStart = lEnd;
      while (prevFolding.equals(myFolding.get(lStart - 1))) lStart--;

      for (int i = lStart; i <= lEnd; i++) {
        myFolding.remove(i);
      }

      List<String> toFold = new ArrayList<>(lEnd - lStart + 1);
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
    private final ConsoleView myConsoleView;

    @SuppressWarnings("unused")
    public ClearAllAction() {
      this(null);
    }

    public ClearAllAction(ConsoleView consoleView) {
      super(ExecutionBundle.message("clear.all.from.console.action.name"), "Clear the contents of the console", AllIcons.Actions.GC);
      myConsoleView = consoleView;
    }

    @Override
    public void update(AnActionEvent e) {
      boolean enabled = myConsoleView != null && myConsoleView.getContentSize() > 0;
      if (!enabled) {
        enabled = e.getData(LangDataKeys.CONSOLE_VIEW) != null;
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null && editor.getDocument().getTextLength() == 0) {
          enabled = false;
        }
      }
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final ConsoleView consoleView = myConsoleView != null ? myConsoleView : e.getData(LangDataKeys.CONSOLE_VIEW);
      if (consoleView != null) {
        consoleView.clear();
      }
    }
  }

  private class MyHighlighter extends DocumentAdapter implements EditorHighlighter {
    private HighlighterClient myEditor;

    @NotNull
    @Override
    public HighlighterIterator createIterator(final int startOffset) {
      final int startIndex = ConsoleUtil.findTokenInfoIndexByOffset(myTokens, startOffset);

      return new HighlighterIterator() {
        private int myIndex = startIndex;

        @Override
        public TextAttributes getTextAttributes() {
          return atEnd() ? null : getTokenInfo().contentType.getAttributes();
        }

        @Override
        public int getStart() {
          return atEnd() ? 0 : getTokenInfo().startOffset;
        }

        @Override
        public int getEnd() {
          return atEnd() ? 0 : getTokenInfo().endOffset;
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
    public void setText(@NotNull final CharSequence text) {
    }

    @Override
    public void setEditor(@NotNull final HighlighterClient editor) {
      LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
      myEditor = editor;
    }

    @Override
    public void setColorScheme(@NotNull EditorColorsScheme scheme) {
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
      String s = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
      if (s == null) return;
      s = StringUtil.convertLineSeparators(s);
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

    return EditorHyperlinkSupport.getNextOccurrence(myEditor, delta, next -> {
      int offset = next.getStartOffset();
      scrollTo(offset);
      final HyperlinkInfo hyperlinkInfo = EditorHyperlinkSupport.getHyperlinkInfo(next);
      if (hyperlinkInfo instanceof BrowserHyperlinkInfo) {
        return;
      }
      if (hyperlinkInfo instanceof HyperlinkInfoBase) {
        VisualPosition position = myEditor.offsetToVisualPosition(offset);
        Point point = myEditor.visualPositionToXY(new VisualPosition(position.getLine() + 1, position.getColumn()));
        ((HyperlinkInfoBase)hyperlinkInfo).navigate(myProject, new RelativePoint(myEditor.getContentComponent(), point));
      }
      else if (hyperlinkInfo != null) {
        hyperlinkInfo.navigate(myProject);
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
      @Override
      protected Editor getEditor(AnActionEvent e) {
        return myEditor;
      }

      @Override
      public void setSelected(AnActionEvent e, final boolean state) {
        super.setSelected(e, state);
        if (myEditor == null) {
          return;
        }

        final String placeholder = myCommandLineFolding.getPlaceholder(0);
        final FoldingModel foldingModel = myEditor.getFoldingModel();
        final int firstLineEnd = myEditor.getDocument().getLineEndOffset(0);
        foldingModel.runBatchFoldingOperation(() -> {
          FoldRegion[] regions = foldingModel.getAllFoldRegions();
          if (regions.length > 0 && regions[0].getStartOffset() == 0 && regions[0].getEndOffset() == firstLineEnd) {
            foldingModel.removeFoldRegion(regions[0]);
          }
          if (placeholder != null) {
            FoldRegion foldRegion = foldingModel.addFoldRegion(0, firstLineEnd, placeholder);
            if (foldRegion != null) {
              foldRegion.setExpanded(false);
            }
          }
        });
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
    consoleActions[5] = new ClearAllAction(this);
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
      if (consoleView.myTokens.isEmpty()) {
        addToken(0, null, ConsoleViewContentType.SYSTEM_OUTPUT);
      }
      final TokenInfo info = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
      if (info.contentType != ConsoleViewContentType.USER_INPUT && !StringUtil.containsChar(textToUse, '\n')) {
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
   * Command line used to launch application/test from idea may be quite long.
   * Hence, it takes many visual lines during representation if soft wraps are enabled
   * or, otherwise, takes many columns and makes horizontal scrollbar thumb too small.
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
    private String getPlaceholder(int line) {
      if (myEditor == null || line != 0) {
        return null;
      }

      String text = EditorHyperlinkSupport.getLineText(myEditor.getDocument(), 0, false);
      // Don't fold the first line if the line is not that big.
      if (text.length() < 1000) {
        return null;
      }
      int index = 0;
      if (text.charAt(0) == '"') {
        index = text.indexOf('"', 1) + 1;
      }
      if (index == 0) {
        boolean nonWhiteSpaceFound = false;
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

  private class HyperlinkNavigationAction extends DumbAwareAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
      Runnable runnable = myHyperlinks.getLinkNavigationRunnable(myEditor.getCaretModel().getLogicalPosition());
      assert runnable != null;
      runnable.run();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myHyperlinks.getLinkNavigationRunnable(myEditor.getCaretModel().getLogicalPosition()) != null);
    }
  }

  @NotNull
  public String getText() {
    return myEditor.getDocument().getText();
  }
}

