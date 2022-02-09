// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.impl;

import com.google.common.base.CharMatcher;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.codeInsight.navigation.IncrementalSearchHandler;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.execution.ConsoleFolding;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ClearConsoleAction;
import com.intellij.execution.actions.ConsoleActionsPostProcessor;
import com.intellij.execution.actions.EOFAction;
import com.intellij.execution.filters.*;
import com.intellij.execution.filters.Filter.ResultItem;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.ScrollingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.pom.Navigatable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsoleViewImpl extends JPanel implements ConsoleView, ObservableConsoleView, DataProvider, OccurenceNavigator {
  @NonNls private static final String CONSOLE_VIEW_POPUP_MENU = "ConsoleView.PopupMenu";
  private static final Logger LOG = Logger.getInstance(ConsoleViewImpl.class);

  private static final int DEFAULT_FLUSH_DELAY = SystemProperties.getIntProperty("console.flush.delay.ms", 200);

  public static final Key<ConsoleViewImpl> CONSOLE_VIEW_IN_EDITOR_VIEW = Key.create("CONSOLE_VIEW_IN_EDITOR_VIEW");

  private static boolean ourTypedHandlerInitialized;
  private final Alarm myFlushUserInputAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  private static final CharMatcher NEW_LINE_MATCHER = CharMatcher.anyOf("\n\r");

  private final CommandLineFolding myCommandLineFolding = new CommandLineFolding();

  private final DisposedPsiManagerCheck myPsiDisposedCheck;

  private final boolean myIsViewer;
  @NotNull
  private ConsoleState myState;

  private final Alarm mySpareTimeAlarm = new Alarm(this);

  @NotNull
  private final Alarm myHeavyAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  private volatile int myHeavyUpdateTicket;
  private final Collection<ChangeListener> myListeners = new CopyOnWriteArraySet<>();

  private final List<AnAction> customActions = new ArrayList<>();
  /**
   * the text from {@link #print(String, ConsoleViewContentType)} goes there and stays there until {@link #flushDeferredText()} is called
   * guarded by LOCK
   */
  private final TokenBuffer myDeferredBuffer = new TokenBuffer(ConsoleBuffer.useCycleBuffer() && ConsoleBuffer.getCycleBufferSize() > 0 ? ConsoleBuffer.getCycleBufferSize() : Integer.MAX_VALUE);

  private boolean myUpdateFoldingsEnabled = true;

  private MyDiffContainer myJLayeredPane;
  private JPanel myMainPanel;
  private boolean myAllowHeavyFilters;
  private boolean myCancelStickToEnd; // accessed in EDT only

  private final Alarm myFlushAlarm = new Alarm(this);

  private final Project myProject;

  private boolean myOutputPaused; // guarded by LOCK

  // do not access directly, use getEditor() for proper synchronization
  private EditorEx myEditor; // guarded by LOCK

  private final Object LOCK = ObjectUtils.sentinel("ConsoleView lock");

  private String myHelpId;

  private final GlobalSearchScope mySearchScope;

  private final List<Filter> myCustomFilters = new SmartList<>();

  @NotNull
  private final InputFilter myInputMessageFilter;
  private volatile List<Filter> myPredefinedFilters = Collections.emptyList();

  public ConsoleViewImpl(@NotNull Project project, boolean viewer) {
    this(project, GlobalSearchScope.allScope(project), viewer, true);
  }

  public ConsoleViewImpl(@NotNull Project project,
                         @NotNull GlobalSearchScope searchScope,
                         boolean viewer,
                         boolean usePredefinedMessageFilter) {
    this(project, searchScope, viewer,
         new ConsoleState.NotStartedStated() {
           @NotNull
           @Override
           public ConsoleState attachTo(@NotNull ConsoleViewImpl console, @NotNull ProcessHandler processHandler) {
             return new ConsoleViewRunningState(console, processHandler, this, true, true);
           }
         },
         usePredefinedMessageFilter);
  }

  protected ConsoleViewImpl(@NotNull Project project,
                            @NotNull GlobalSearchScope searchScope,
                            boolean viewer,
                            @NotNull ConsoleState initialState,
                            boolean usePredefinedMessageFilter) {
    super(new BorderLayout());
    initTypedHandler();
    myIsViewer = viewer;
    myState = initialState;
    myPsiDisposedCheck = new DisposedPsiManagerCheck(project);
    myProject = project;
    mySearchScope = searchScope;

    myInputMessageFilter = ConsoleViewUtil.computeInputFilter(this, project, searchScope);
    project.getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      private long myLastStamp;

      @Override
      public void enteredDumbMode() {
        Editor editor = getEditor();
        if (editor == null) return;
        myLastStamp = editor.getDocument().getModificationStamp();
      }

      @Override
      public void exitDumbMode() {
        ApplicationManager.getApplication().invokeLater(() -> {
          Editor editor = getEditor();
          if (editor == null || project.isDisposed() || DumbService.getInstance(project).isDumb()) return;

          Document document = editor.getDocument();
          if (myLastStamp != document.getModificationStamp()) {
            rehighlightHyperlinksAndFoldings();
          }
        });
      }
    });
    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(EditorColorsManager.TOPIC, __ -> {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (isDisposed()) return;
        ConsoleTokenUtil.updateAllTokenTextAttributes(getEditor(), project);
      });
    if (usePredefinedMessageFilter) {
      updatePredefinedFiltersLater();
      ApplicationManager.getApplication().getMessageBus().connect(this)
        .subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
        @Override
        public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
          updatePredefinedFiltersLater();
        }

        @Override
        public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
          updatePredefinedFiltersLater();
        }
      });
    }
  }

  private void updatePredefinedFiltersLater() {
    ReadAction
      .nonBlocking(() -> ConsoleViewUtil.computeConsoleFilters(myProject, this, mySearchScope))
      .expireWith(this)
      .finishOnUiThread(ModalityState.stateForComponent(this), filters -> {
        myPredefinedFilters = filters;
        rehighlightHyperlinksAndFoldings();
      }).submit(AppExecutorUtil.getAppExecutorService());
  }

  private static void initTypedHandler() {
    if (ourTypedHandlerInitialized) return;
    EditorActionManager.getInstance();
    TypedAction typedAction = TypedAction.getInstance();
    typedAction.setupHandler(new MyTypedHandler(typedAction.getHandler()));
    ourTypedHandlerInitialized = true;
  }

  public Editor getEditor() {
    synchronized (LOCK) {
      return myEditor;
    }
  }

  public EditorHyperlinkSupport getHyperlinks() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Editor editor = getEditor();
    return editor == null ? null : EditorHyperlinkSupport.get(editor);
  }

  public void scrollToEnd() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Editor editor = getEditor();
    if (editor != null) {
      scrollToEnd(editor);
    }
  }

  private void scrollToEnd(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorUtil.scrollToTheEnd(editor, true);
    myCancelStickToEnd = false;
  }

  public void foldImmediately() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myFlushAlarm.isEmpty()) {
      cancelAllFlushRequests();
      flushDeferredText();
    }

    Editor editor = getEditor();
    FoldingModel model = editor.getFoldingModel();
    model.runBatchFoldingOperation(() -> {
      for (FoldRegion region : model.getAllFoldRegions()) {
        model.removeFoldRegion(region);
      }
    });

    updateFoldings(0, editor.getDocument().getLineCount() - 1);
  }

  @Override
  public void attachToProcess(@NotNull ProcessHandler processHandler) {
    myState = myState.attachTo(this, processHandler);
  }

  @Override
  public void clear() {
    synchronized (LOCK) {
      if (getEditor() == null) return;
      // real document content will be cleared on next flush;
      myDeferredBuffer.clear();
    }
    if (!myFlushAlarm.isDisposed()) {
      cancelAllFlushRequests();
      addFlushRequest(0, CLEAR);
      cancelHeavyAlarm();
    }
  }

  @Override
  public void scrollTo(int offset) {
    if (getEditor() == null) return;
    final class ScrollRunnable extends FlushRunnable {
      private ScrollRunnable() {
        super(true); // each request must be executed
      }

      @Override
      public void doRun() {
        flushDeferredText();
        Editor editor = getEditor();
        if (editor == null) return;
        int moveOffset = getEffectiveOffset(editor);
        editor.getCaretModel().moveToOffset(moveOffset);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }

      private int getEffectiveOffset(@NotNull Editor editor) {
        int moveOffset = Math.min(offset, editor.getDocument().getTextLength());
        if (ConsoleBuffer.useCycleBuffer() && moveOffset >= editor.getDocument().getTextLength()) {
          moveOffset = 0;
        }
        return moveOffset;
      }
    }
    addFlushRequest(0, new ScrollRunnable());
  }

  @Override
  public void requestScrollingToEnd() {
    if (getEditor() == null) {
      return;
    }

    addFlushRequest(0, new FlushRunnable(true) {
      @Override
      public void doRun() {
        flushDeferredText();
        Editor editor = getEditor();
        if (editor != null && !myFlushAlarm.isDisposed()) {
          scrollToEnd(editor);
        }
      }
    });
  }

  private void addFlushRequest(int millis, @NotNull FlushRunnable flushRunnable) {
    flushRunnable.queue(millis);
  }

  @Override
  public void setOutputPaused(boolean value) {
    synchronized (LOCK) {
      myOutputPaused = value;
      if (!value && getEditor() != null) {
        requestFlushImmediately();
      }
    }
  }

  @Override
  public boolean isOutputPaused() {
    synchronized (LOCK) {
      return myOutputPaused;
    }
  }

  private boolean keepSlashR = true;
  public void setEmulateCarriageReturn(boolean emulate) {
    keepSlashR = emulate;
  }

  @Override
  public boolean hasDeferredOutput() {
    synchronized (LOCK) {
      return myDeferredBuffer.length() > 0;
    }
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!hasDeferredOutput()) {
      runnable.run();
      return;
    }
    if (mySpareTimeAlarm.isDisposed()) {
      return;
    }
    if (myJLayeredPane == null) {
      getComponent();
    }
    mySpareTimeAlarm.addRequest(
      () -> performWhenNoDeferredOutput(runnable),
      100,
      ModalityState.stateForComponent(myJLayeredPane)
    );
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myMainPanel == null) {
      myMainPanel = new JPanel(new BorderLayout());
      myJLayeredPane = new MyDiffContainer(myMainPanel, createCompositeFilter().getUpdateMessage());
      Disposer.register(this, myJLayeredPane);
      add(myJLayeredPane, BorderLayout.CENTER);
    }

    if (getEditor() == null) {
      EditorEx editor = initConsoleEditor();
      synchronized (LOCK) {
        myEditor = editor;
      }
      requestFlushImmediately();
      myMainPanel.add(createCenterComponent(), BorderLayout.CENTER);
    }
    return this;
  }

  @NotNull
  protected CompositeFilter createCompositeFilter() {
    CompositeFilter compositeFilter = new CompositeFilter(myProject, ContainerUtil.concat(myCustomFilters, myPredefinedFilters));
    compositeFilter.setForceUseAllFilters(true);
    return compositeFilter;
  }

  /**
   * Adds transparent (actually, non-opaque) component over console.
   * It will be as big as console. Use it to draw on console because it does not prevent user from console usage.
   *
   * @param component component to add
   */
  public final void addLayerToPane(@NotNull JComponent component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    getComponent(); // Make sure component exists
    component.setOpaque(false);
    component.setVisible(true);
    myJLayeredPane.add(component, 0);
  }

  @NotNull
  private EditorEx initConsoleEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorEx editor = createConsoleEditor();
    registerConsoleEditorActions(editor);
    editor.getScrollPane().setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        updateStickToEndState(editor, true);
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        updateStickToEndState(editor, false);
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.isShiftDown()) return; // ignore horizontal scrolling
        updateStickToEndState(editor, false);
      }
    };
    editor.getScrollPane().addMouseWheelListener(mouseListener);
    editor.getScrollPane().getVerticalScrollBar().addMouseListener(mouseListener);
    editor.getScrollPane().getVerticalScrollBar().addMouseMotionListener(mouseListener);
    editor.getScrollingModel().addVisibleAreaListener(e -> {
      // There is a possible case that the console text is populated while the console is not shown (e.g. we're debugging and
      // 'Debugger' tab is active while 'Console' is not). It's also possible that newly added text contains long lines that
      // are soft wrapped. We want to update viewport position then when the console becomes visible.
      Rectangle oldR = e.getOldRectangle();

      if (oldR != null && oldR.height <= 0 && e.getNewRectangle().height > 0 && isStickingToEnd(editor)) {
        scrollToEnd(editor);
      }
    });
    return editor;
  }

  private void updateStickToEndState(@NotNull EditorEx editor, boolean useImmediatePosition) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    JScrollBar scrollBar = editor.getScrollPane().getVerticalScrollBar();
    int scrollBarPosition = useImmediatePosition ? scrollBar.getValue() :
                            editor.getScrollingModel().getVisibleAreaOnScrollingFinished().y;
    boolean vscrollAtBottom = scrollBarPosition == scrollBar.getMaximum() - scrollBar.getVisibleAmount();
    boolean stickingToEnd = isStickingToEnd(editor);

    if (!vscrollAtBottom && stickingToEnd) {
      myCancelStickToEnd = true;
    } 
    else if (vscrollAtBottom && !stickingToEnd) {
      scrollToEnd(editor);
    }
  }

  @NotNull
  protected JComponent createCenterComponent() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getEditor().getComponent();
  }

  @Override
  public void dispose() {
    myState = myState.dispose();
    Editor editor = getEditor();
    if (editor != null) {
      cancelAllFlushRequests();
      mySpareTimeAlarm.cancelAllRequests();
      disposeEditor();
      editor.putUserData(CONSOLE_VIEW_IN_EDITOR_VIEW, null);
      synchronized (LOCK) {
        myDeferredBuffer.clear();
        myEditor = null;
      }
    }
  }

  private void cancelAllFlushRequests() {
    myFlushAlarm.cancelAllRequests();
    CLEAR.clearRequested();
    FLUSH.clearRequested();
  }

  @TestOnly
  public void waitAllRequests() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert ApplicationManager.getApplication().isUnitTestMode();
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      while (true) {
        try {
          myFlushAlarm.waitForAllExecuted(10, TimeUnit.SECONDS);
          myFlushUserInputAlarm.waitForAllExecuted(10, TimeUnit.SECONDS);
          myFlushAlarm.waitForAllExecuted(10, TimeUnit.SECONDS);
          myFlushUserInputAlarm.waitForAllExecuted(10, TimeUnit.SECONDS);
          return;
        }
        catch (CancellationException e) {
          //try again
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
          throw new RuntimeException(e);
        }
      }
    });
    try {
      while (true) {
        try {
          future.get(10, TimeUnit.MILLISECONDS);
          break;
        }
        catch (TimeoutException ignored) {
        }
        UIUtil.dispatchAllInvocationEvents();
      }
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected void disposeEditor() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      Editor editor = getEditor();
      if (!editor.isDisposed()) {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    });
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    List<Pair<String, ConsoleViewContentType>> result = myInputMessageFilter.applyFilter(text, contentType);
    if (result == null) {
      print(text, contentType, null);
    }
    else {
      for (Pair<String, ConsoleViewContentType> pair : result) {
        if (pair.first != null) {
          print(pair.first, pair.second == null ? contentType : pair.second, null);
        }
      }
    }
  }

  protected void print(@NotNull String text, @NotNull ConsoleViewContentType contentType, @Nullable HyperlinkInfo info) {
    text = Strings.convertLineSeparators(text, keepSlashR);
    synchronized (LOCK) {
      boolean hasEditor = getEditor() != null;
      myDeferredBuffer.print(text, contentType, info);

      if (hasEditor) {
        if (contentType == ConsoleViewContentType.USER_INPUT) {
          requestFlushImmediately();
        }
        else {
          boolean shouldFlushNow = myDeferredBuffer.length() >= myDeferredBuffer.getCycleBufferSize();
          addFlushRequest(shouldFlushNow ? 0 : DEFAULT_FLUSH_DELAY, FLUSH);
        }
      }
    }
  }

  // send text which was typed in the console to the running process
  private void sendUserInput(@NotNull CharSequence typedText) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myState.isRunning() && NEW_LINE_MATCHER.indexIn(typedText) >= 0) {
      CharSequence textToSend = ConsoleTokenUtil.computeTextToSend(getEditor(), getProject());
      if (textToSend.length() != 0) {
        myFlushUserInputAlarm.addRequest(() -> {
          if (myState.isRunning()) {
            try {
              // this may block forever, see IDEA-54340
              myState.sendUserInput(textToSend.toString());
            }
            catch (IOException ignored) {
            }
          }
        }, 0);
      }
    }
  }

  protected ModalityState getStateForUpdate() {
    return null;
  }

  private void requestFlushImmediately() {
    addFlushRequest(0, FLUSH);
  }

  /**
   * Holds number of symbols managed by the current console.
   * <p/>
   * Total number is assembled as a sum of symbols that are already pushed to the document and number of deferred symbols that
   * are awaiting to be pushed to the document.
   */
  @Override
  public int getContentSize() {
    int length;
    Editor editor;
    synchronized (LOCK) {
      length = myDeferredBuffer.length();
      editor = getEditor();
    }
    return (editor == null || CLEAR.hasRequested() ? 0 : editor.getDocument().getTextLength()) + length;
  }

  @Override
  public boolean canPause() {
    return true;
  }

  public void flushDeferredText() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isDisposed()) return;
    Editor editor = getEditor();
    boolean shouldStickToEnd = !myCancelStickToEnd && isStickingToEnd(editor);
    myCancelStickToEnd = false; // Cancel only needs to last for one update. Next time, isStickingToEnd() will be false.

    List<TokenBuffer.TokenInfo> deferredTokens;
    Document document = editor.getDocument();

    synchronized (LOCK) {
      if (myOutputPaused) return;

      deferredTokens = myDeferredBuffer.drain();
      if (deferredTokens.isEmpty()) return;
      cancelHeavyAlarm();
    }

    RangeMarker lastProcessedOutput = document.createRangeMarker(document.getTextLength(), document.getTextLength());

    if (!shouldStickToEnd) {
      ((ScrollingModelEx)editor.getScrollingModel()).accumulateViewportChanges();
    }
    Collection<ConsoleViewContentType> contentTypes = new HashSet<>();
    List<Pair<String, ConsoleViewContentType>> contents = new ArrayList<>();
    CharSequence addedText;
    try {
      // the text can contain one "\r" at the start meaning we should delete the last line
      boolean startsWithCR = deferredTokens.get(0) == TokenBuffer.CR_TOKEN;
      if (startsWithCR) {
        // remove last line if any
        if (document.getLineCount() != 0) {
          int lineStartOffset = document.getLineStartOffset(document.getLineCount() - 1);
          document.deleteString(lineStartOffset, document.getTextLength());
        }
      }
      int startIndex = startsWithCR ? 1 : 0;
      List<TokenBuffer.TokenInfo> refinedTokens = new ArrayList<>(deferredTokens.size() - startIndex);
      int backspacePrefixLength = ConsoleTokenUtil.evaluateBackspacesInTokens(deferredTokens, startIndex, refinedTokens);
      if (backspacePrefixLength > 0) {
        int lineCount = document.getLineCount();
        if (lineCount != 0) {
          int lineStartOffset = document.getLineStartOffset(lineCount - 1);
          document.deleteString(Math.max(lineStartOffset, document.getTextLength() - backspacePrefixLength), document.getTextLength());
        }
      }
      addedText = TokenBuffer.getRawText(refinedTokens);
      document.insertString(document.getTextLength(), addedText);
      ConsoleTokenUtil.highlightTokenTextAttributes(getEditor(), getProject(), refinedTokens, getHyperlinks(), contentTypes, contents);
    }
    finally {
      if (!shouldStickToEnd) {
        ((ScrollingModelEx)editor.getScrollingModel()).flushViewportChanges();
      }
    }
    if (!contentTypes.isEmpty()) {
      for (ChangeListener each : myListeners) {
        each.contentAdded(contentTypes);
      }
    }
    if (!contents.isEmpty()) {
      for (ChangeListener each : myListeners) {
        for (int i = contents.size() - 1; i >= 0; i--) {
          each.textAdded(contents.get(i).first, contents.get(i).second);
        }
      }
    }
    myPsiDisposedCheck.performCheck();

    int startLine = lastProcessedOutput.isValid() ? editor.getDocument().getLineNumber(lastProcessedOutput.getEndOffset()) : 0;
    lastProcessedOutput.dispose();
    highlightHyperlinksAndFoldings(startLine);

    if (shouldStickToEnd) {
      scrollToEnd();
    }
    sendUserInput(addedText);
  }

  private boolean isDisposed() {
    Editor editor = getEditor();
    return myProject.isDisposed() || editor == null || editor.isDisposed();
  }

  protected void doClear() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (isDisposed()) return;

    Editor editor = getEditor();
    Document document = editor.getDocument();
    int documentTextLength = document.getTextLength();
    if (documentTextLength > 0) {
      DocumentUtil.executeInBulk(document, () -> document.deleteString(0, documentTextLength));
    }
    synchronized (LOCK) {
      clearHyperlinkAndFoldings();
    }
    MarkupModel model = DocumentMarkupModel.forDocument(editor.getDocument(), getProject(), true);
    model.removeAllHighlighters(); // remove all empty highlighters leftovers if any
    editor.getInlayModel().getInlineElementsInRange(0, 0).forEach(Disposer::dispose); // remove inlays if any
  }

  protected static boolean isStickingToEnd(@NotNull Editor editor) {
    Document document = editor.getDocument();
    int caretOffset = editor.getCaretModel().getOffset();
    return document.getLineNumber(caretOffset) >= document.getLineCount() - 1;
  }

  private void clearHyperlinkAndFoldings() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Editor editor = getEditor();
    for (RangeHighlighter highlighter : editor.getMarkupModel().getAllHighlighters()) {
      if (highlighter.getUserData(ConsoleTokenUtil.MANUAL_HYPERLINK) == null) {
        editor.getMarkupModel().removeHighlighter(highlighter);
      }
    }

    editor.getFoldingModel().runBatchFoldingOperation(() -> ((FoldingModelEx)editor.getFoldingModel()).clearFoldRegions());

    cancelHeavyAlarm();
  }

  private void cancelHeavyAlarm() {
    if (!myHeavyAlarm.isDisposed()) {
      myHeavyAlarm.cancelAllRequests();
      ++myHeavyUpdateTicket;
    }
  }

  @Override
  public Object getData(@NotNull String dataId) {
    Editor editor = getEditor();
    if (editor == null) {
      return null;
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      int offset = editor.getCaretModel().getOffset();
      HyperlinkInfo info = getHyperlinks().getHyperlinkAt(offset);
      return info == null ? null : new Navigatable() {
        @Override public void navigate(boolean requestFocus) { info.navigate(myProject); }
        @Override public boolean canNavigate() { return true; }
        @Override public boolean canNavigateToSource() { return true; }
      };
    }

    if (CommonDataKeys.EDITOR.is(dataId)) {
      return editor;
    }
    if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
      return myHelpId;
    }
    if (LangDataKeys.CONSOLE_VIEW.is(dataId)) {
      return this;
    }
    return null;
  }

  @Override
  public void setHelpId(@NotNull String helpId) {
    myHelpId = helpId;
  }

  public void setUpdateFoldingsEnabled(boolean updateFoldingsEnabled) {
    myUpdateFoldingsEnabled = updateFoldingsEnabled;
  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {
    myCustomFilters.add(filter);
  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {
    print(hyperlinkText, ConsoleViewContentType.NORMAL_OUTPUT, info);
  }

  @NotNull
  private EditorEx createConsoleEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorEx editor = doCreateConsoleEditor();
    LOG.assertTrue(UndoUtil.isUndoDisabledFor(editor.getDocument()), "Undo must be disabled in console for performance reasons");
    LOG.assertTrue(!((DocumentImpl)editor.getDocument()).isWriteThreadOnly(), "Console document must support background modifications, see e.g. ConsoleViewUtil.setupConsoleEditor() "+getClass());
    editor.installPopupHandler(new ContextMenuPopupHandler() {
      @Override
      public ActionGroup getActionGroup(@NotNull EditorMouseEvent event) {
        return getPopupGroup(event);
      }
    });

    int bufferSize = ConsoleBuffer.useCycleBuffer() ? ConsoleBuffer.getCycleBufferSize() : 0;
    editor.getDocument().setCyclicBufferSize(bufferSize);
    editor.putUserData(CONSOLE_VIEW_IN_EDITOR_VIEW, this);
    editor.getSettings().setAllowSingleLogicalLineFolding(true); // We want to fold long soft-wrapped command lines
    return editor;
  }

  @NotNull
  protected EditorEx doCreateConsoleEditor() {
    return ConsoleViewUtil.setupConsoleEditor(myProject, true, false);
  }

  private void registerConsoleEditorActions(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_GOTO_DECLARATION).getShortcuts();
    CustomShortcutSet shortcutSet = new CustomShortcutSet(ArrayUtil.mergeArrays(shortcuts, CommonShortcuts.ENTER.getShortcuts()));
    new HyperlinkNavigationAction().registerCustomShortcutSet(shortcutSet, editor.getContentComponent());
    if (!myIsViewer) {
      new EnterHandler().registerCustomShortcutSet(CommonShortcuts.ENTER, editor.getContentComponent());
      registerActionHandler(editor, IdeActions.ACTION_EDITOR_PASTE, new PasteHandler());
      registerActionHandler(editor, IdeActions.ACTION_EDITOR_BACKSPACE, new DeleteBackspaceHandler(-1, IdeActions.ACTION_EDITOR_BACKSPACE));
      registerActionHandler(editor, IdeActions.ACTION_EDITOR_DELETE, new DeleteBackspaceHandler(0, IdeActions.ACTION_EDITOR_DELETE));
      registerActionHandler(editor, IdeActions.ACTION_EDITOR_TAB, new TabHandler());

      registerActionHandler(editor, EOFAction.ACTION_ID);
    }
  }

  private static void registerActionHandler(@NotNull Editor editor, @NotNull String actionId) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    AnAction action = ActionManager.getInstance().getAction(actionId);
    action.registerCustomShortcutSet(action.getShortcutSet(), editor.getContentComponent());
  }

  private static void registerActionHandler(@NotNull Editor editor, @NotNull String actionId, @NotNull AnAction action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    action.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), editor.getContentComponent());
  }

  @NotNull
  private ActionGroup getPopupGroup(@NotNull EditorMouseEvent event) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ActionManager actionManager = ActionManager.getInstance();
    HyperlinkInfo info = getHyperlinks() != null ? getHyperlinks().getHyperlinkInfoByEvent(event) : null;
    ActionGroup group = null;
    if (info instanceof HyperlinkWithPopupMenuInfo) {
      group = ((HyperlinkWithPopupMenuInfo)info).getPopupMenuGroup(event.getMouseEvent());
    }
    if (group == null) {
      group = (ActionGroup)actionManager.getAction(CONSOLE_VIEW_POPUP_MENU);
    }
    List<ConsoleActionsPostProcessor> postProcessors = ConsoleActionsPostProcessor.EP_NAME.getExtensionList();
    AnAction[] result = group.getChildren(null);

    for (ConsoleActionsPostProcessor postProcessor : postProcessors) {
      result = postProcessor.postProcessPopupActions(this, result);
    }
    return new DefaultActionGroup(result);
  }

  private void highlightHyperlinksAndFoldings(int startLine) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CompositeFilter compositeFilter = createCompositeFilter();
    boolean canHighlightHyperlinks = !compositeFilter.isEmpty();

    if (!canHighlightHyperlinks && !myUpdateFoldingsEnabled) {
      return;
    }
    Document document = getEditor().getDocument();
    if (document.getTextLength() == 0) return;

    int endLine = Math.max(0, document.getLineCount() - 1);

    if (canHighlightHyperlinks) {
      getHyperlinks().highlightHyperlinks(compositeFilter, startLine, endLine);
    }

    if (myAllowHeavyFilters && compositeFilter.isAnyHeavy() && compositeFilter.shouldRunHeavy()) {
      runHeavyFilters(compositeFilter, startLine, endLine);
    }
    if (myUpdateFoldingsEnabled) {
      updateFoldings(startLine, endLine);
    }
  }

  public void rehighlightHyperlinksAndFoldings() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isDisposed()) return;

    clearHyperlinkAndFoldings();
    highlightHyperlinksAndFoldings(0);
  }

  private void runHeavyFilters(@NotNull CompositeFilter compositeFilter, int line1, int endLine) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int startLine = Math.max(0, line1);

    Document document = getEditor().getDocument();
    int startOffset = document.getLineStartOffset(startLine);
    String text = document.getText(new TextRange(startOffset, document.getLineEndOffset(endLine)));
    Document documentCopy = new DocumentImpl(text,true);
    documentCopy.setReadOnly(true);

    myJLayeredPane.startUpdating();
    int currentValue = myHeavyUpdateTicket;
    myHeavyAlarm.addRequest(() -> {
      if (!compositeFilter.shouldRunHeavy()) return;
      try {
        compositeFilter.applyHeavyFilter(documentCopy, startOffset, startLine, additionalHighlight ->
          addFlushRequest(0, new FlushRunnable(true) {
            @Override
            public void doRun() {
              if (myHeavyUpdateTicket != currentValue) return;
              TextAttributes additionalAttributes = additionalHighlight.getTextAttributes(null);
              if (additionalAttributes != null) {
                ResultItem item = additionalHighlight.getResultItems().get(0);
                getHyperlinks().addHighlighter(item.getHighlightStartOffset(), item.getHighlightEndOffset(), additionalAttributes);
              }
              else {
                getHyperlinks().highlightHyperlinks(additionalHighlight, 0);
              }
            }
          })
        );
      }
      catch (IndexNotReadyException ignore) {
      }
      finally {
        if (myHeavyAlarm.getActiveRequestCount() <= 1) { // only the current request
          UIUtil.invokeLaterIfNeeded(() -> myJLayeredPane.finishUpdating());
        }
      }
    }, 0);
  }

  protected void updateFoldings(int startLine, int endLine) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Editor editor = getEditor();
    editor.getFoldingModel().runBatchFoldingOperation(() -> {
      Document document = editor.getDocument();

      FoldRegion existingRegion = null;
      if (startLine > 0) {
        int prevLineStart = document.getLineStartOffset(startLine - 1);
        FoldRegion[] regions = FoldingUtil.getFoldRegionsAtOffset(editor, prevLineStart);
        if (regions.length == 1) {
          existingRegion = regions[0];
        }
      }
      ConsoleFolding lastFolding = existingRegion == null ? null : findFoldingByRegion(existingRegion);
      int lastStartLine = Integer.MAX_VALUE;
      if (lastFolding != null) {
        int offset = existingRegion.getStartOffset();
        if (offset == 0) {
          lastStartLine = 0;
        }
        else {
          lastStartLine = document.getLineNumber(offset);
          if (document.getLineStartOffset(lastStartLine) != offset) lastStartLine++;
        }
      }

      List<ConsoleFolding> extensions = ContainerUtil.filter(ConsoleFolding.EP_NAME.getExtensionList(), cf -> cf.isEnabledForConsole(this));
      if (extensions.isEmpty()) return;

      for (int line = startLine; line <= endLine; line++) {
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
        ConsoleFolding next = line < endLine ? foldingForLine(extensions, line, document) : null;
        if (next != lastFolding) {
          if (lastFolding != null) {
            boolean isExpanded = false;
            if (line > startLine && existingRegion != null && lastStartLine < startLine) {
              isExpanded = existingRegion.isExpanded();
              editor.getFoldingModel().removeFoldRegion(existingRegion);
            }
            addFoldRegion(document, lastFolding, lastStartLine, line - 1, isExpanded);
          }
          lastFolding = next;
          lastStartLine = line;
          existingRegion = null;
        }
      }
    });
  }

  private static final Key<String> USED_FOLDING_FQN_KEY = Key.create("USED_FOLDING_KEY");

  private void addFoldRegion(@NotNull Document document, @NotNull ConsoleFolding folding, int startLine, int endLine, boolean isExpanded) {
    List<String> toFold = new ArrayList<>(endLine - startLine + 1);
    for (int i = startLine; i <= endLine; i++) {
      toFold.add(EditorHyperlinkSupport.getLineText(document, i, false));
    }

    int oStart = document.getLineStartOffset(startLine);
    if (oStart > 0 && folding.shouldBeAttachedToThePreviousLine()) oStart--;
    int oEnd = CharArrayUtil.shiftBackward(document.getImmutableCharSequence(), document.getLineEndOffset(endLine) - 1, " \t") + 1;

    String placeholder = folding.getPlaceholderText(getProject(), toFold);
    FoldRegion region = placeholder == null ? null : getEditor().getFoldingModel().addFoldRegion(oStart, oEnd, placeholder);
    if (region != null) {
      region.setExpanded(isExpanded);
      region.putUserData(USED_FOLDING_FQN_KEY, getFoldingFqn(folding));
    }
  }

  private ConsoleFolding findFoldingByRegion(@NotNull FoldRegion region) {
    String lastFoldingFqn = USED_FOLDING_FQN_KEY.get(region);
    if (lastFoldingFqn == null) return null;
    ConsoleFolding consoleFolding = ConsoleFolding.EP_NAME.getByKey(lastFoldingFqn, ConsoleViewImpl.class, ConsoleViewImpl::getFoldingFqn);
    return consoleFolding != null && consoleFolding.isEnabledForConsole(this) ? consoleFolding : null;
  }

  @NotNull
  private static String getFoldingFqn(@NotNull ConsoleFolding consoleFolding) {
    return consoleFolding.getClass().getName();
  }

  private @Nullable ConsoleFolding foldingForLine(@NotNull List<? extends ConsoleFolding> extensions, int line, @NotNull Document document) {
    String lineText = EditorHyperlinkSupport.getLineText(document, line, false);
    if (line == 0 && myCommandLineFolding.shouldFoldLine(myProject, lineText)) {
      return myCommandLineFolding;
    }

    for (ConsoleFolding extension : extensions) {
      if (extension.shouldFoldLine(myProject, lineText)) {
        return extension;
      }
    }

    return null;
  }

  private static class ClearThisConsoleAction extends ClearConsoleAction {
    private final ConsoleView myConsoleView;

    ClearThisConsoleAction(@NotNull ConsoleView consoleView) {
      myConsoleView = consoleView;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = myConsoleView.getContentSize() > 0;
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myConsoleView.clear();
    }
  }

  /**
   * @deprecated use {@link ClearConsoleAction} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static class ClearAllAction extends ClearConsoleAction {
  }

  private static final class MyTypedHandler extends TypedActionHandlerBase {
    private MyTypedHandler(@NotNull TypedActionHandler originalAction) {
      super(originalAction);
    }

    @Override
    public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
      ConsoleViewImpl consoleView = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW);
      if (consoleView == null || !consoleView.myState.isRunning() || consoleView.myIsViewer) {
        if (myOriginalHandler != null) {
          myOriginalHandler.execute(editor, charTyped, dataContext);
        }
        return;
      }
      String text = String.valueOf(charTyped);
      consoleView.type(editor, text);
    }
  }

  private void type(@NotNull Editor editor, @NotNull String text) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    flushDeferredText();
    SelectionModel selectionModel = editor.getSelectionModel();

    int lastOffset = selectionModel.hasSelection() ? selectionModel.getSelectionStart() : editor.getCaretModel().getOffset() - 1;
    RangeMarker marker = ConsoleTokenUtil.findTokenMarker(getEditor(), getProject(), lastOffset);
    if (marker == null || ConsoleTokenUtil.getTokenType(marker) != ConsoleViewContentType.USER_INPUT) {
      print(text, ConsoleViewContentType.USER_INPUT);
      moveScrollRemoveSelection(editor, editor.getDocument().getTextLength());
      return;
    }

    String textToUse = StringUtil.convertLineSeparators(text);
    int typeOffset;
    Document document = editor.getDocument();
    if (selectionModel.hasSelection()) {
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();
      document.deleteString(start, end);
      selectionModel.removeSelection();
      typeOffset = start;
      assert typeOffset <= document.getTextLength() : "typeOffset="+typeOffset+"; document.getTextLength()="+document.getTextLength()+"; sel start="+start+"; sel end="+end+"; document="+document.getClass();
    }
    else {
      typeOffset = editor.getCaretModel().getOffset();
      assert typeOffset <= document.getTextLength() : "typeOffset="+typeOffset+"; document.getTextLength()="+document.getTextLength()+"; caret model="+editor.getCaretModel();
    }
    insertUserText(typeOffset, textToUse);
  }

  private abstract static class ConsoleAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      DataContext context = e.getDataContext();
      ConsoleViewImpl console = getRunningConsole(context);
      if (console != null) {
        execute(console, context);
      }
    }

    protected abstract void execute(@NotNull ConsoleViewImpl console, @NotNull DataContext context);

    @Override
    public void update(@NotNull AnActionEvent e) {
      ConsoleViewImpl console = getRunningConsole(e.getDataContext());
      e.getPresentation().setEnabled(console != null);
    }

    @Nullable
    private static ConsoleViewImpl getRunningConsole(@NotNull DataContext context) {
      Editor editor = CommonDataKeys.EDITOR.getData(context);
      if (editor != null) {
        ConsoleViewImpl console = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW);
        if (console != null && console.myState.isRunning()) {
          return console;
        }
      }
      return null;
    }
  }

  private static class EnterHandler extends ConsoleAction {
    @Override
    public void execute(@NotNull ConsoleViewImpl consoleView, @NotNull DataContext context) {
      consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      consoleView.flushDeferredText();
      Editor editor = consoleView.getEditor();
      moveScrollRemoveSelection(editor, editor.getDocument().getTextLength());
    }
  }

  private static class PasteHandler extends ConsoleAction {
    @Override
    public void execute(@NotNull ConsoleViewImpl consoleView, @NotNull DataContext context) {
      String text = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
      if (text == null) return;
      Editor editor = consoleView.getEditor();
      consoleView.type(editor, text);
    }
  }

  private static class DeleteBackspaceHandler extends ConsoleAction {
    private final int myTextOffsetToDeleteRelativeToCaret;
    private final String myParentActionId;

    private DeleteBackspaceHandler(int textOffsetToDeleteRelativeToCaret, @NotNull String parentActionId) {
      myTextOffsetToDeleteRelativeToCaret = textOffsetToDeleteRelativeToCaret;
      myParentActionId = parentActionId;
    }

    @Override
    public void execute(@NotNull ConsoleViewImpl consoleView, @NotNull DataContext context) {
      Editor editor = consoleView.getEditor();

      if (IncrementalSearchHandler.isHintVisible(editor)) {
        getDefaultActionHandler(myParentActionId).execute(editor, null, context);
        return;
      }

      consoleView.flushDeferredText();
      Document document = editor.getDocument();
      int length = document.getTextLength();
      if (length == 0) {
        return;
      }

      SelectionModel selectionModel = editor.getSelectionModel();
      if (selectionModel.hasSelection()) {
        consoleView.deleteUserText(selectionModel.getSelectionStart(),
                                   selectionModel.getSelectionEnd() - selectionModel.getSelectionStart());
      }
      else {
        int offset = editor.getCaretModel().getOffset() + myTextOffsetToDeleteRelativeToCaret;
        if (offset >= 0) {
          consoleView.deleteUserText(offset, 1);
        }
      }
    }

    @NotNull
    private static EditorActionHandler getDefaultActionHandler(@NotNull String actionId) {
      return EditorActionManager.getInstance().getActionHandler(actionId);
    }
  }

  private static class TabHandler extends ConsoleAction {
    @Override
    protected void execute(@NotNull ConsoleViewImpl console, @NotNull DataContext context) {
      console.type(console.getEditor(), "\t");
    }
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusableComponent() {
    //ensure editor created
    getComponent();
    return getEditor().getContentComponent();
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
  protected OccurenceInfo calcNextOccurrence(int delta) {
    Editor editor = getEditor();
    if (isDisposed() || editor == null) {
      return null;
    }

    return EditorHyperlinkSupport.getNextOccurrence(editor, delta, next -> {
      int offset = next.getStartOffset();
      scrollTo(offset);
      HyperlinkInfo hyperlinkInfo = EditorHyperlinkSupport.getHyperlinkInfo(next);
      if (hyperlinkInfo instanceof BrowserHyperlinkInfo) {
        return;
      }
      if (hyperlinkInfo instanceof HyperlinkInfoBase) {
        VisualPosition position = editor.offsetToVisualPosition(offset);
        Point point = editor.visualPositionToXY(new VisualPosition(position.getLine() + 1, position.getColumn()));
        ((HyperlinkInfoBase)hyperlinkInfo).navigate(myProject, new RelativePoint(editor.getContentComponent(), point));
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

  @NotNull
  @Override
  public String getNextOccurenceActionName() {
    return ExecutionBundle.message("down.the.stack.trace");
  }

  @NotNull
  @Override
  public String getPreviousOccurenceActionName() {
    return ExecutionBundle.message("up.the.stack.trace");
  }

  public void addCustomConsoleAction(@NotNull AnAction action) {
    customActions.add(action);
  }

  @Override
  public AnAction @NotNull [] createConsoleActions() {
    //Initializing prev and next occurrences actions
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    AnAction prevAction = actionsManager.createPrevOccurenceAction(this);
    prevAction.getTemplatePresentation().setText(getPreviousOccurenceActionName());
    AnAction nextAction = actionsManager.createNextOccurenceAction(this);
    nextAction.getTemplatePresentation().setText(getNextOccurenceActionName());

    AnAction switchSoftWrapsAction = new ToggleUseSoftWrapsToolbarAction(SoftWrapAppliancePlaces.CONSOLE) {
      @Override
      protected Editor getEditor(@NotNull AnActionEvent e) {
        return ConsoleViewImpl.this.getEditor();
      }
    };
    AnAction autoScrollToTheEndAction = new ScrollToTheEndToolbarAction(getEditor());

    List<AnAction> consoleActions = new ArrayList<>();
    consoleActions.add(prevAction);
    consoleActions.add(nextAction);
    consoleActions.add(switchSoftWrapsAction);
    consoleActions.add(autoScrollToTheEndAction);
    consoleActions.add(ActionManager.getInstance().getAction("Print"));
    consoleActions.add(new ClearThisConsoleAction(this));
    consoleActions.addAll(customActions);
    List<ConsoleActionsPostProcessor> postProcessors = ConsoleActionsPostProcessor.EP_NAME.getExtensionList();
    AnAction[] result = consoleActions.toArray(AnAction.EMPTY_ARRAY);
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
  public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, () -> myListeners.remove(listener));
  }

  private void insertUserText(int offset, @NotNull String text) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Pair<String, ConsoleViewContentType>> result = myInputMessageFilter.applyFilter(text, ConsoleViewContentType.USER_INPUT);
    if (result == null) {
      doInsertUserInput(offset, text);
    }
    else {
      for (Pair<String, ConsoleViewContentType> pair : result) {
        String chunkText = pair.getFirst();
        ConsoleViewContentType chunkType = pair.getSecond();
        if (chunkType.equals(ConsoleViewContentType.USER_INPUT)) {
          doInsertUserInput(offset, chunkText);
          offset += chunkText.length();
        }
        else {
          print(chunkText, chunkType, null);
        }
      }
    }
  }

  private void doInsertUserInput(int offset, @NotNull String text) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Editor editor = getEditor();
    Document document = editor.getDocument();

    int oldDocLength = document.getTextLength();
    document.insertString(offset, text);
    int newStartOffset = Math.max(0,document.getTextLength() - oldDocLength + offset - text.length()); // take care of trim document
    int newEndOffset = document.getTextLength() - oldDocLength + offset; // take care of trim document

    if (ConsoleTokenUtil.findTokenMarker(getEditor(), getProject(), newEndOffset) == null) {
      ConsoleTokenUtil.createTokenRangeHighlighter(getEditor(), getProject(), ConsoleViewContentType.USER_INPUT, newStartOffset, newEndOffset);
    }

    moveScrollRemoveSelection(editor, newEndOffset);
    sendUserInput(text);
  }

  private static void moveScrollRemoveSelection(@NotNull Editor editor, int offset) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  private void deleteUserText(int startOffset, int length) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Editor editor = getEditor();
    Document document = editor.getDocument();

    RangeMarker marker = ConsoleTokenUtil.findTokenMarker(getEditor(), getProject(), startOffset);
    if (marker == null || ConsoleTokenUtil.getTokenType(marker) != ConsoleViewContentType.USER_INPUT) {
      return;
    }

    int endOffset = startOffset + length;
    if (startOffset >= 0 && endOffset >= 0 && endOffset > startOffset) {
      document.deleteString(startOffset, endOffset);
    }
    moveScrollRemoveSelection(editor, startOffset);
  }

  public boolean isRunning() {
    return myState.isRunning();
  }

  public void addNotificationComponent(@NotNull JComponent notificationComponent) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    add(notificationComponent, BorderLayout.NORTH);
  }

  @TestOnly
  @NotNull
  ConsoleState getState() {
    return myState;
  }

  /**
   * Command line used to launch application/test from idea may be quite long.
   * Hence, it takes many visual lines during representation if soft wraps are enabled
   * or, otherwise, takes many columns and makes horizontal scrollbar thumb too small.
   * <p/>
   * Our point is to fold such long command line and represent it as a single visual line by default.
   */
  private class CommandLineFolding extends ConsoleFolding {
    @Override
    public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
      return line.length() >= 1000 && myState.isCommandLine(line);
    }

    @Override
    public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
      String text = lines.get(0);

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
  }

  private class FlushRunnable implements Runnable {
    // Does request of this class was myFlushAlarm.addRequest()-ed but not yet executed
    private final AtomicBoolean requested = new AtomicBoolean();
    private final boolean adHoc; // true if requests of this class should not be merged (i.e they can be requested multiple times)

    private FlushRunnable(boolean adHoc) {
      this.adHoc = adHoc;
    }

    void queue(long delay) {
      if (myFlushAlarm.isDisposed()) return;
      if (adHoc || requested.compareAndSet(false, true)) {
        myFlushAlarm.addRequest(this, delay, getStateForUpdate());
      }
    }
    void clearRequested() {
      requested.set(false);
    }

    boolean hasRequested() {
      return requested.get();
    }

    @Override
    public final void run() {
      if (isDisposed()) return;
      // flush requires UndoManger/CommandProcessor properly initialized
      if (!StartupManagerEx.getInstanceEx(myProject).startupActivityPassed()) {
        addFlushRequest(DEFAULT_FLUSH_DELAY, FLUSH);
      }

      clearRequested();
      doRun();
    }

    protected void doRun() {
      flushDeferredText();
    }
  }

  private final FlushRunnable FLUSH = new FlushRunnable(false);

  private final class ClearRunnable extends FlushRunnable {
    private ClearRunnable() {
      super(false);
    }

    @Override
    public void doRun() {
      doClear();
    }
  }
  private final ClearRunnable CLEAR = new ClearRunnable();

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private class HyperlinkNavigationAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Runnable runnable = getHyperlinks().getLinkNavigationRunnable(getEditor().getCaretModel().getLogicalPosition());
      assert runnable != null;
      runnable.run();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(getHyperlinks().getLinkNavigationRunnable(getEditor().getCaretModel().getLogicalPosition()) != null);
    }
  }

  @NotNull
  public String getText() {
    return getEditor().getDocument().getText();
  }
}