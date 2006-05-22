package com.intellij.execution.impl;

import com.intellij.codeInsight.navigation.IncrementalSearchHandler;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataAccessor;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.actions.DiffActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Alarm;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public final class ConsoleViewImpl extends JPanel implements ConsoleView, DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.ConsoleViewImpl");

  private static final int FLUSH_DELAY = 200; //TODO : make it an option

  private static final Key<ConsoleViewImpl> CONSOLE_VIEW_IN_EDITOR_VIEW = Key.create("CONSOLE_VIEW_IN_EDITOR_VIEW");
  static {
    final EditorActionManager actionManager = EditorActionManager.getInstance();
    final TypedAction typedAction = actionManager.getTypedAction();
    typedAction.setupHandler(new MyTypedHandler(typedAction.getHandler()));
  }

  private static final Color BACKGROUND_COLOR = Color.white;
  private static final TextAttributes HYPERLINK_ATTRIBUTES = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);

  private final DisposedPsiManagerCheck myPsiDisposedCheck;
  private ConsoleState myState = ConsoleState.NOT_STARTED;
  private final int CYCLIC_BUFFER_SIZE = GeneralSettings.getInstance().getCyclicBufferSize();
  private final boolean USE_CYCLIC_BUFFER = GeneralSettings.getInstance().isUseCyclicBuffer();

  private static class TokenInfo{
    final ConsoleViewContentType contentType;
    int startOffset;
    int endOffset;
    final TextAttributes attributes;

    public TokenInfo(final ConsoleViewContentType contentType, final int startOffset, final int endOffset) {
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

  private String myHelpId;

  private Alarm myFlushAlarm = new Alarm();

  private final Runnable myFlushDeferredRunnable = new Runnable() {
    public void run() {
      if (myProject.isDisposed()) return;
      flushDeferredText();
    }
  };

  private CompositeFilter myMessageFilter = new CompositeFilter();

  public ConsoleViewImpl(final Project project) {
    super(new BorderLayout());
    myPsiDisposedCheck = new DisposedPsiManagerCheck(project);
    myProject = project;

    addMessageFilter(new ExceptionFilter(myProject));//TEMP!
  }

  public void attachToProcess(final ProcessHandler processHandler){
    myState = myState.attachTo(this, processHandler);
  }

  public void clear() {
    assertIsDispatchThread();

    synchronized(LOCK){
      myContentSize = 0;
      if (USE_CYCLIC_BUFFER) {
        myDeferredOutput = new StringBuffer(Math.min(myDeferredOutput.length(), CYCLIC_BUFFER_SIZE));
      }
      else {
        myDeferredOutput = new StringBuffer();
      }
      myDeferredUserInput = new StringBuffer();
      if (myEditor != null){
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            myHyperlinks.clear();
            myEditor.getMarkupModel().removeAllHighlighters();
            myTokens.clear();
            CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
              public void run() {
                myEditor.getDocument().deleteString(0, myEditor.getDocument().getTextLength());
              }
            }, null, null);
          }
        });
      }
    }
  }

  public void scrollTo(final int offset) {
    assertIsDispatchThread();
    if (myEditor == null) return;
    if (myState.isFinished() && !hasDeferredOutput()) {
      myEditor.getCaretModel().moveToOffset(offset);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
    else {
      myEditor.getScrollingModel().scrollTo(myEditor.offsetToLogicalPosition(offset), ScrollType.MAKE_VISIBLE);
    }
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
      new Alarm().addRequest(
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
      add(myEditor.getComponent(), BorderLayout.CENTER);

      myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          if (e.getNewLength() == 0) {
            // string has beeen removed, move tokens down
            synchronized (LOCK) {
              int offset = e.getOffset();
              int toRemoveLen = e.getOldLength();
              int tIndex = findTokenInfoIndexByOffset(offset+toRemoveLen);
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

  public void dispose(){
    myState = myState.dispose();
    if (myEditor != null){
      myFlushAlarm.cancelAllRequests();
      EditorFactory.getInstance().releaseEditor(myEditor);
      synchronized (LOCK) {
        myDeferredOutput = new StringBuffer();
      }
      myEditor = null;
    }
  }

  public void print(String s, final ConsoleViewContentType contentType) {
    synchronized(LOCK){
      s = StringUtil.convertLineSeparators(s,  "\n");
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
        myFlushAlarm.addRequest(myFlushDeferredRunnable, FLUSH_DELAY, ModalityState.stateForComponent(myEditor.getComponent()));
      }
    }
  }

  private void requestFlushImmediately() {
    if (myEditor != null) {
      myFlushAlarm.addRequest(new Runnable() {
        public void run() {
          flushDeferredText();
        }
      }, 0, ModalityState.stateForComponent(myEditor.getComponent()));
    }
  }

  public int getContentSize() { return myContentSize; }

  public boolean canPause() {
    return true;
  }

  private void flushDeferredText() {
    ApplicationManager.getApplication().assertIsDispatchThread();

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
    final boolean isAtEndOfDocument = myEditor.getCaretModel().getOffset() == myEditor.getDocument().getTextLength();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            document.insertString(document.getTextLength(), text);
          }
        }, null, null);
      }
    });
    final int newLineCount = document.getLineCount();
    if (oldLineCount < newLineCount) {
      myPsiDisposedCheck.performCheck();
      highlightHyperlinks(oldLineCount - 1, newLineCount - 2);
    }

    if (isAtEndOfDocument) {
      myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
      myEditor.getSelectionModel().removeSelection();
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  private void flushDeferredUserInput() {
    if (myState.isRunning()){
      final String text = myDeferredUserInput.substring(0, myDeferredUserInput.length());
      final int index = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));
      if (index < 0) return;
      try{
        myState.sendUserInput(text.substring(0, index + 1));
      }
      catch(IOException e){
        return;
      }
      myDeferredUserInput.setLength(0);
      myDeferredUserInput.append(text.substring(index + 1));
    }
  }

  public Object getData(final String dataId) {
    if (DataConstants.NAVIGATABLE.equals(dataId)){
      if (myEditor == null) {
        return null;
      }
      final LogicalPosition pos = myEditor.getCaretModel().getLogicalPosition();
      final HyperlinkInfo info = getHyperlinkInfoByLineAndCol(pos.line, pos.column);
      final OpenFileDescriptor openFileDescriptor = info instanceof OpenFileHyperlinkInfo ? ((OpenFileHyperlinkInfo)info).getDescriptor() : null;
      if (openFileDescriptor == null || !openFileDescriptor.getFile().isValid()) {
        return null;
      }
      return openFileDescriptor;
    }

    if (DataConstants.EDITOR.equals(dataId)) {
      return myEditor;
    }
    if (DataConstants.HELP_ID.equals(dataId)) {
      return myHelpId;
    }
    return null;
  }

  public void setHelpId(final String helpId) {
    myHelpId = helpId;
  }

  public void addMessageFilter(final Filter filter) {
    myMessageFilter.addFilter(filter);
  }

  public void printHyperlink(final String hyperlinkText, final HyperlinkInfo info) {
    if (myEditor == null) return;
    print(hyperlinkText, ConsoleViewContentType.NORMAL_OUTPUT);
    flushDeferredText();
    final int textLength = myEditor.getDocument().getTextLength();
    addHyperlink(textLength - hyperlinkText.length(), textLength, null, info);
  }

  private Editor createEditor() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Editor>() {
      public Editor compute() {
        final EditorFactory editorFactory = EditorFactory.getInstance();
        final Document editorDocument = editorFactory.createDocument("");

        final int bufferSize = USE_CYCLIC_BUFFER ? CYCLIC_BUFFER_SIZE : 0;
        editorDocument.setCyclicBufferSize(bufferSize);

        final EditorEx editor = (EditorEx) editorFactory.createViewer(editorDocument,myProject);
        final EditorHighlighter highlighter = new MyHighghlighter();
        editor.setHighlighter(highlighter);
        editor.putUserData(CONSOLE_VIEW_IN_EDITOR_VIEW, ConsoleViewImpl.this);

        final EditorSettings editorSettings = editor.getSettings();
        editorSettings.setLineMarkerAreaShown(false);
        editorSettings.setLineNumbersShown(false);
        editorSettings.setFoldingOutlineShown(false);
        editorSettings.setAdditionalPageAtBottom(false);

        final EditorColorsScheme scheme = editor.getColorsScheme();
        editor.setBackgroundColor(BACKGROUND_COLOR);
        scheme.setColor(EditorColors.CARET_ROW_COLOR, null);
        scheme.setColor(EditorColors.RIGHT_MARGIN_COLOR, null);

        editor.addEditorMouseListener(new EditorPopupHandler(){
          public void invokePopup(final EditorMouseEvent event) {
            final MouseEvent mouseEvent = event.getMouseEvent();
            popupInvoked(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
          }
        });

        editor.addEditorMouseListener(
          new EditorMouseAdapter(){
            public void mouseReleased(final EditorMouseEvent e){
              final MouseEvent mouseEvent = e.getMouseEvent();
              if (!mouseEvent.isPopupTrigger()){
                navigate(e);
              }
            }
          }
        );

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

        setEditorUpActions(editor);

        return editor;
      }
    });
  }

  private static void setEditorUpActions(final Editor editor) {
    new EnterHandler().registerCustomShortcutSet(CommonShortcuts.ENTER, editor.getContentComponent());
    registerActionHandler(editor, IdeActions.ACTION_EDITOR_PASTE, new PasteHandler());
    registerActionHandler(editor, IdeActions.ACTION_EDITOR_BACKSPACE, new BackSpaceHandler());
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
    }
  }

  private HyperlinkInfo getHyperlinkInfoByPoint(final Point p){
    if (myState == ConsoleState.NOT_STARTED) return null;
    final LogicalPosition pos = myEditor.xyToLogicalPosition(new Point(p.x, p.y));
    return getHyperlinkInfoByLineAndCol(pos.line, pos.column);
  }

  private HyperlinkInfo getHyperlinkInfoByLineAndCol(final int line, final int col) {
    final int offset = myEditor.logicalPositionToOffset(new LogicalPosition(line, col));
    return myHyperlinks.getHyperlinkAt(offset);
  }

  private void highlightHyperlinks(final int line1, final int line2){
    if (myMessageFilter != null){
      ApplicationManager.getApplication().assertIsDispatchThread();
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      final Document document = myEditor.getDocument();
      final CharSequence chars = document.getCharsSequence();
      for(int line = line1; line <= line2; line++){
        if (line < 0) continue;
        final int startOffset = document.getLineStartOffset(line);
        int endOffset = document.getLineEndOffset(line);
        if (endOffset < document.getTextLength()){
          endOffset++; // add '\n'
        }
        final String text = chars.subSequence(startOffset, endOffset).toString();
        final Filter.Result result = myMessageFilter.applyFilter(text, endOffset);
        if (result != null){
          final int highlightStartOffset = result.highlightStartOffset;
          final int highlightEndOffset = result.highlightEndOffset;
          final HyperlinkInfo hyperlinkInfo = result.hyperlinkInfo;
          addHyperlink(highlightStartOffset, highlightEndOffset, result.highlightAttributes, hyperlinkInfo);
        }
      }
    }
  }

  private void addHyperlink(final int highlightStartOffset,
                            final int highlightEndOffset,
                            final TextAttributes highlightAttributes,
                            final HyperlinkInfo hyperlinkInfo) {
    TextAttributes textAttributes = highlightAttributes != null ? highlightAttributes : HYPERLINK_ATTRIBUTES;
    final RangeHighlighter highlighter = myEditor.getMarkupModel().addRangeHighlighter(highlightStartOffset,
                                                                                       highlightEndOffset,
                                                                                       HighlighterLayer.SELECTION - 1,
                                                                                       textAttributes,
                                                                                       HighlighterTargetArea.EXACT_RANGE);
    myHyperlinks.add(highlighter, hyperlinkInfo);
  }

  private class ClearAllAction extends AnAction{
    public ClearAllAction(){
      super(ExecutionBundle.message("clear.all.from.console.action.name"));
    }

    public void actionPerformed(final AnActionEvent e){
      clear();
    }
  }

  private class CopyAction extends AnAction{
    public CopyAction(){
      super(myEditor.getSelectionModel().hasSelection() ? ExecutionBundle.message("copy.selected.content.action.name") : ExecutionBundle.message("copy.content.action.name"));
    }

    public void actionPerformed(final AnActionEvent e){
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

  private class MyHighghlighter extends DocumentAdapter implements EditorHighlighter {
    private boolean myHasEditor;

    public HighlighterIterator createIterator(final int startOffset) {
      final int startIndex = findTokenInfoIndexByOffset(startOffset);

      return new HighlighterIterator(){
        private int myIndex = startIndex;

        public TextAttributes getTextAttributes() {
          return getTokenInfo().attributes;
        }

        public int getStart() {
          return getTokenInfo().startOffset;
        }

        public int getEnd() {
          return getTokenInfo().endOffset;
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

        private TokenInfo getTokenInfo() {
          return myTokens.get(myIndex);
        }
      };
    }

    public void setText(final CharSequence text) {
    }

    public void setEditor(final Editor editor) {
      LOG.assertTrue(!myHasEditor, "Highlighters cannot be reused with different editors");
      myHasEditor = true;
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
    private TypedActionHandler myOriginalHandler;

    public MyTypedHandler(final TypedActionHandler originalAction) {
      myOriginalHandler = originalAction;
    }

    public void execute(final Editor editor, final char charTyped, final DataContext dataContext) {
      final ConsoleViewImpl consoleView = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW);
      if (consoleView == null || !consoleView.myState.isRunning()){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      else{
        final String s = String.valueOf(charTyped);
        consoleView.print(s, ConsoleViewContentType.USER_INPUT);
        consoleView.flushDeferredText();
      }
    }
  }

  private static final DataAccessor<ConsoleViewImpl> CONSOLE = new DataAccessor<ConsoleViewImpl>() {
    public ConsoleViewImpl getImpl(final DataContext dataContext) throws NoDataException {
      return EDITOR.getNotNull(dataContext).getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW);
    }
  };

  private static final Condition<ConsoleViewImpl> CONSOLE_IS_RUNNING = new Condition<ConsoleViewImpl>() {
    public boolean value(final ConsoleViewImpl consoleView) {
      return consoleView.myState.isRunning();
    }
  };

  private static final DataAccessor<ConsoleViewImpl> RUNNINT_CONSOLE =DataAccessor.createConditionalAccessor(CONSOLE, CONSOLE_IS_RUNNING);

  private static abstract class ConsoleAction extends AnAction {
    public void actionPerformed(final AnActionEvent e) {
      final DataContext context = e.getDataContext();
      final ConsoleViewImpl console = RUNNINT_CONSOLE.from(context);
      execute(console, context);
    }

    protected abstract void execute(ConsoleViewImpl console, final DataContext context);

    public void update(final AnActionEvent e) {
      final ConsoleViewImpl console = RUNNINT_CONSOLE.from(e.getDataContext());
      e.getPresentation().setEnabled(console != null);
    }
  }

  private static class EnterHandler extends ConsoleAction {
    public void execute(final ConsoleViewImpl consoleView, final DataContext context) {
      consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      consoleView.flushDeferredText();
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
        consoleView.myEditor.getComponent().getToolkit().beep();
      }
      if (s == null) return;
      consoleView.print(s, ConsoleViewContentType.USER_INPUT);
      consoleView.flushDeferredText();
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

      synchronized(consoleView.LOCK){
        if (consoleView.myTokens.size() == 0) return;
        final TokenInfo info = consoleView.myTokens.get(consoleView.myTokens.size() - 1);
        if (info.contentType != ConsoleViewContentType.USER_INPUT) return;
        if (consoleView.myDeferredUserInput.length() == 0) return;

        consoleView.myDeferredUserInput.setLength(consoleView.myDeferredUserInput.length() - 1);
        info.endOffset -= 1;
        if (info.startOffset == info.endOffset){
          consoleView.myTokens.remove(consoleView.myTokens.size() - 1);
        }
        consoleView.myContentSize--;
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          document.deleteString(length - 1, length);
          editor.getCaretModel().moveToOffset(length - 1);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
        }
      });
    }

    private static EditorActionHandler getDefaultActionHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
    }
  }

  private static class Hyperlinks {
    private static final int NO_INDEX = Integer.MIN_VALUE;
    private final Map<RangeHighlighter,HyperlinkInfo> myHighlighterToMessageInfoMap = new HashMap<RangeHighlighter, HyperlinkInfo>();
    private int myLastIndex = NO_INDEX;

    public void clear() {
      myHighlighterToMessageInfoMap.clear();
      myLastIndex = NO_INDEX;
    }

    public HyperlinkInfo getHyperlinkAt(final int offset) {
      for (final RangeHighlighter highlighter : myHighlighterToMessageInfoMap.keySet()) {
        if (containsOffset(offset, highlighter)) {
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
  }
}
