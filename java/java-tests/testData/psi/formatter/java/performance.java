package com.intellij.openapi.editor.impl;

import com.intellij.Patches;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.impl.event.MarkupModelEvent;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JScrollPane2;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TooManyListenersException;

public class EditorImpl implements EditorEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorImpl");
  private static final Key DND_COMMAND_KEY = Key.create("DndCommand");
  private final DocumentImpl myDocument;

  private JPanel myPanel;
  private JScrollPane myScrollPane;
  private EditorComponentImpl myEditorComponent;
  private EditorGutterComponentImpl myGutterComponent;


  private CommandProcessor myCommandProcessor;
  private MyScrollBar myVerticalScrollBar;
  private MyScrollBar myHorizontalScrollBar;

  private ArrayList<EditorMouseListener> myMouseListeners = new ArrayList<EditorMouseListener>();
  private ArrayList<EditorMouseMotionListener> myMouseMotionListeners = new ArrayList<EditorMouseMotionListener>();

  private int myCharHeight = -1;
  private int myLineHeight = -1;
  private int myDescent = -1;

  private boolean myIsInsertMode = true;

  private final CaretCursor myCaretCursor;
  private final ScrollingTimer myScrollingTimer = new ScrollingTimer();

  private final Object MOUSE_DRAGGED_GROUP = Key.create("MouseDraggedGroup");
  private final THashMap<Object, Object> myUserDataMap = new THashMap<Object, Object>();

  private final DocumentListener myEditorDocumentAdapter;

  private SettingsImpl mySettings;

  private boolean isReleased = false;

  private MouseEvent myMousePressedEvent = null;

  private int mySavedSelectionStart = -1;
  private int mySavedSelectionEnd = -1;
  private int myLastColumnNumber = 0;

  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private MyEditable myEditable;

  private EditorColorsScheme myScheme;
  private final boolean myIsViewer;
  private final SelectionModelImpl mySelectionModel;
  private final EditorMarkupModelImpl myMarkupModel;
  private final FoldingModelImpl myFoldingModel;
  private final ScrollingModelImpl myScrollingModel;
  private final CaretModelImpl myCaretModel;

  private static final RepaintCursorThread ourCaretThread;

//  private final BorderEffect myBorderEffect = new BorderEffect();

  private int myMouseSelectionState = MOUSE_SELECTION_STATE_NONE;
  private FoldRegion myMouseSelectedRegion = null;

  private static final int MOUSE_SELECTION_STATE_NONE = 0;
  private static final int MOUSE_SELECTION_STATE_WORD_SELECTED = 1;
  private static final int MOUSE_SELECTION_STATE_LINE_SELECTED = 2;

  private final MarkupModelListener myMarkupModelListener;

  private EditorHighlighter myHighlighter;

  private int myScrollbarOrientation;
  private boolean myMousePressedInsideSelection;
  private FontMetrics myPlainFontMetrics;
  private FontMetrics myBoldFontMetrics;
  private FontMetrics myItalicFontMetrics;
  private FontMetrics myBoldItalicFontMetrics;

  private static final int CACHED_CHARS_BUFFER_SIZE = 300;

  private CachedFontContent myPlainCache = new CachedFontContent(Font.PLAIN);
  private CachedFontContent myBoldCache = new CachedFontContent(Font.BOLD);
  private CachedFontContent myBoldItalicCache = new CachedFontContent(Font.BOLD + Font.ITALIC);
  private CachedFontContent myItalicCache = new CachedFontContent(Font.ITALIC);

  private int myCurrentFontType = Font.PLAIN;

  private boolean myIsBlockSelectionMode;
  private EditorSizeContainer mySizeContainer = new EditorSizeContainer();

  private Runnable myCursorUpdater;
  private int myCaretUpdateVShift;
  Project myProject;
  private long myMouseSelectionChangeTimestamp;
  private int mySavedCaretOffsetForDNDUndoHack;
  private ArrayList<FocusChangeListener> myFocusListeners = new ArrayList<FocusChangeListener>();

  private MyInputMethodHandler myInputMethodRequestsHandler;
  private InputMethodRequests myInputMethodRequestsSwingWrapper;
  private boolean myIsOneLineMode;
  private boolean myIsRendererMode;
  private VirtualFile myVirtualFile;
  private boolean myIsColumnMode = false;
  private Color myForcedBackground = null;
  private Dimension myPreferredSize;
  private Runnable myGutterSizeUpdater = null;
  private Alarm myAppleRepaintAlarm;

  static {
    ourCaretThread = new RepaintCursorThread();
    ourCaretThread.start();
  }


  public EditorImpl(Document document, boolean viewer, Project project) {
    myProject = project;
    myDocument = (DocumentImpl)document;
    myScheme = new MyColorSchemeDelegate();
    myIsViewer = viewer;
    mySettings = new SettingsImpl(this);

    mySelectionModel = new SelectionModelImpl(this);
    myMarkupModel = new EditorMarkupModelImpl(this);
    myFoldingModel = new FoldingModelImpl(this);
    myCaretModel = new CaretModelImpl(this);
    myIsBlockSelectionMode = false;
    mySizeContainer.reset();

    myCommandProcessor = CommandProcessor.getInstance();

    myEditorDocumentAdapter = new EditorDocumentAdapter();

    myMarkupModelListener = new MarkupModelListener() {
      public void rangeHighlighterChanged(MarkupModelEvent event) {
        RangeHighlighterImpl rangeHighlighter = (RangeHighlighterImpl)event.getHighlighter();
        if (rangeHighlighter.isValid()) {
          int start = rangeHighlighter.getAffectedAreaStartOffset();
          int end = rangeHighlighter.getAffectedAreaEndOffset();
          int startLine = myDocument.getLineNumber(start);
          int endLine = myDocument.getLineNumber(end);
          repaintLines(Math.max(0, startLine - 1), Math.min(endLine + 1, getDocument().getLineCount()));
        }
        else {
          repaint(0, getDocument().getTextLength());
        }
        ((EditorMarkupModelImpl)getMarkupModel()).repaint();
        ((EditorMarkupModelImpl)getMarkupModel()).markDirtied();
        GutterIconRenderer renderer = rangeHighlighter.getGutterIconRenderer();
        if (renderer != null) {
          updateGutterSize();
        }
        updateCaretCursor();
      }
    };

    ((MarkupModelImpl)myDocument.getMarkupModel(myProject)).addMarkupModelListener(myMarkupModelListener);
    ((MarkupModelImpl)getMarkupModel()).addMarkupModelListener(myMarkupModelListener);

    myDocument.addDocumentListener(myFoldingModel);
    myDocument.addDocumentListener(myCaretModel);
    myDocument.addDocumentListener(mySelectionModel);
    myDocument.addDocumentListener(myEditorDocumentAdapter);

    myCaretCursor = new CaretCursor();

    myFoldingModel.flushCaretShift();
    myScrollbarOrientation = EditorEx.VERTICAL_SCROLLBAR_RIGHT;

    EditorHighlighter highlighter = new EmptyEditorHighlighter(myScheme.getAttributes(HighlighterColors.TEXT));
    setHighlighter(highlighter);

    initComponent();

    myScrollingModel = new ScrollingModelImpl(this);

    myGutterComponent.updateSize();
    myEditorComponent.setSize(getContentSize());

    Dimension preferredSize = getPreferredSize();
    myScrollPane.getViewport().setViewSize(preferredSize);

    if (Patches.APPLE_BUG_ID_3716835) {
      myScrollingModel.addVisibleAreaListener(new VisibleAreaListener() {
        public void visibleAreaChanged(VisibleAreaEvent e) {
          if (myAppleRepaintAlarm == null) {
            myAppleRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
          }
          myAppleRepaintAlarm.cancelAllRequests();
          myAppleRepaintAlarm.addRequest(new Runnable() {
            public void run() {
              repaint(0, myDocument.getTextLength());
            }
          }, 50, ModalityState.stateForComponent(myEditorComponent));
        }
      });
    }

    updateCaretCursor();
  }

  public boolean isViewer() {
    return myIsViewer || myIsRendererMode;
  }

  public boolean isRendererMode() {
    return myIsRendererMode;
  }

  public void setRendererMode(boolean isRendererMode) {
    myIsRendererMode = isRendererMode;
  }

  public void setFile(VirtualFile vFile) {
    myVirtualFile = vFile;
    reinitSettings();
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  public MarkupModel getMarkupModel() {
    return myMarkupModel;
  }

  public FoldingModel getFoldingModel() {
    return myFoldingModel;
  }

  public CaretModel getCaretModel() {
    return myCaretModel;
  }

  public ScrollingModel getScrollingModel() {
    return myScrollingModel;
  }

  public EditorSettings getSettings() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return mySettings;
  }

  public void reinitSettings() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myCharHeight = -1;
    myLineHeight = -1;
    myDescent = -1;
    myPlainFontMetrics = null;

    myPlainCache.reinitSettings();
    myBoldCache.reinitSettings();
    myItalicCache.reinitSettings();
    myBoldItalicCache.reinitSettings();

    myCaretModel.reinitSettings();
    mySelectionModel.reinitSettings();
    mySettings.reinitSettings();
    ourCaretThread.setBlinkCaret(mySettings.isBlinkCaret());
    ourCaretThread.setBlinkPeriod(mySettings.getCaretBlinkPeriod());
    mySizeContainer.reset();
    myFoldingModel.refreshSettings();
    myFoldingModel.rebuild();

    myHighlighter.setColorScheme(myScheme);

    myGutterComponent.revalidate();
    myEditorComponent.repaint();

    updateCaretCursor();
  }

  public void release() {
    isReleased = true;
    myDocument.removeDocumentListener(myHighlighter);
    myDocument.removeDocumentListener(myEditorDocumentAdapter);
    myDocument.removeDocumentListener(myFoldingModel);
    myDocument.removeDocumentListener(myCaretModel);
    myDocument.removeDocumentListener(mySelectionModel);
    MarkupModelImpl markupModel = (MarkupModelImpl)myDocument.getMarkupModel(myProject, false);
    if (markupModel != null) markupModel.removeMarkupModelListener(myMarkupModelListener);
    myMarkupModel.dispose();
    myLineHeight = -1;
    myCharHeight = -1;
    myDescent = -1;
    myPlainFontMetrics = null;
    myScrollingModel.dispose();
    myGutterComponent.dispose();
  }

  public <T> void putUserData(Key<T> key, T value) {
    if (value != null) {
      myUserDataMap.put(key, value);
    }
    else {
      myUserDataMap.remove(key);
    }
  }

  public <T> T getUserData(Key<T> key) {
    return (T)myUserDataMap.get(key);
  }

  private void initComponent() {
    myEditorComponent = new EditorComponentImpl(this);
//    myStatusBar = new EditorStatusBarImpl();

    myScrollPane = new JScrollPane2() {
      protected void processMouseWheelEvent(MouseWheelEvent e) {
        if (mySettings.isWheelFontChangeEnabled()) {
          boolean changeFontSize = SystemInfo.isMac
                                   ? !e.isControlDown() && e.isMetaDown() && !e.isAltDown() && !e.isShiftDown()
                                   : e.isControlDown() && !e.isMetaDown() && !e.isAltDown() && !e.isShiftDown();
          if (changeFontSize) {
            setFontSize(myScheme.getEditorFontSize() + e.getWheelRotation());
            return;
          }
        }

        super.processMouseWheelEvent(e);
      }
    };
    myPanel = new JPanel() {
      public void addNotify() {
        super.addNotify();
        if (((JComponent)getParent()).getBorder() != null) myScrollPane.setBorder(null);
      }
    };

    //myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));
    myPanel.setLayout(new BorderLayout());

    myVerticalScrollBar = new MyScrollBar(JScrollBar.VERTICAL);
    myHorizontalScrollBar = new MyScrollBar(JScrollBar.HORIZONTAL);

    myGutterComponent = new EditorGutterComponentImpl(this);
    myGutterComponent.setOpaque(true);

    myScrollPane.setVerticalScrollBar(myVerticalScrollBar);
    myScrollPane.setHorizontalScrollBar(myHorizontalScrollBar);
    myScrollPane.setViewportView(myEditorComponent);
    //myScrollPane.setBorder(null);
    myScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    myScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);


    myScrollPane.setRowHeaderView(myGutterComponent);
    stopOptimizedScrolling();

    myEditorComponent.setTransferHandler(new MyTransferHandler());
    myEditorComponent.setAutoscrolls(true);

/*  Default mode till 1.4.0
 *   myScrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
 */
    myPanel.add(myScrollPane);

    myEditorComponent.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent event) {
        if (Patches.APPLE_BUG_ID_3337563) return; // Everything is going through InputMethods under MacOS X in JDK releases earlier than 1.4.2_03-117.1
        if (event.isConsumed()) {
          return;
        }
        if (processKeyTyped(event)) {
          event.consume();
        }
      }
    });

    MyMouseAdapter mouseAdapter = new MyMouseAdapter();
    myEditorComponent.addMouseListener(mouseAdapter);
    myGutterComponent.addMouseListener(mouseAdapter);

    MyMouseMotionListener mouseMotionListener = new MyMouseMotionListener();
    myEditorComponent.addMouseMotionListener(mouseMotionListener);
    myGutterComponent.addMouseMotionListener(mouseMotionListener);

    myEditorComponent.addFocusListener(new FocusAdapter() {
      public void focusGained(FocusEvent e) {
        myCaretCursor.activate();
        int caretLine = getCaretModel().getLogicalPosition().line;
        repaintLines(caretLine, caretLine);
        fireFocusGained();
      }

      public void focusLost(FocusEvent e) {
        synchronized (ourCaretThread) {
          if (ourCaretThread.myEditor == EditorImpl.this) {
            ourCaretThread.myEditor = null;
          }
        }
        int caretLine = getCaretModel().getLogicalPosition().line;
        repaintLines(caretLine, caretLine);
        fireFocusLost();
      }
    });

//    myBorderEffect.reset();

    try {
      myEditorComponent.getDropTarget().addDropTargetListener(new DropTargetAdapter() {
        public void drop(DropTargetDropEvent dtde) {
        }

        public void dragOver(DropTargetDragEvent dtde) {
          Point location = dtde.getLocation();

          moveCaretToScreenPos(location.x, location.y);
          getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      });
    }
    catch (TooManyListenersException e) {
    }
  }

  public void setFontSize(final int fontSize) {
    int oldFontSize = myScheme.getEditorFontSize();
    myScheme.setEditorFontSize(fontSize);
    myPropertyChangeSupport.firePropertyChange(EditorEx.PROP_FONT_SIZE, oldFontSize, fontSize);
  }

  private void processKeyTyped(char c) {
    // [vova] This is patch for Mac OS X. Under Mac "input methods"
    // is handled before our EventQueue consume upcoming KeyEvents.
    IdeEventQueue queue = IdeEventQueue.getInstance();
    if (queue.isWaitingForSecondKeyStroke() || ProgressManager.getInstance().hasModalProgressIndicator()) {
      return;
    }
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    DataContext dataContext = getDataContext();
    actionManager.fireBeforeEditorTyping(c, dataContext);
    EditorActionManager.getInstance().getTypedAction().actionPerformed(this, c, dataContext);
  }

  private void fireFocusLost() {
    FocusChangeListener[] listeners = getFocusListeners();
    for (int i = 0; i < listeners.length; i++) {
      FocusChangeListener listener = listeners[i];
      listener.focusLost(this);
    }
  }

  private FocusChangeListener[] getFocusListeners() {
    return myFocusListeners.toArray(new FocusChangeListener[myFocusListeners.size()]);
  }

  private void fireFocusGained() {
    FocusChangeListener[] listeners = getFocusListeners();
    for (int i = 0; i < listeners.length; i++) {
      FocusChangeListener listener = listeners[i];
      listener.focusGained(this);
    }
  }

  public void setHighlighter(EditorHighlighter highlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myHighlighter != null) {
      getDocument().removeDocumentListener(myHighlighter);
    }

    getDocument().addDocumentListener(highlighter);
    highlighter.setText(getDocument().getCharsSequence());
    myHighlighter = highlighter;
    myHighlighter.setEditor(this);
    if (myPanel != null) {
      reinitSettings();
    }
  }

  public EditorHighlighter getHighlighter() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myHighlighter;
  }

  public JComponent getContentComponent() {
    return myEditorComponent;
  }

  public EditorGutterComponentEx getGutterComponentEx() {
    return myGutterComponent;
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public void setInsertMode(boolean mode) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean oldValue = myIsInsertMode;
    myIsInsertMode = mode;
    myPropertyChangeSupport.firePropertyChange(EditorEx.PROP_INSERT_MODE, oldValue, mode);
    //Repaint the caret line by moving caret to the same place
    LogicalPosition caretPosition = getCaretModel().getLogicalPosition();
    getCaretModel().moveToLogicalPosition(caretPosition);
  }

  public boolean isInsertMode() {
    return myIsInsertMode;
  }

  public void setColumnMode(boolean mode) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean oldValue = myIsColumnMode;
    myIsColumnMode = mode;
    myPropertyChangeSupport.firePropertyChange(PROP_COLUMN_MODE, oldValue, mode);
  }

  public boolean isColumnMode() {
    return myIsColumnMode;
  }

  public void setBlockSelectionMode(boolean isBlockSelectionMode) {
    myIsBlockSelectionMode = isBlockSelectionMode;
  }

  public boolean isBlockSelectionMode() {
    return myIsBlockSelectionMode;
  }

  private int yPositionToVisibleLineNumber(int y) {
    return y / getLineHeight();
  }

  public int getSpaceWidth(int  fontType) {
    int width = charWidth(fontType, ' ');
    return width > 0 ? width : 1;
  }

  public VisualPosition xyToVisualPosition(Point p) {
    int line = yPositionToVisibleLineNumber(p.y);

    int x = 0;
    int offset = logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(line, 0)));
    int textLength = myDocument.getTextLength();

    if (offset >= textLength) return new VisualPosition(line, 0);

    int column = 0;
    int prevX = 0;
    CharSequence text = myDocument.getCharsNoThreadCheck();
    char c = ' ';
    IterationState state = new IterationState(this, offset, false);

    int fontType = state.getMergedAttributes().getFontType();
    int spaceSize = getSpaceWidth(fontType);

    outer:
        while (true) {
          if (offset >= textLength) break;

          if (offset >= state.getEndOffset()) {
            state.advance();
            fontType = state.getMergedAttributes().getFontType();
          }

          FoldRegion region = state.getCurrentFold();
          if (region != null) {
            char[] placeholder = region.getPlaceholderText().toCharArray();
            for (int j = 0; j < placeholder.length; j++) {
              c = placeholder[j];
              x += charWidth(fontType, c);
              if (x >= p.x) break outer;
              column++;
            }
            offset = region.getEndOffset();
          }
          else {
            prevX = x;
            c = text.charAt(offset);
            if (c == '\n') {
              break;
            }
            if (c == '\t') {
              x = nextTabStop(x);
            }
            else {
              x += charWidth(fontType, c);
            }

            if (x >= p.x) break;

            if (c == '\t') {
              column += (x - prevX) / spaceSize;
            }
            else {
              column++;
            }

            offset++;
          }
        }

    int charWidth = charWidth(fontType, c);

    if (x >= p.x && c == '\t') {
      if (mySettings.isCaretInsideTabs()) {
        column += (p.x - prevX) / spaceSize;
        if ((p.x - prevX) % spaceSize > spaceSize / 2) column++;
      }
      else {
        if ((x - p.x) * 2 < x - prevX) {
          column += (x - prevX) / spaceSize;
        }
      }
    }
    else {
      if (x >= p.x) {
        if ((x - p.x) * 2 < charWidth) column++;
      }
      else {
        column += (p.x - x) / getSpaceWidth(fontType);
      }
    }

    return new VisualPosition(line, column);
  }

  public VisualPosition offsetToVisualPosition(int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  public LogicalPosition offsetToLogicalPosition(int offset) {
    int line = calcLogicalLineNumber(offset);
    int column = calcColumnNumber(offset, line);
    return new LogicalPosition(line, column);
  }

  public LogicalPosition xyToLogicalPosition(Point p) {
    final Point pp;
    if (p.x >= 0 && p.y >= 0) {
      pp = p;
    }
    else {
      pp = new Point(Math.max(p.x, 0), Math.max(p.y, 0));
    }

    return visualToLogicalPosition(xyToVisualPosition(pp));
  }

  public Point logicalPositionToXY(LogicalPosition pos) {
    VisualPosition visible = logicalToVisualPosition(pos);
    int y = visibleLineNumberToYPosition(visible.line);

    int lineStartOffset;

    if (pos.line == 0) {
      lineStartOffset = 0;
    }
    else {
      if (pos.line >= myDocument.getLineCount()) {
        lineStartOffset = myDocument.getTextLength();
      }
      else {
        lineStartOffset = myDocument.getLineStartOffset(pos.line);
      }
    }

    int x = getTabbedTextWidth(lineStartOffset, visible);
    return new Point(x, y);
  }

  public Point visualPositionToXY(VisualPosition visible) {
    int y = visibleLineNumberToYPosition(visible.line);
    int logLine = visualToLogicalPosition(new VisualPosition(visible.line, 0)).line;

    int lineStartOffset;

    if (logLine == 0) {
      lineStartOffset = 0;
    }
    else {
      if (logLine >= myDocument.getLineCount()) {
        lineStartOffset = myDocument.getTextLength();
      }
      else {
        lineStartOffset = myDocument.getLineStartOffset(logLine);
      }
    }

    int x = getTabbedTextWidth(lineStartOffset, visible);
    return new Point(x, y);
  }

  private int getTabbedTextWidth(int lineStartOffset, VisualPosition pos) {
    if (pos.column == 0) return 0;

    int x = 0;
    int offset = lineStartOffset;
    CharSequence text = myDocument.getCharsNoThreadCheck();
    int textLength = myDocument.getTextLength();
    IterationState state = new IterationState(this, offset, false);
    int fontType = state.getMergedAttributes().getFontType();
    int spaceSize = getSpaceWidth(fontType);

    int column = 0;
    outer:
        while (column < pos.column) {
          if (offset >= textLength) break;

          if (offset >= state.getEndOffset()) {
            state.advance();
            fontType = state.getMergedAttributes().getFontType();
          }

          FoldRegion region = state.getCurrentFold();

          if (region != null) {
            char[] placeholder = region.getPlaceholderText().toCharArray();
            for (int j = 0; j < placeholder.length; j++) {
              x += charWidth(fontType, placeholder[j]);
              column++;
              if (column >= pos.column) break outer;
            }
            offset = region.getEndOffset();
          }
          else {
            char c = text.charAt(offset);
            if (c == '\n') {
              break;
            }
            if (c == '\t') {
              int prevX = x;
              x = nextTabStop(x);
              column += (x - prevX) / spaceSize;
            }
            else {
              x += charWidth(fontType, c);
              column++;
            }
            offset++;
          }
        }

    if (column != pos.column) {
      x += getSpaceWidth(fontType) * (pos.column - column);
    }

    return x;
  }

  public int nextTabStop(int x) {
    int tabSize = mySettings.getTabSize(myProject);
    if (tabSize <= 0) {
      tabSize = 1;
    }

    tabSize *= getSpaceWidth(Font.PLAIN);

    int nTabs = x / tabSize;
    return (nTabs + 1) * tabSize;
  }

  public int visibleLineNumberToYPosition(int lineNum) {
    if (lineNum < 0) throw new IndexOutOfBoundsException("Wrong line: " + lineNum);
    return lineNum * getLineHeight();
  }

  public void repaint(int startOffset, int endOffset) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myScrollPane == null) {
      return;
    }
    if (endOffset > myDocument.getTextLength()) {
      endOffset = myDocument.getTextLength();
    }
    if (startOffset < endOffset) {
      int startLine = myDocument.getLineNumber(startOffset);
      int endLine = myDocument.getLineNumber(endOffset);
      repaintLines(startLine, endLine);
    }
  }

  public void repaintLines(int startLine, int endLine) {
    Rectangle visibleRect = getScrollingModel().getVisibleArea();
    int yStartLine = logicalPositionToXY(new LogicalPosition(startLine, 0)).y;
    int yEndLine = logicalPositionToXY(new LogicalPosition(endLine, 0)).y + getLineHeight() + WAVE_HEIGHT;

    myEditorComponent.repaintEditorComponent(visibleRect.x,
                                             yStartLine,
                                             visibleRect.x + visibleRect.width,
                                             yEndLine - yStartLine);
    myGutterComponent.repaint(0, yStartLine, myGutterComponent.getWidth(), yEndLine - yStartLine);
  }

  private void beforeChangedUpdate(DocumentEvent e) {
    Rectangle viewRect = getScrollingModel().getVisibleArea();
    Point pos = visualPositionToXY(getCaretModel().getVisualPosition());
    myCaretUpdateVShift = pos.y - viewRect.y;
    mySizeContainer.beforeChange(e);
  }

  private void changedUpdate(DocumentEvent e) {
    if (myScrollPane == null) return;

    stopOptimizedScrolling();
    mySelectionModel.removeBlockSelection();

    mySizeContainer.changedUpdate(e);
    int startVisualLine = offsetToVisualPosition(e.getOffset()).line;
    int endVisualLine = offsetToVisualPosition(e.getOffset() + e.getNewLength()).line;

    if (myDocument.getTextLength() > 0) {
      int startDocLine = myDocument.getLineNumber(e.getOffset());
      int endDocLine = myDocument.getLineNumber(e.getOffset() + e.getNewLength());
      if (e.getOldLength() > e.getNewLength() || startDocLine != endDocLine) {
        updateGutterSize();
      }
    }

    updateCaretCursor();
    repaintLines(startVisualLine, endVisualLine);

    Point caretLocation = visualPositionToXY(getCaretModel().getVisualPosition());
    int scrollOffset = caretLocation.y - myCaretUpdateVShift;
    getScrollingModel().scrollVertically(scrollOffset);
  }

  private void updateGutterSize() {
    if (myGutterSizeUpdater != null) return;
    myGutterSizeUpdater = new Runnable() {
      public void run() {
        myGutterComponent.updateSize();
        myGutterSizeUpdater = null;
      }
    };
  }

  void validateSize() {
    if (myGutterSizeUpdater != null) {
      SwingUtilities.invokeLater(myGutterSizeUpdater);
    }

    Dimension dim = getPreferredSize();

    if (!dim.equals(myPreferredSize)) {
      myPreferredSize = dim;

      stopOptimizedScrolling();
      myScrollPane.getViewport().setViewSize(myPreferredSize);
      int lineNum = Math.max(1, getDocument().getLineCount());

      myGutterComponent.setLineNumberAreaWidth(getFontMetrics(Font.PLAIN).stringWidth(Integer.toString((lineNum + 2))) + 6);
      final JViewport rowHeader = myScrollPane.getRowHeader();
      if (rowHeader != null) {
        rowHeader.setViewSize(myGutterComponent.getPreferredSize());
      }

      getScrollPane().revalidate();

      myEditorComponent.repaint();
      myScrollPane.repaint();
      myMarkupModel.repaint();
    }
  }

  void recalcSizeAndRepaint() {
    mySizeContainer.reset();

    validateSize();

    Dimension size = getPreferredSize();

    myScrollPane.getVerticalScrollBar().setMaximum(size.height);
    myScrollPane.getHorizontalScrollBar().setMaximum(size.width);

    getScrollPane().revalidate();
    myMarkupModel.repaint();

    stopOptimizedScrolling();
    myEditorComponent.repaintEditorComponent();

    myGutterComponent.repaint();
  }

  public Document getDocument() {
    return myDocument;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void addEditorMouseListener(EditorMouseListener listener) {
    myMouseListeners.add(listener);
  }

  public void removeEditorMouseListener(EditorMouseListener listener) {
    boolean success = myMouseListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void addEditorMouseMotionListener(EditorMouseMotionListener listener) {
    myMouseMotionListeners.add(listener);
  }

  public void removeEditorMouseMotionListener(EditorMouseMotionListener listener) {
    boolean success = myMouseMotionListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public boolean isDisposed() {
    return isReleased;
  }

  public void paint(Graphics g) {
    validateSize();
    startOptimizedScrolling();

    if (myCursorUpdater != null) {
      myCursorUpdater.run();
      myCursorUpdater = null;
    }

    Rectangle clip = getClipBounds(g);

    if (clip == null) {
      return;
    }

    Rectangle viewRect = getScrollingModel().getVisibleArea();
    if (viewRect == null) {
      return;
    }

    if (isReleased) {
      g.setColor(new Color(128, 255, 128));
      g.fillRect(clip.x, clip.y, clip.width, clip.height);

      return;
    }

    Color background = getBackroundColor();
    g.setColor(background);
    g.fillRect(clip.x, clip.y, clip.width, clip.height);

    paintBackgrounds(g, clip);
    paintRectangularSelection(g);
    paintRightMargin(g, clip);
    final MarkupModel docMarkup = myDocument.getMarkupModel(myProject);
    if (docMarkup != null) {
      paintLineMarkersSeparators(g, clip, docMarkup);
    }
    paintLineMarkersSeparators(g, clip, myMarkupModel);
    paintText(g, clip);
    paintSegmentHighlightersBorderAndAfterEndOfLine(g, clip);
    BorderEffect borderEffect = new BorderEffect(this, g);
    borderEffect.paintHighlighters(getHighlighter());
    if (docMarkup != null) {
      borderEffect.paintHighlighters(docMarkup.getAllHighlighters());
    }
    borderEffect.paintHighlighters((getMarkupModel()).getAllHighlighters());
    paintCaretCursor(g);

    paintComposedTextDecoration((Graphics2D)g);
  }

  public void setBackgroundColor(Color color) {
    myForcedBackground = color;
  }

  public void resetBackgourndColor() {
    myForcedBackground = null;
  }

  public Color getBackroundColor() {
    if (myForcedBackground != null) return myForcedBackground;

    Color color = myScheme.getColor(EditorColors.BACKGROUND_COLOR);
    if (color == null) color = Color.white;
    if (myDocument.isWritable()) {
      return color;
    }
    Color readOnlyColor = myScheme.getColor(EditorColors.READONLY_BACKGROUND_COLOR);
    return readOnlyColor != null ? readOnlyColor : color;
  }

  private void paintComposedTextDecoration(Graphics2D g) {
    if (myInputMethodRequestsHandler != null && myInputMethodRequestsHandler.composedText != null) {
      VisualPosition visStart = offsetToVisualPosition(
        Math.min(myInputMethodRequestsHandler.composedTextStart, myDocument.getTextLength()));
      int y = visibleLineNumberToYPosition(visStart.line) + getLineHeight() - getDescent() + 1;
      Point p1 = visualPositionToXY(visStart);
      Point p2 = logicalPositionToXY(
        offsetToLogicalPosition(Math.min(myInputMethodRequestsHandler.composedTextEnd, myDocument.getTextLength())));

      Stroke saved = g.getStroke();
      BasicStroke dotted = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{0, 2, 0, 2},
                                           0);
      g.setStroke(dotted);
      g.drawLine(p1.x, y, p2.x, y);
      g.setStroke(saved);
    }
  }

  private static Rectangle getClipBounds(Graphics g) {
    return g.getClipBounds();
  }

  private void paintRightMargin(Graphics g, Rectangle clip) {
    Color rightMargin = myScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR);
    if (!mySettings.isRightMarginShown() || rightMargin == null) {
      return;
    }
    int x = mySettings.getRightMargin(myProject) * getSpaceWidth(Font.PLAIN);
    if (x >= clip.x && x < clip.x + clip.width) {
      g.setColor(rightMargin);
      g.drawLine(x, clip.y, x, clip.y + clip.height);
    }
  }

  private void paintSegmentHighlightersBorderAndAfterEndOfLine(Graphics g, Rectangle clip) {
    if (myDocument.getLineCount() == 0) return;
    int startLineNumber = yPositionToVisibleLineNumber(clip.y);
    int endLineNumber = yPositionToVisibleLineNumber(clip.y + clip.height) + 1;

    RangeHighlighter[] segmentHighlighters;
    final MarkupModel docMarkup = myDocument.getMarkupModel(myProject);
    if (docMarkup != null) {
      segmentHighlighters = docMarkup.getAllHighlighters();
      for (int i = 0; i < segmentHighlighters.length; i++) {
        paintSegmentHighlighterAfterEndOfLine(g, (RangeHighlighterEx)segmentHighlighters[i], startLineNumber,
                                              endLineNumber);
      }
    }

    segmentHighlighters = getMarkupModel().getAllHighlighters();
    for (int i = 0; i < segmentHighlighters.length; i++) {
      RangeHighlighter segmentHighlighter = segmentHighlighters[i];
      paintSegmentHighlighterAfterEndOfLine(g, (RangeHighlighterEx)segmentHighlighter, startLineNumber, endLineNumber);
    }
  }

  private void paintSegmentHighlighterAfterEndOfLine(Graphics g, RangeHighlighterEx segmentHighlighter,
                                                     int startLineNumber, int endLineNumber) {
    if (!segmentHighlighter.isValid()) {
      return;
    }
    if (segmentHighlighter.isAfterEndOfLine()) {
      int startOffset = segmentHighlighter.getStartOffset();
      int visibleStartLine = offsetToVisualPosition(startOffset).line;

      if (!getFoldingModel().isOffsetCollapsed(startOffset)) {
        if (visibleStartLine >= startLineNumber && visibleStartLine <= endLineNumber) {
          int logStartLine = offsetToLogicalPosition(startOffset).line;
          LogicalPosition logPosition = offsetToLogicalPosition(myDocument.getLineEndOffset(logStartLine));
          Point end = logicalPositionToXY(logPosition);
          int charWidth = getSpaceWidth(Font.PLAIN);
          int lineHeight = getLineHeight();
          TextAttributes attributes = segmentHighlighter.getTextAttributes();
          if (attributes != null && attributes.getBackgroundColor() != null) {
            g.setColor(attributes.getBackgroundColor());
            g.fillRect(end.x, end.y, charWidth, lineHeight);
          }
          if (attributes != null && attributes.getEffectColor() != null) {
            int y = visibleLineNumberToYPosition(visibleStartLine) + getLineHeight() - getDescent() + 1;
            g.setColor(attributes.getEffectColor());
            if (attributes.getEffectType() == EffectType.WAVE_UNDERSCORE) {
              drawWave(g, end.x, end.x + charWidth - 1, y);
            }
            else if (attributes.getEffectType() != EffectType.BOXED) {
              g.drawLine(end.x, y, end.x + charWidth - 1, y);
            }
          }
        }
      }
    }
  }

  public int getMaxWidthInRange(int startOffset, int endOffset) {
    int width = 0;
    VisualPosition start = offsetToVisualPosition(startOffset);
    VisualPosition end = offsetToVisualPosition(endOffset);

    for (int i = start.line; i <= end.line; i++) {
      int lastColumn = EditorUtil.getLastVisualLineColumnNumber(this, i) + 1;
      int lineWidth = visualPositionToXY(new VisualPosition(i, lastColumn)).x;

      if (lineWidth > width) {
        width = lineWidth;
      }
    }

    return width;
  }

  private void paintBackgrounds(Graphics g, Rectangle clip) {
    int lineHeight = getLineHeight();

    int visibleLineNumber = clip.y / lineHeight;

    int startLineNumber = xyToLogicalPosition(new Point(0, clip.y)).line;

    if (startLineNumber >= myDocument.getLineCount() || startLineNumber < 0) {
      return;
    }

    int start = myDocument.getLineStartOffset(startLineNumber);

    IterationState iterationState = new IterationState(this, start, true);

    LineIterator lIterator = createLineIterator();
    lIterator.start(start);
    if (lIterator.atEnd()) {
      return;
    }

    TextAttributes attributes = iterationState.getMergedAttributes();
    Color backColor = attributes.getBackgroundColor();
    Point position = new Point(0, visibleLineNumber * lineHeight);
    int fontType = attributes.getFontType();
    CharSequence text = myDocument.getCharsNoThreadCheck();
    int lastLineIndex = Math.max(0, myDocument.getLineCount() - 1);
    while (!iterationState.atEnd() && !lIterator.atEnd()) {
      int hEnd = iterationState.getEndOffset();
      int lEnd = lIterator.getEnd();

      if (hEnd >= lEnd) {
        FoldRegion collapsedFolderAt = myFoldingModel.getCollapsedRegionAtOffset(start);
        if (collapsedFolderAt == null) {
          position.x = drawBackground(g, backColor, text.subSequence(start, lEnd - lIterator.getSeparatorLength()),
                                      position, fontType);

          if (lIterator.getLineNumber() < lastLineIndex) {
            if (backColor != null) {
              Color saved = g.getColor();
              g.setColor(backColor);
              g.fillRect(position.x, position.y, clip.x + clip.width - position.x, lineHeight);
              g.setColor(saved);
            }
          }
          else {
            paintAfterFileEndBackground(iterationState, g, position, clip, lineHeight);
            break;
          }

          position.x = 0;
          if (position.y > clip.y + clip.height) break;
          position.y += lineHeight;
          start = lEnd;
        }

        lIterator.advance();
      }
      else {
        FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
        if (collapsedFolderAt != null) {
          position.x = drawBackground(g, backColor, collapsedFolderAt.getPlaceholderText(),
                                      position, fontType);
        }
        else {
          if (hEnd > lEnd - lIterator.getSeparatorLength()) {
            position.x = drawBackground(g, backColor, text.subSequence(start, lEnd - lIterator.getSeparatorLength()),
                                        position, fontType);
          }
          else {
            position.x = drawBackground(g, backColor, text.subSequence(start, hEnd), position, fontType);
          }
        }

        iterationState.advance();
        attributes = iterationState.getMergedAttributes();
        backColor = attributes.getBackgroundColor();
        fontType = attributes.getFontType();
        start = iterationState.getStartOffset();
      }
    }

    if (lIterator.getLineNumber() >= lastLineIndex && position.y <= clip.y + clip.height) {
      paintAfterFileEndBackground(iterationState, g, position, clip, lineHeight);
    }
  }

  private void paintRectangularSelection(Graphics g) {
    final SelectionModel model = getSelectionModel();
    if (!model.hasBlockSelection()) return;
    final Point start = logicalPositionToXY(model.getBlockStart());
    final Point end = logicalPositionToXY(model.getBlockEnd());
    g.setColor(myScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
    final int y;
    final int height;
    if (start.y <= end.y) {
      y = start.y;
      height = end.y - y + getLineHeight();
    }
    else {
      y = end.y;
      height = start.y - end.y + getLineHeight();
    }
    final int x = Math.min(start.x, end.x);
    final int width = Math.max(2, Math.abs(end.x - start.x));
    g.fillRect(x, y, width, height);
  }

  private static void paintAfterFileEndBackground(IterationState iterationState, Graphics g, Point position,
                                                  Rectangle clip, int lineHeight) {
    Color backColor = iterationState.getPastFileEndBackground();
    if (backColor != null) {
      Color saved = g.getColor();
      g.setColor(backColor);
      g.fillRect(position.x, position.y, clip.x + clip.width - position.x, lineHeight);
      g.setColor(saved);
    }
  }

  private int drawBackground(Graphics g,
                             Color backColor,
                             CharSequence text,
                             Point position,
                             int fontType) {
    int w = getTextSegmentWidth(text, position.x, fontType);

    if (backColor != null) {
      Color savedColor = g.getColor();
      g.setColor(backColor);
      g.fillRect(position.x, position.y, w, getLineHeight());
      g.setColor(savedColor);
    }

    return position.x + w;
  }

  private LineIterator createLineIterator() {
    return myDocument.createLineIterator();
  }

  private void paintText(Graphics g, Rectangle clip) {
    int lineHeight = getLineHeight();

    int visibleLineNumber = clip.y / lineHeight;

    int startLineNumber = xyToLogicalPosition(new Point(0, clip.y)).line;

    if (startLineNumber >= myDocument.getLineCount() || startLineNumber < 0) {
      return;
    }

    int start = myDocument.getLineStartOffset(startLineNumber);

    IterationState iterationState = new IterationState(this, start, true);

    LineIterator lIterator = createLineIterator();
    lIterator.start(start);
    if (lIterator.atEnd()) {
      return;
    }

    TextAttributes attributes = iterationState.getMergedAttributes();
    Color currentColor = attributes.getForegroundColor();
    if (currentColor == null) {
      currentColor = Color.black;
    }
    Color effectColor = attributes.getEffectColor();
    EffectType effectType = attributes.getEffectType();
    int fontType = attributes.getFontType();
    myCurrentFontType = fontType;
    Font currentFont = getFont(fontType);
    g.setColor(currentColor);
    g.setFont(currentFont);
    Point position = new Point(0, visibleLineNumber * lineHeight);
    while (!iterationState.atEnd() && !lIterator.atEnd()) {
      int hEnd = iterationState.getEndOffset();
      int lEnd = lIterator.getEnd();
      if (hEnd >= lEnd) {
        FoldRegion collapsedFolderAt = myFoldingModel.getCollapsedRegionAtOffset(start);
        if (collapsedFolderAt == null) {
          drawString(g, start, lEnd - lIterator.getSeparatorLength(), position, clip, effectColor, effectType,
                     fontType, currentColor);
          position.x = 0;
          if (position.y > clip.y + clip.height) break;
          position.y += lineHeight;
          start = lEnd;
        }

//        myBorderEffect.eolReached(g, this);
        lIterator.advance();
      }
      else {
        FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
        if (collapsedFolderAt != null) {
          int foldingXStart = position.x;
          position.x = drawString(g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType,
                                  fontType, currentColor);
          BorderEffect.paintFoldedEffect(g, foldingXStart, position.y, position.x, getLineHeight(), effectColor,
                                         effectType);

        }
        else {
          if (hEnd > lEnd - lIterator.getSeparatorLength()) {
            position.x = drawString(g, start, lEnd - lIterator.getSeparatorLength(), position, clip, effectColor,
                                    effectType, fontType, currentColor);
          }
          else {
            position.x = drawString(g, start, hEnd, position, clip, effectColor, effectType, fontType, currentColor);
          }
        }

        iterationState.advance();
        attributes = iterationState.getMergedAttributes();
        Color color = attributes.getForegroundColor();
        if (color == null) {
          color = Color.black;
        }
        if (color != currentColor) {
          g.setColor(color);
          currentColor = color;
        }
        effectColor = attributes.getEffectColor();
        effectType = attributes.getEffectType();
        fontType = attributes.getFontType();

        start = iterationState.getStartOffset();
      }
    }

    FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
    if (collapsedFolderAt != null) {
      int foldingXStart = position.x;
      int foldingXEnd = drawString(g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType, fontType,
                                   currentColor);
      BorderEffect.paintFoldedEffect(g, foldingXStart, position.y, foldingXEnd, getLineHeight(), effectColor,
                                     effectType);
//      myBorderEffect.collapsedFolderReached(g, this);
    }

    flushCachedChars(g);
  }

  private class CachedFontContent {
    int[] charWidths = null;
    final CharSequence[] data = new CharSequence[CACHED_CHARS_BUFFER_SIZE];
    final int[] x = new int[CACHED_CHARS_BUFFER_SIZE];
    final int[] y = new int[CACHED_CHARS_BUFFER_SIZE];
    final Color[] color = new Color[CACHED_CHARS_BUFFER_SIZE];

    int myCount = 0;
    final int myFontType;
    private FontMetrics myFontMetrics;

    public CachedFontContent(int fontType) {
      myFontType = fontType;
    }

    public void reinitSettings() {
      myFontMetrics = null;
      charWidths = null;
    }

    public int charWidth(char c) {
      if (c >= 256) return fontMetrics().charWidth(c);
      if (charWidths == null) {
        charWidths = new int[256];
        FontMetrics fontMetrics = fontMetrics();
        for (int i = 0; i < 256; i++) {
          charWidths[i] = fontMetrics.charWidth(i);
        }
      }
      return charWidths[c];
    }

    private FontMetrics fontMetrics() {
      if (myFontMetrics == null) {
        myFontMetrics = getFontMetrics(myFontType);
      }
      return myFontMetrics;
    }

    public void flushContent(Graphics g) {
      if (myCount != 0) {
        if (myCurrentFontType != myFontType) {
          myCurrentFontType = myFontType;
          g.setFont(getFont(myFontType));
        }

        Color currentColor = null;
        for (int i = 0; i < myCount; i++) {
          if (!color[i].equals(currentColor)) {
            currentColor = color[i];
            g.setColor(currentColor);
          }

          drawChars(g, data[i], x[i], y[i]);
          color[i] = null;
          data[i] = null;
        }

        myCount = 0;
      }
    }

    public void addContent(Graphics g, CharSequence data, int x, int y, Color color) {
      this.data[myCount] = data;
      this.x[myCount] = x;
      this.y[myCount] = y;
      this.color[myCount] = color;

      myCount++;
      if (myCount >= CACHED_CHARS_BUFFER_SIZE) {
        flushContent(g);
      }
    }
  }

  private void flushCachedChars(Graphics g) {
    myPlainCache.flushContent(g);
    myBoldCache.flushContent(g);
    myBoldItalicCache.flushContent(g);
    myItalicCache.flushContent(g);
  }

  private void paintCaretCursor(Graphics g) {
    myCaretCursor.paint(g);
  }

  private void paintLineMarkersSeparators(Graphics g, Rectangle clip, MarkupModel markupModel) {
    if (markupModel == null) return;
    RangeHighlighter[] lineMarkers = markupModel.getAllHighlighters();
    for (int i = 0; i < lineMarkers.length; i++) {
      RangeHighlighter lineMarker = lineMarkers[i];
      paintLineMarkerSeparator(lineMarker, clip, g);
    }
  }

  private void paintLineMarkerSeparator(RangeHighlighter marker, Rectangle clip, Graphics g) {
    if (!marker.isValid()) {
      return;
    }
    Color separatorColor = marker.getLineSeparatorColor();
    if (separatorColor != null) {
      int lineNumber = marker.getLineSeparatorPlacement() == SeparatorPlacement.TOP ? marker.getDocument()
        .getLineNumber(marker.getStartOffset()) : marker.getDocument().getLineNumber(marker.getEndOffset());
      if (lineNumber < 0 || lineNumber >= myDocument.getLineCount()) {
        return;
      }

      int endShift = clip.x + clip.width;
      g.setColor(separatorColor);

      if (mySettings.isRightMarginShown()
          && myScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR) != null) {
        endShift =
        Math.min(endShift, mySettings.getRightMargin(myProject) * getSpaceWidth(Font.PLAIN));
      }

      int y = visibleLineNumberToYPosition(logicalToVisualPosition(new LogicalPosition(lineNumber, 0)).line);

      if (marker.getLineSeparatorPlacement() != SeparatorPlacement.TOP) {
        y += getLineHeight();
      }

      g.drawLine(0, y - 1, endShift, y - 1);
    }
  }

  private Font getFont(int type) {
    if (type == Font.BOLD) {
      return myScheme.getFont(EditorFontType.BOLD);
    }
    else {
      if (type == Font.ITALIC) {
        return myScheme.getFont(EditorFontType.ITALIC);
      }
      else {
        if (type == Font.ITALIC + Font.BOLD) {
          return myScheme.getFont(EditorFontType.BOLD_ITALIC);
        }
        else {
          return myScheme.getFont(EditorFontType.PLAIN);
        }
      }
    }
  }

  private int drawString(Graphics g, int start, int end, Point position, Rectangle clip, Color effectColor,
                         EffectType effectType, int fontType, Color fontColor) {
    if (start >= end) return position.x;

    CharSequence text = myDocument.getCharsNoThreadCheck();
    boolean isInClip = (getLineHeight() + position.y >= clip.y) && (position.y <= clip.y + clip.height);

    if (!isInClip) return position.x;

    int y = getLineHeight() - getDescent() + position.y;
    int x = position.x;
    return drawTabbedString(g, text.subSequence(start, end), x, y, effectColor, effectType, fontType, fontColor);
  }

  private int drawString(Graphics g, String text, Point position, Rectangle clip, Color effectColor,
                         EffectType effectType, int fontType, Color fontColor) {
    boolean isInClip = (getLineHeight() + position.y >= clip.y) && (position.y <= clip.y + clip.height);

    if (!isInClip) return position.x;

    int y = getLineHeight() - getDescent() + position.y;
    int x = position.x;
    return drawTabbedString(g, text, x, y, effectColor, effectType, fontType,
                            fontColor);
  }

  private int drawTabbedString(Graphics g, CharSequence text, int x, int y, Color effectColor,
                               EffectType effectType, int fontType, Color fontColor) {
    int xStart = x;

    int start = 0;

    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) != '\t') continue;

      if (i > start) {
        drawCharsCached(g, text.subSequence(start, i), x, y, fontType, fontColor);
        for (int j = start; j < i; j++) {
          char c = text.charAt(j);
          x += charWidth(fontType, c);
        }
      }

      int x1 = nextTabStop(x);
      drawTabPlacer(g, y, x, x1);
      x = x1;
      start = i + 1;
    }

    if (start < text.length()) {
      drawCharsCached(g, text.subSequence(start, text.length()), x, y, fontType, fontColor);
      for (int j = start; j < text.length(); j++) x += charWidth(fontType, text.charAt(j));
    }

    if (effectColor != null) {
      Color savedColor = g.getColor();

      int w = getTextSegmentWidth(text, xStart, fontType);
//      myBorderEffect.flushIfCantProlong(g, this, effectType, effectColor);
      if (effectType == EffectType.LINE_UNDERSCORE) {
        g.setColor(effectColor);
        g.drawLine(xStart, y + 1, xStart + w, y + 1);
        g.setColor(savedColor);
      }
      else {
        if (effectType == EffectType.STRIKEOUT) {
          g.setColor(effectColor);
          int y1 = y - getCharHeight() / 2;
          g.drawLine(xStart, y1, xStart + w, y1);
          g.setColor(savedColor);
        }
        else {
          if (effectType == EffectType.WAVE_UNDERSCORE) {
            g.setColor(effectColor);
            drawWave(g, xStart, xStart + w, y + 1);
            g.setColor(savedColor);
          } /*else {
            if (effectType == EffectType.BOXED) {
              int yStart = y - getLineHeight() + getDescent();
              if (!myBorderEffect.isDetached()) myBorderEffect.start(effectColor, start);
              myBorderEffect.setEndOffset(offset + length);
            }
          }*/
        }
      }
    } /*else myBorderEffect.flushBorder(g, this);*/

    return x;
  }

  public int charWidth(int fontType, char c) {
    switch(fontType) {
      case Font.PLAIN: return myPlainCache.charWidth(c);
      case Font.BOLD: return myBoldCache.charWidth(c);
      case Font.ITALIC: return myItalicCache.charWidth(c);
      case Font.BOLD | Font.ITALIC: return myBoldItalicCache.charWidth(c);
    }

    return 0;
  }

  private void drawTabPlacer(Graphics g, int y, int start, int stop) {
    if (mySettings.isWhitespacesShown()) {
      stop -= g.getFontMetrics().charWidth(' ') / 2;
      Color oldColor = g.getColor();
      g.setColor(myScheme.getColor(EditorColors.WHITESPACES_COLOR));
      final int charHeight = getCharHeight();
      final int halfCharHeight = charHeight / 2;
      int mid = y - halfCharHeight;
      int top = y - charHeight;
      g.drawLine(start, mid, stop, mid);
      g.drawLine(stop, y, stop, top);
      g.fillPolygon(new int[]{stop - halfCharHeight, stop - halfCharHeight, stop},
                    new int[]{y, y - charHeight, y - halfCharHeight}, 3);
      g.setColor(oldColor);
    }
  }

  private void drawCharsCached(Graphics g,
                               CharSequence data,
                               int x,
                               int y,
                               int fontType,
                               Color color) {
    if (!SystemInfo.isMac) {
      if (fontType == myCurrentFontType) {
        drawChars(g, data, x, y);
      }
      else if (fontType == Font.PLAIN) {
        myPlainCache.addContent(g, data, x, y, color);
      }
      else if (fontType == Font.BOLD) {
        myBoldCache.addContent(g, data, x, y, color);
      }
      else if (fontType == Font.ITALIC) {
        myItalicCache.addContent(g, data, x, y, color);
      }
      else {
        myBoldItalicCache.addContent(g, data, x, y, color);
      }
    }
    else {
      if (fontType != myCurrentFontType) {
        myCurrentFontType = fontType;
        g.setFont(getFont(fontType));
      }

      drawChars(g, data, x, y);
    }
  }

  private void drawChars(Graphics g, CharSequence data, int x, int y) {
    g.drawString(data.toString(), x, y);

    if (mySettings.isWhitespacesShown()) {
      Color oldColor = g.getColor();
      g.setColor(myScheme.getColor(EditorColors.WHITESPACES_COLOR));
      final FontMetrics metrics = g.getFontMetrics();
      int halfSpaceWidth = metrics.charWidth(' ') / 2;
      for (int i = 0; i < data.length(); i++) {
        if (data.charAt(i) == ' ') {
          g.fillRect(x + halfSpaceWidth, y, 1, 1);
        }
        x += metrics.charWidth(data.charAt(i));
      }
      g.setColor(oldColor);
    }
  }

  private static final int WAVE_HEIGHT = 2;
  private static final int WAVE_SEGMENT_LENGTH = 4;

  private static void drawWave(Graphics g, int xStart, int xEnd, int y) {
    int startSegment = xStart / WAVE_SEGMENT_LENGTH;
    int endSegment = xEnd / WAVE_SEGMENT_LENGTH;
    for (int i = startSegment; i < endSegment; i++) {
      drawWaveSegment(g, WAVE_SEGMENT_LENGTH * i, y);
    }

    int x = WAVE_SEGMENT_LENGTH * endSegment;
    g.drawLine(x, y + WAVE_HEIGHT, x + WAVE_SEGMENT_LENGTH / 2, y);
  }

  private static void drawWaveSegment(Graphics g, int x, int y) {
    g.drawLine(x, y + WAVE_HEIGHT, x + WAVE_SEGMENT_LENGTH / 2, y);
    g.drawLine(x + WAVE_SEGMENT_LENGTH / 2, y, x + WAVE_SEGMENT_LENGTH, y + WAVE_HEIGHT);
  }

  private int getTextSegmentWidth(CharSequence text, int xStart, int fontType) {
    int start = 0;
    int x = xStart;

    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) != '\t') continue;

      if (i > start) {
        for (int j = start; j < i; j++) x += charWidth(fontType, text.charAt(j));
      }
      x = nextTabStop(x);
      start = i + 1;
    }

    if (start < text.length()) {
      for (int j = start; j < text.length(); j++) x += charWidth(fontType, text.charAt(j));
    }

    return x - xStart;
  }

  public int getLineHeight() {
    if (myLineHeight != -1) return myLineHeight;

    ApplicationManager.getApplication().assertIsDispatchThread();

    FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
    myLineHeight = (int)(fontMetrics.getHeight() * (isOneLineMode() ? 1 : myScheme.getLineSpacing()));
    if (myLineHeight == 0) {
      myLineHeight = fontMetrics.getHeight();
      if (myLineHeight == 0) {
        myLineHeight = 12;
      }
    }

    return myLineHeight;
  }

  int getDescent() {
    if (myDescent != -1) {
      return myDescent;
    }
    FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
    myDescent = fontMetrics.getDescent();
    return myDescent;
  }

  public FontMetrics getFontMetrics(int fontType) {
    if (myPlainFontMetrics == null) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myPlainFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      myBoldFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.BOLD));
      myItalicFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.ITALIC));
      myBoldItalicFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.BOLD_ITALIC));
    }

    if (fontType == Font.PLAIN) return myPlainFontMetrics;
    if (fontType == Font.BOLD) return myBoldFontMetrics;
    if (fontType == Font.ITALIC) return myItalicFontMetrics;
    if (fontType == Font.BOLD + Font.ITALIC) return myBoldItalicFontMetrics;

    LOG.assertTrue(false, "Unknown font type: " + fontType);

    return myPlainFontMetrics;
  }

  private int getCharHeight() {
    if (myCharHeight == -1) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      myCharHeight = fontMetrics.charWidth('a');
    }
    return myCharHeight;
  }

  public Dimension getPreferredSize() {
    Dimension draft = getSizeWithoutCaret();
    int caretX = visualPositionToXY(getCaretModel().getVisualPosition()).x;
    draft.width = Math.max(caretX, draft.width) + mySettings.getAdditionalColumnsCount() * getSpaceWidth(Font.PLAIN);
    return draft;
  }

  private Dimension getSizeWithoutCaret() {
    Dimension size = mySizeContainer.getContentSize();
    if (isOneLineMode()) return new Dimension(size.width, getLineHeight());
    if (mySettings.isAdditionalPageAtBottom()) {
      int lineHeight = getLineHeight();
      return new Dimension(size.width,
                           size.height +
                           Math.max(getScrollingModel().getVisibleArea().height - 2 * lineHeight, lineHeight));
    }

    return getContentSize();
  }

  public Dimension getContentSize() {
    Dimension size = mySizeContainer.getContentSize();
    return new Dimension(size.width, size.height + mySettings.getAdditionalLinesCount() * getLineHeight());
  }

  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  public int logicalPositionToOffset(LogicalPosition pos) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myDocument.getLineCount() == 0) return 0;

    if (pos.line < 0) throw new IndexOutOfBoundsException("Wrong line: " + pos.line);
    if (pos.column < 0) throw new IndexOutOfBoundsException("Wrong column:" + pos.column);

    if (pos.line >= myDocument.getLineCount()) {
      return myDocument.getTextLength();
    }

    int start = myDocument.getLineStartOffset(pos.line);
    int end = myDocument.getLineEndOffset(pos.line);

    CharSequence text = myDocument.getCharsNoThreadCheck();

    if (pos.column == 0) return start;
    return EditorUtil.calcOffset(this, text, start, end, pos.column, mySettings.getTabSize(myProject));
  }

  public void setLastColumnNumber(int val) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myLastColumnNumber = val;
  }

  public int getLastColumnNumber() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myLastColumnNumber;
  }

  int getVisibleLineCount() {
    int line = getDocument().getLineCount();
    line -= myFoldingModel.getFoldedLinesCountBefore(getDocument().getTextLength() + 1);
    return line;
  }

  public VisualPosition logicalToVisualPosition(LogicalPosition logicalPos) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myFoldingModel.isFoldingEnabled()) return new VisualPosition(logicalPos.line, logicalPos.column);

    int offset = logicalPositionToOffset(logicalPos);

    FoldRegion outermostCollapsed = myFoldingModel.getCollapsedRegionAtOffset(offset);
    if (outermostCollapsed != null && offset > outermostCollapsed.getStartOffset()) {
      if (offset < getDocument().getTextLength() - 1) {
        offset = outermostCollapsed.getStartOffset();
        LogicalPosition foldStart = offsetToLogicalPosition(offset);
        return logicalToVisualPosition(foldStart);
      }
      else {
        offset = outermostCollapsed.getEndOffset() + 3;
      }
    }

    int line = logicalPos.line;
    int column = logicalPos.column;

    line -= myFoldingModel.getFoldedLinesCountBefore(offset);

    FoldRegion[] toplevel = myFoldingModel.fetchTopLevel();
    for (int idx = myFoldingModel.getLastTopLevelIndexBefore(offset); idx >= 0; idx--) {
      FoldRegion region = toplevel[idx];
      if (region.isValid()) {
        if (region.getDocument().getLineNumber(region.getEndOffset()) == logicalPos.line &&
            region.getEndOffset() <= offset) {
          LogicalPosition foldStart = offsetToLogicalPosition(region.getStartOffset());
          LogicalPosition foldEnd = offsetToLogicalPosition(region.getEndOffset());
          column += foldStart.column + region.getPlaceholderText().length() - foldEnd.column;
          offset = region.getStartOffset();
          logicalPos = foldStart;
        }
        else {
          break;
        }
      }
    }

    LOG.assertTrue(line >= 0);

    return new VisualPosition(line, Math.max(0, column));
  }

  private FoldRegion getLastCollapsedBeforePosition(VisualPosition visual) {
    FoldRegion[] topLevelCollapsed = myFoldingModel.fetchTopLevel();

    if (topLevelCollapsed == null) return null;

    int start = 0;
    int end = topLevelCollapsed.length - 1;
    int i = 0;

    while (start <= end) {
      i = (start + end) / 2;
      FoldRegion region = topLevelCollapsed[i];
      LogicalPosition logFoldEnd = offsetToLogicalPosition(region.getEndOffset() - 1);
      VisualPosition visFoldEnd = logicalToVisualPosition(logFoldEnd);
      if (visFoldEnd.line < visual.line) {
        start = i + 1;
      }
      else {
        if (visFoldEnd.line > visual.line) {
          end = i - 1;
        }
        else {
          if (visFoldEnd.column < visual.column) {
            start = i + 1;
          }
          else {
            if (visFoldEnd.column > visual.column) {
              end = i - 1;
            }
            else {
              i--;
              break;
            }
          }
        }
      }
    }

    while (i >= 0 && i < topLevelCollapsed.length) {
      if (topLevelCollapsed[i].isValid()) break;
      i--;
    }

    if (i >= 0 && i < topLevelCollapsed.length) {
      FoldRegion region = topLevelCollapsed[i];
      LogicalPosition logFoldEnd = offsetToLogicalPosition(region.getEndOffset() - 1);
      VisualPosition visFoldEnd = logicalToVisualPosition(logFoldEnd);
      if (visFoldEnd.line > visual.line || visFoldEnd.line == visual.line && visFoldEnd.column > visual.column) {
        i--;
        if (i >= 0) {
          return topLevelCollapsed[i];
        }
        else {
          return null;
        }
      }
      return region;
    }

    return null;
  }

  public LogicalPosition visualToLogicalPosition(VisualPosition visiblePos) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myFoldingModel.isFoldingEnabled()) return new LogicalPosition(visiblePos.line, visiblePos.column);

    int line = visiblePos.line;
    int column = visiblePos.column;

    FoldRegion lastCollapsedBefore = getLastCollapsedBeforePosition(visiblePos);

    if (lastCollapsedBefore != null) {
      LogicalPosition logFoldEnd = offsetToLogicalPosition(lastCollapsedBefore.getEndOffset());
      VisualPosition visFoldEnd = logicalToVisualPosition(logFoldEnd);

      line = logFoldEnd.line + (visiblePos.line - visFoldEnd.line);
      if (visFoldEnd.line == visiblePos.line) {
        if (visiblePos.column >= visFoldEnd.column) {
          column = logFoldEnd.column + (visiblePos.column - visFoldEnd.column);
        }
        else {
          return offsetToLogicalPosition(lastCollapsedBefore.getStartOffset());
        }
      }
    }

    if (column < 0) column = 0;

    return new LogicalPosition(line, column);
  }

  private int calcLogicalLineNumber(int offset) {
    int textLength = myDocument.getTextLength();
    if (textLength == 0) return 0;

    if (offset > textLength || offset < 0) {
      throw new IndexOutOfBoundsException("Wrong offset: " + offset + " textLength: " + textLength);
    }

    int lineIndex = myDocument.getLineNumber(offset);

    LOG.assertTrue(lineIndex >= 0 && lineIndex < myDocument.getLineCount());

    return lineIndex;
  }

  private int calcColumnNumber(int offset, int lineIndex) {
    if (myDocument.getTextLength() == 0) return 0;

    CharSequence text = myDocument.getCharsSequence();
    int start = myDocument.getLineStartOffset(lineIndex);
    if (start == offset) return 0;
    return EditorUtil.calcColumnNumber(this, text, start, offset, mySettings.getTabSize(myProject));
  }

  private void moveCaretToScreenPos(int x, int y) {
    if (x < 0) {
      x = 0;
    }

    LogicalPosition pos = xyToLogicalPosition(new Point(x, y));

    int columnNumber = pos.column;
    int lineNumber = pos.line;

    if (lineNumber >= myDocument.getLineCount()) {
      lineNumber = myDocument.getLineCount() - 1;
    }
    if (!mySettings.isVirtualSpace()) {
      if (lineNumber >= 0) {
        int lineEndOffset = myDocument.getLineEndOffset(lineNumber);
        int lineEndColumnNumber = calcColumnNumber(lineEndOffset, lineNumber);
        if (columnNumber > lineEndColumnNumber) {
          columnNumber = lineEndColumnNumber;
        }
      }
    }
    if (lineNumber < 0) {
      lineNumber = 0;
      columnNumber = 0;
    }
    if (!mySettings.isCaretInsideTabs()) {
      int offset = logicalPositionToOffset(new LogicalPosition(lineNumber, columnNumber));
      CharSequence text = myDocument.getCharsSequence();
      if (offset >= 0 && offset < myDocument.getTextLength()) {
        if (text.charAt(offset) == '\t') {
          columnNumber = calcColumnNumber(offset, lineNumber);
        }
      }
    }
    LogicalPosition pos1 = new LogicalPosition(lineNumber, columnNumber);
    getCaretModel().moveToLogicalPosition(pos1);
  }

  private void runMousePressedCommand(final MouseEvent e) {
    myMousePressedEvent = e;
    EditorMouseEvent event = new EditorMouseEvent(this, e, getMouseEventArea(e));

    EditorMouseListener[] mouseListeners = myMouseListeners.toArray(new EditorMouseListener[myMouseListeners.size()]);
    for (int i = 0; i < mouseListeners.length; i++) {
      mouseListeners[i].mousePressed(event);
    }

    // On some systems (for example on Linux) popup trigger is MOUSE_PRESSED event.
    // But this trigger is always consumed by popup handler. In that case we have to
    // also move caret.
    if (event.isConsumed() &&
        !(event.getMouseEvent().isPopupTrigger() || event.getArea() == EditorMouseEventArea.EDITING_AREA)) {
      return;
    }

    if (myCommandProcessor != null) {
      Runnable runnable = new Runnable() {
        public void run() {
          processMousePressed(e);
        }
      };
      myCommandProcessor.executeCommand(myProject, runnable, "", null);
    }
    else {
      processMousePressed(e);
    }
  }

  private void runMouseClickedCommand(final MouseEvent e) {
    EditorMouseEvent event = new EditorMouseEvent(this, e, getMouseEventArea(e));
    EditorMouseListener[] listeners = myMouseListeners.toArray(new EditorMouseListener[myMouseListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].mouseClicked(event);
      if (event.isConsumed()) {
        e.consume();
        return;
      }
    }
  }

  private void runMouseReleasedCommand(final MouseEvent e) {
    myScrollingTimer.stop();
    EditorMouseEvent event = new EditorMouseEvent(this, e, getMouseEventArea(e));
    EditorMouseListener[] listeners = myMouseListeners.toArray(new EditorMouseListener[myMouseListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].mouseReleased(event);
      if (event.isConsumed()) {
        e.consume();
        return;
      }
    }

    if (myCommandProcessor != null) {
      Runnable runnable = new Runnable() {
        public void run() {
          processMouseReleased(e);
        }
      };
      myCommandProcessor.executeCommand(myProject, runnable, "", null);
    }
    else {
      processMouseReleased(e);
    }
  }

  private void runMouseEnteredCommand(MouseEvent e) {
    EditorMouseEvent event = new EditorMouseEvent(this, e, getMouseEventArea(e));
    EditorMouseListener[] listeners = myMouseListeners.toArray(new EditorMouseListener[myMouseListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].mouseEntered(event);
      if (event.isConsumed()) {
        e.consume();
        return;
      }
    }
  }

  private void runMouseExitedCommand(MouseEvent e) {
    EditorMouseEvent event = new EditorMouseEvent(this, e, getMouseEventArea(e));
    EditorMouseListener[] listeners = myMouseListeners.toArray(new EditorMouseListener[myMouseListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].mouseExited(event);
      if (event.isConsumed()) {
        e.consume();
        return;
      }
    }
  }

  private void processMousePressed(MouseEvent e) {
    if (myMouseSelectionState != MOUSE_SELECTION_STATE_NONE &&
        System.currentTimeMillis() - myMouseSelectionChangeTimestamp > 1000) {
      setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);
    }

    int x = e.getX();
    int y = e.getY();

    if (x < 0) x = 0;
    if (y < 0) y = 0;

    final EditorMouseEventArea eventArea = getMouseEventArea(e);
    if (eventArea == EditorMouseEventArea.FOLDING_OUTLINE_AREA) {
      final FoldRegion range = myGutterComponent.findFoldingAnchorAt(x, y);
      if (range != null) {
        final boolean expansion = !range.isExpanded();

        int scrollShift = y - getScrollingModel().getVerticalScrollOffset();
        Runnable processor = new Runnable() {
          public void run() {
            myFoldingModel.flushCaretShift();
            range.setExpanded(expansion);
          }
        };
        getFoldingModel().runBatchFoldingOperation(processor);
        y = myGutterComponent.getHeadCenterY(range);
        getScrollingModel().scrollVertically(y - scrollShift);
        return;
      }
    }

    if (e.getSource() == myGutterComponent) {
      if (eventArea == EditorMouseEventArea.LINE_MARKERS_AREA ||
          eventArea == EditorMouseEventArea.ANNOTATIONS_AREA) {
        myGutterComponent.mousePressed(e);
        if (e.isConsumed()) return;
      }
      x = 0;
    }

    int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();
    moveCaretToScreenPos(x, y);

    if (e.isPopupTrigger()) return;

    requestFocus();

    int caretOffset = getCaretModel().getOffset();

    myMouseSelectedRegion = myFoldingModel.getFoldingPlaceholderAt(new Point(x, y));
    myMousePressedInsideSelection = mySelectionModel.hasSelection() &&
                                    caretOffset >= mySelectionModel.getSelectionStart() &&
                                    caretOffset <= mySelectionModel.getSelectionEnd();

    if (!myMousePressedInsideSelection && mySelectionModel.hasBlockSelection()) {
      int[] starts = mySelectionModel.getBlockSelectionStarts();
      int[] ends = mySelectionModel.getBlockSelectionEnds();
      for (int i = 0; i < starts.length; i++) {
        if (caretOffset >= starts[i] && caretOffset < ends[i]) {
          myMousePressedInsideSelection = true;
          break;
        }
      }
    }

    if (getMouseEventArea(e) == EditorMouseEventArea.LINE_NUMBERS_AREA && e.getClickCount() == 1) {
      mySelectionModel.selectLineAtCaret();
      setMouseSelectionState(MOUSE_SELECTION_STATE_LINE_SELECTED);
      mySavedSelectionStart = mySelectionModel.getSelectionStart();
      mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
      return;
    }

    if (e.isShiftDown() && !e.isControlDown() && !e.isAltDown()) {
      if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
        if (caretOffset < mySavedSelectionStart) {
          mySelectionModel.setSelection(mySavedSelectionEnd, caretOffset);
        }
        else {
          mySelectionModel.setSelection(mySavedSelectionStart, caretOffset);
        }
      }
      else {
        mySelectionModel.setSelection(oldSelectionStart, caretOffset);
      }
    }
    else {
      if (!myMousePressedInsideSelection && getSelectionModel().hasSelection()) {
        setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);
        mySelectionModel.setSelection(caretOffset, caretOffset);
      }
      else {
        if (!e.isPopupTrigger()) {
          switch (e.getClickCount()) {
            case 2:
              mySelectionModel.selectWordAtCaret(mySettings.isMouseClickSelectionHonorsCamelWords());
              setMouseSelectionState(MOUSE_SELECTION_STATE_WORD_SELECTED);
              mySavedSelectionStart = mySelectionModel.getSelectionStart();
              mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
              getCaretModel().moveToOffset(mySavedSelectionEnd);
              break;

            case 3:
              mySelectionModel.selectLineAtCaret();
              setMouseSelectionState(MOUSE_SELECTION_STATE_LINE_SELECTED);
              mySavedSelectionStart = mySelectionModel.getSelectionStart();
              mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
              break;
          }
        }
      }
    }
  }

  private static boolean isControlKeyDown(MouseEvent mouseEvent) {
    return SystemInfo.isMac ? mouseEvent.isMetaDown() : mouseEvent.isControlDown();
  }

  private void processMouseReleased(MouseEvent e) {
    if (e.getSource() == myGutterComponent) {
      myGutterComponent.mouseReleased(e);
    }

    if (getMouseEventArea(e) != EditorMouseEventArea.EDITING_AREA || e.getY() < 0 || e.getX() < 0) {
      return;
    }

//    if (myMousePressedInsideSelection) getSelectionModel().removeSelection();
    final FoldRegion region = ((FoldingModelEx)getFoldingModel()).getFoldingPlaceholderAt(e.getPoint());
    if (e.getX() >= 0 && e.getY() >= 0 && region != null && region == myMouseSelectedRegion) {
      getFoldingModel().runBatchFoldingOperation(new Runnable() {
        public void run() {
          myFoldingModel.flushCaretShift();
          region.setExpanded(true);
        }
      });
    }

    if (myMousePressedEvent != null && myMousePressedEvent.getClickCount() == 1 && myMousePressedInsideSelection) {
      getSelectionModel().removeSelection();
    }
  }

  public DataContext getDataContext() {
    return getProjectAwareDataContext(DataManager.getInstance().getDataContext(getContentComponent()));
  }

  public DataContext getProjectAwareDataContext(final DataContext original) {
    if (original.getData(DataConstants.PROJECT) == myProject) return original;

    return new DataContext() {
      public Object getData(String dataId) {
        if (DataConstants.PROJECT.equals(dataId)) {
          return myProject;
        }
        return original.getData(dataId);
      }
    };
  }


  public EditorMouseEventArea getMouseEventArea(MouseEvent e) {
    if (myGutterComponent != e.getSource()) return EditorMouseEventArea.EDITING_AREA;

    int x = myGutterComponent.convertX(e.getX());

    if (x >= myGutterComponent.getLineNumberAreaOffset() &&
        x < myGutterComponent.getLineNumberAreaOffset() + myGutterComponent.getLineNumberAreaWidth()) {
      return EditorMouseEventArea.LINE_NUMBERS_AREA;
    }

    if (x >= myGutterComponent.getAnnotationsAreaOffset() &&
        x <= myGutterComponent.getAnnotationsAreaOffset() + myGutterComponent.getAnnotationsAreaWidth()) {
      return EditorMouseEventArea.ANNOTATIONS_AREA;
    }

    if (x >= myGutterComponent.getLineMarkerAreaOffset() &&
        x < myGutterComponent.getLineMarkerAreaOffset() + myGutterComponent.getLineMarkerAreaWidth()) {
      return EditorMouseEventArea.LINE_MARKERS_AREA;
    }

    if (x >= myGutterComponent.getFoldingAreaOffset() &&
        x < myGutterComponent.getFoldingAreaOffset() + myGutterComponent.getFoldingAreaWidth()) {
      return EditorMouseEventArea.FOLDING_OUTLINE_AREA;
    }

    return null;
  }

  private void requestFocus() {
    myEditorComponent.requestFocus();
  }

  private void validateMousePointer(MouseEvent e) {
    if (e.getSource() == myGutterComponent) {
      FoldRegion foldingAtCursor = myGutterComponent.findFoldingAnchorAt(e.getX(), e.getY());
      myGutterComponent.setActiveFoldRegion(foldingAtCursor);
      if (foldingAtCursor != null) {
        myGutterComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        myGutterComponent.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }
    else {
      myGutterComponent.setActiveFoldRegion(null);
      if (getSelectionModel().hasSelection() &&
          (e.getModifiersEx() & (MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK)) == 0) {
        int offset = logicalPositionToOffset(xyToLogicalPosition(e.getPoint()));
        if (getSelectionModel().getSelectionStart() <= offset && offset < getSelectionModel().getSelectionEnd()) {
          myEditorComponent.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          return;
        }
      }
      myEditorComponent.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
    }
  }

  private void runMouseDraggedCommand(final MouseEvent e) {
    if (myCommandProcessor == null || myMousePressedEvent != null && myMousePressedEvent.isConsumed()) {
      return;
    }
    myCommandProcessor.executeCommand(myProject, new Runnable() {
      public void run() {
        processMouseDragged(e);
      }
    }, "", MOUSE_DRAGGED_GROUP);
  }

  private void processMouseDragged(MouseEvent e) {
    if (SwingUtilities.isRightMouseButton(e)) {
      return;
    }
    Rectangle rect = getScrollingModel().getVisibleArea();

    int dx = 0;
    int x = e.getX();

    if (e.getSource() == myGutterComponent) {
      x = 0;
    }

    if (x < rect.x) {
      dx = x - rect.x;
    }
    else {
      if (x > rect.x + rect.width) {
        dx = x - rect.x - rect.width;
      }
    }

    int dy = 0;
    int y = e.getY();
    if (y < rect.y) {
      dy = y - rect.y;
    }
    else {
      if (y > rect.y + rect.height) {
        dy = y - rect.y - rect.height;
      }
    }
    if (dx == 0 && dy == 0) {
      myScrollingTimer.stop();

      SelectionModel selectionModel = getSelectionModel();
      int oldSelectionStart = selectionModel.getLeadSelectionOffset();
      int oldCaretOffset = getCaretModel().getOffset();
      LogicalPosition oldLogicalCaret = getCaretModel().getLogicalPosition();
      moveCaretToScreenPos(x, y);
      getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

      int newCaretOffset = getCaretModel().getOffset();
      int caretShift = newCaretOffset - mySavedSelectionStart;

      if (myMousePressedEvent != null &&
          getMouseEventArea(myMousePressedEvent) != EditorMouseEventArea.EDITING_AREA &&
          getMouseEventArea(myMousePressedEvent) != EditorMouseEventArea.LINE_NUMBERS_AREA) {
        selectionModel.setSelection(oldSelectionStart, newCaretOffset);
      }
      else {
        if (isColumnMode()) {
          final LogicalPosition blockStart = selectionModel.hasBlockSelection()
                                             ? selectionModel.getBlockStart()
                                             : oldLogicalCaret;
          selectionModel.setBlockSelection(blockStart, getCaretModel().getLogicalPosition());
        }
        else {
          if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
            if (caretShift < 0) {
              int newSelection = newCaretOffset;
              if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                newSelection = mySelectionModel.getWordAtCaretStart();
              }
              else {
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                  newSelection = logicalPositionToOffset(
                    visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line, 0)));
                }
              }
              if (newSelection < 0) newSelection = newCaretOffset;
              selectionModel.setSelection(mySavedSelectionEnd, newSelection);
              getCaretModel().moveToOffset(newSelection);
            }
            else {
              int newSelection = newCaretOffset;
              if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                newSelection = mySelectionModel.getWordAtCaretEnd();
              }
              else {
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                  newSelection = logicalPositionToOffset(
                    visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line + 1, 0)));
                }
              }
              if (newSelection < 0) newSelection = newCaretOffset;
              selectionModel.setSelection(mySavedSelectionStart, newSelection);
              getCaretModel().moveToOffset(newSelection);
            }
            return;
          }

          if (!myMousePressedInsideSelection) {
            selectionModel.setSelection(oldSelectionStart, newCaretOffset);
          }
          else {
            if (caretShift != 0) {
              if (myMousePressedEvent != null) {
                if (mySettings.isDndEnabled()) {
                  boolean isCopy = isControlKeyDown(e) || isViewer() || !getDocument().isWritable();
                  mySavedCaretOffsetForDNDUndoHack = oldCaretOffset;
                  getContentComponent().getTransferHandler().exportAsDrag(getContentComponent(), e,
                                                                          isCopy
                                                                          ? TransferHandler.COPY
                                                                          : TransferHandler.MOVE);
                }
                else {
                  selectionModel.removeSelection();
                }
                myMousePressedEvent = null;
              }
            }
          }
        }
      }
    }
    else {
      myScrollingTimer.start(dx, dy);
    }
  }

  private static class RepaintCursorThread extends Thread {
    private long mySleepTime = 500;
    private boolean myIsBlinkCaret = true;
    private EditorImpl myEditor = null;
    private boolean isStopped = false;
    private MyRepaintRunnable myRepaintRunnable;

    public RepaintCursorThread() {
      super("EditorCaretThread");
      myRepaintRunnable = new MyRepaintRunnable();
    }

    private class MyRepaintRunnable implements Runnable {
      public void run() {
        if (myEditor != null) {
          myEditor.myCaretCursor.repaint();
        }
      }
    }

    public void setBlinkPeriod(int blinkPeriod) {
      mySleepTime = blinkPeriod > 10 ? blinkPeriod : 10;
    }

    public void setBlinkCaret(boolean value) {
      myIsBlinkCaret = value;
    }

    public synchronized void stopThread() {
      isStopped = true;
    }

    public void run() {
      while (true) {
        try {
          Thread.sleep(myIsBlinkCaret ? mySleepTime : 1000);
        }
        catch (InterruptedException e) {
        }

        synchronized (this) {
          if (isStopped) {
            break;
          }
        }

        if (myEditor == null) {
          continue;
        }
        CaretCursor activeCursor = myEditor.myCaretCursor;

        long time = System.currentTimeMillis();
        time -= activeCursor.myStartTime;

        if (time > mySleepTime) {
          boolean toRepaint = true;
          if (myIsBlinkCaret) {
            activeCursor.isVisible = !activeCursor.isVisible;
          }
          else {

            toRepaint = !activeCursor.isVisible;
            activeCursor.isVisible = true;
          }

          if (toRepaint) {
            SwingUtilities.invokeLater(myRepaintRunnable);
          }
        }
      }
    }
  }

  void updateCaretCursor() {
    if (!IJSwingUtilities.hasFocus(getContentComponent())) {
      stopOptimizedScrolling();
    }

    if (myCursorUpdater == null) {
      myCursorUpdater = new Runnable() {
        public void run() {
          if (myCursorUpdater == null) return;
          myCursorUpdater = null;

          if (getDocument().getMarkupModel(myProject) == null) return;

          VisualPosition caretPosition = getCaretModel().getVisualPosition();
          Point pos1 = visualPositionToXY(caretPosition);
          Point pos2 = visualPositionToXY(new VisualPosition(caretPosition.line, caretPosition.column + 1));
          myCaretCursor.setPosition(pos1, pos2.x - pos1.x);
        }
      };
    }
  }

  public void setCaretVisible(boolean b) {
    if (b) {
      myCaretCursor.activate();
    }
    else {
      myCaretCursor.passivate();
    }
  }

  public void addFocusListener(FocusChangeListener listener) {
    myFocusListeners.add(listener);
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isOneLineMode() {
    return myIsOneLineMode;
  }

  public void setOneLineMode(boolean isOneLineMode) {
    myIsOneLineMode = isOneLineMode;
    getScrollPane().setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
    reinitSettings();
  }

  public void stopOptimizedScrolling() {
    if (SystemInfo.isMac) return;
    myEditorComponent.setOpaque(false);
  }

  private void startOptimizedScrolling() {
    if (SystemInfo.isMac) return;
    myEditorComponent.setOpaque(true);
  }

  private class CaretCursor {
    private Point myLocation;
    private int myWidth;
    private boolean isVisible = true;
    private long myStartTime = 0;

    public CaretCursor() {
      myLocation = new Point(0, 0);
    }

    public void activate() {
      synchronized (ourCaretThread) {
        ourCaretThread.myEditor = EditorImpl.this;
        ourCaretThread.setBlinkCaret(mySettings.isBlinkCaret());
        ourCaretThread.setBlinkPeriod(mySettings.getCaretBlinkPeriod());
        isVisible = true;
      }
    }

    public void passivate() {
      synchronized (ourCaretThread) {
        isVisible = false;
      }
    }

    public void setPosition(Point location, int width) {
      myStartTime = System.currentTimeMillis();
      myLocation = location;
      isVisible = true;
      myWidth = Math.max(width, 2);
      repaint();
    }

    private void repaint() {
      myEditorComponent.repaintEditorComponent(myLocation.x,
                                                               myLocation.y,
                                                               myWidth,
                                                               getLineHeight());
    }

    public void paint(Graphics g) {
      if (!isVisible || !IJSwingUtilities.hasFocus(getContentComponent()) || isRendererMode()) return;

      int x = myLocation.x;
      int lineHeight = getLineHeight();
      int y = myLocation.y;

      Rectangle viewRect = getScrollingModel().getVisibleArea();
      if (x - viewRect.x < 0) {
        return;
      }


      g.setColor(myScheme.getColor(EditorColors.CARET_COLOR));
      if (!SystemInfo.isMac) {
        Color background = myScheme.getColor(EditorColors.CARET_ROW_COLOR);
        if (background == null) background = getBackroundColor();
        g.setXORMode(background != null ? background : Color.white);
      }

      if (EditorImpl.this.myIsInsertMode != mySettings.isBlockCursor()) {
        for (int i = 0; i < mySettings.getLineCursorWidth(); i++) {
          g.drawLine(x + i, y, x + i, y + lineHeight - 1);
        }
      }
      else {
        g.fillRect(x, y, myWidth, lineHeight - 1);
      }

      g.setPaintMode();
    }
  }

  private class ScrollingTimer {
    Timer myTimer;
    private static final int TIMER_PERIOD = 100;
    private static final int CYCLE_SIZE = 20;
    private int myXCycles;
    private int myYCycles;
    private int myDx;
    private int myDy;
    private int xPassedCycles = 0;
    private int yPassedCycles = 0;

    public void start(int dx, int dy) {
      myDx = 0;
      myDy = 0;
      if (dx > 0) {
        myXCycles = CYCLE_SIZE / dx + 1;
        myDx = 1 + dx / CYCLE_SIZE;
      }
      else {
        if (dx < 0) {
          myXCycles = -CYCLE_SIZE / dx + 1;
          myDx = -1 + dx / CYCLE_SIZE;
        }
      }

      if (dy > 0) {
        myYCycles = CYCLE_SIZE / dy + 1;
        myDy = 1 + dy / CYCLE_SIZE;
      }
      else {
        if (dy < 0) {
          myYCycles = -CYCLE_SIZE / dy + 1;
          myDy = -1 + dy / CYCLE_SIZE;
        }
      }

      if (myTimer != null) {
        return;
      }
      myTimer = new Timer(TIMER_PERIOD,
                          new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                              myCommandProcessor.executeCommand(myProject, new Runnable() {
                                public void run() {
                                  int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();
                                  LogicalPosition caretPosition = getCaretModel().getLogicalPosition();
                                  int columnNumber = caretPosition.column;
                                  xPassedCycles++;
                                  if (xPassedCycles >= myXCycles) {
                                    xPassedCycles = 0;
                                    columnNumber += myDx;
                                  }

                                  int lineNumber = caretPosition.line;
                                  yPassedCycles++;
                                  if (yPassedCycles >= myYCycles) {
                                    yPassedCycles = 0;
                                    lineNumber += myDy;
                                  }

                                  LogicalPosition pos = new LogicalPosition(lineNumber, columnNumber);
                                  getCaretModel().moveToLogicalPosition(pos);
                                  getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

                                  int newCaretOffset = getCaretModel().getOffset();
                                  int caretShift = newCaretOffset - mySavedSelectionStart;

                                  if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
                                    if (caretShift < 0) {
                                      int newSelection = newCaretOffset;
                                      if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                                        newSelection = mySelectionModel.getWordAtCaretStart();
                                      }
                                      else {
                                        if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                                          newSelection = logicalPositionToOffset(visualToLogicalPosition(
                                            new VisualPosition(getCaretModel().getVisualPosition().line, 0)));
                                        }
                                      }
                                      if (newSelection < 0) newSelection = newCaretOffset;
                                      mySelectionModel.setSelection(mySavedSelectionEnd, newSelection);
                                      getCaretModel().moveToOffset(newSelection);
                                    }
                                    else {
                                      int newSelection = newCaretOffset;
                                      if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                                        newSelection = mySelectionModel.getWordAtCaretEnd();
                                      }
                                      else {
                                        if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                                          newSelection = logicalPositionToOffset(visualToLogicalPosition(
                                            new VisualPosition(getCaretModel().getVisualPosition().line + 1, 0)));
                                        }
                                      }
                                      if (newSelection < 0) newSelection = newCaretOffset;
                                      mySelectionModel.setSelection(mySavedSelectionStart, newSelection);
                                      getCaretModel().moveToOffset(newSelection);
                                    }
                                    return;
                                  }

                                  if (mySelectionModel.hasBlockSelection()) {
                                    mySelectionModel.setBlockSelection(mySelectionModel.getBlockStart(),
                                                                       getCaretModel().getLogicalPosition());
                                  }
                                  else {
                                    mySelectionModel.setSelection(oldSelectionStart, getCaretModel().getOffset());
                                  }
                                }
                              },
                                                                "Move Cursor", null);
                            }
                          });
      myTimer.start();
    }

    public void stop() {
      if (myTimer != null) {
        myTimer.stop();
        myTimer = null;
      }
    }

  }

  class MyScrollBar extends JScrollBar {

    public MyScrollBar(int orientation) {
      super(orientation);
    }

    /**
     * This is helper method. It returns height of the top (descrease) scrollbar
     * button. Please note, that it's possible to return real height only if scrollbar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getDecScrollButtonHeight() {
      ScrollBarUI ui = getUI();
      Insets insets = getInsets();
      if (ui instanceof BasicScrollBarUI) {
        try {
          Field decrButtonField = BasicScrollBarUI.class.getDeclaredField("decrButton");
          decrButtonField.setAccessible(true);
          JButton decrButtonValue = (JButton)decrButtonField.get(ui);
          LOG.assertTrue(decrButtonValue != null);
          return insets.top + decrButtonValue.getHeight();
        }
        catch (Exception exc) {
          exc.printStackTrace();
          throw new IllegalStateException(exc.getMessage());
        }
      }
      else {
        return insets.top + 15;
      }
    }

    /**
     * This is helper method. It returns height of the bottom (increase) scrollbar
     * button. Please note, that it's possible to return real height only if scrollbar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getIncScrollButtonHeight() {
      ScrollBarUI ui = getUI();
      Insets insets = getInsets();
      if (ui instanceof BasicScrollBarUI) {
        try {
          Field incrButtonField = BasicScrollBarUI.class.getDeclaredField("incrButton");
          incrButtonField.setAccessible(true);
          JButton incrButtonValue = (JButton)incrButtonField.get(ui);
          LOG.assertTrue(incrButtonValue != null);
          return insets.bottom + incrButtonValue.getHeight();
        }
        catch (Exception exc) {
          exc.printStackTrace();
          throw new IllegalStateException(exc.getMessage());
        }
      }
      else if ("apple.laf.AquaScrollBarUI".equals(ui.getClass().getName())) {
        return insets.bottom + 30;
      }
      else {
        return insets.bottom + 15;
      }
    }

    public int getUnitIncrement(int direction) {
      JViewport vp = myScrollPane.getViewport();
      Rectangle vr = vp.getViewRect();
      return myEditorComponent.getScrollableUnitIncrement(vr, SwingConstants.VERTICAL, direction);
    }

    public int getBlockIncrement(int direction) {
      JViewport vp = myScrollPane.getViewport();
      Rectangle vr = vp.getViewRect();
      return myEditorComponent.getScrollableBlockIncrement(vr, SwingConstants.VERTICAL, direction);
    }
  }

  private MyEditable getViewer() {
    if (myEditable == null) {
      myEditable = new MyEditable();
    }
    return myEditable;
  }

  public CopyProvider getCopyProvider() {
    return getViewer();
  }

  public CutProvider getCutProvider() {
    return getViewer();
  }

  public PasteProvider getPasteProvider() {

    return getViewer();
  }

  public DeleteProvider getDeleteProvider() {
    return getViewer();
  }

  private class MyEditable implements CutProvider, CopyProvider, PasteProvider, DeleteProvider {
    public void performCopy(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_COPY, dataContext);
    }

    public boolean isCopyEnabled(DataContext dataContext) {
      return true;
    }

    public void performCut(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_CUT, dataContext);
    }

    public boolean isCutEnabled(DataContext dataContext) {
      return !isViewer() && getDocument().isWritable();
    }

    public void performPaste(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_PASTE, dataContext);
    }

    public boolean isPastePossible(DataContext dataContext) {
      // Copy of isPasteEnabled. See interface method javadoc.
      return !isViewer() && getDocument().isWritable();
    }

    public boolean isPasteEnabled(DataContext dataContext) {
      return !isViewer() && getDocument().isWritable();
    }

    public void deleteElement(DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_DELETE, dataContext);
    }

    public boolean canDeleteElement(DataContext dataContext) {
      return !isViewer() && getDocument().isWritable();
    }

    private void executeAction(String actionId, DataContext dataContext) {
      EditorAction action = (EditorAction)ActionManager.getInstance().getAction(actionId);
      if (action != null) {
        action.actionPerformed(EditorImpl.this, dataContext);
      }
    }
  }

  public void setColorsScheme(EditorColorsScheme scheme) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myScheme = scheme;
    reinitSettings();
  }

  public EditorColorsScheme getColorsScheme() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myScheme;
  }

  public void setVerticalScrollbarOrientation(int type) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int currentHorOffset = myScrollingModel.getHorizontalScrollOffset();
    myScrollbarOrientation = type;
    if (type == EditorEx.VERTICAL_SCROLLBAR_LEFT) {
      myScrollPane.setLayout(new LeftHandScrollbarLayout());
    }
    else {
      myScrollPane.setLayout(new ScrollPaneLayout());
    }
    myScrollingModel.scrollHorizontally(currentHorOffset);
  }

  public void setVerticalScrollbarVisible(boolean b) {
    if (b) {
      myScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    }
    else {
      myScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
    }
  }

  public void setHorizontalScrollbarVisible(boolean b) {
    if (b) {
      myScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }
    else {
      myScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }
  }

  public int getVerticalScrollbarOrientation() {
    return myScrollbarOrientation;
  }

  public MyScrollBar getVerticalScrollBar() {
    return myVerticalScrollBar;
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private int getMouseSelectionState() {
    return myMouseSelectionState;
  }

  private void setMouseSelectionState(int mouseSelectionState) {
    myMouseSelectionState = mouseSelectionState;
    myMouseSelectionChangeTimestamp = System.currentTimeMillis();
  }


  public void replaceInputMethodText(InputMethodEvent e) {
    getInputMethodRequests();
    myInputMethodRequestsHandler.replaceInputMethodText(e);
  }

  public void inputMethodCaretPositionChanged(InputMethodEvent e) {
    getInputMethodRequests();
    myInputMethodRequestsHandler.setInputMethodCaretPosition(e);
  }

  public InputMethodRequests getInputMethodRequests() {
    if (myInputMethodRequestsHandler == null) {
      myInputMethodRequestsHandler = new MyInputMethodHandler();
      myInputMethodRequestsSwingWrapper = new MyInputMethodHandleSwingThreadWrapper(myInputMethodRequestsHandler);
    }
    return myInputMethodRequestsSwingWrapper;
  }

  public boolean processKeyTyped(KeyEvent e) {
    if (e.getID() != KeyEvent.KEY_TYPED) return false;
    char c = e.getKeyChar();
    if (isReallyTypedEvent(e) && c >= 0x20 && c != 0x7F) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
      processKeyTyped(c);
      return true;
    }
    else {
      return false;
    }
  }

  private static boolean isReallyTypedEvent(KeyEvent e) {
    int modifiers = e.getModifiers();
    if (SystemInfo.isMac) {
      return !e.isMetaDown() && !e.isControlDown();
    }

    return (modifiers & ActionEvent.ALT_MASK) == (modifiers & ActionEvent.CTRL_MASK);
  }

  public void beforeModalityStateChanged() {
    myScrollingModel.beforeModalityStateChanged();
  }

  private static class MyInputMethodHandleSwingThreadWrapper implements InputMethodRequests {
    private InputMethodRequests myDelegate;

    public MyInputMethodHandleSwingThreadWrapper(InputMethodRequests delegate) {
      myDelegate = delegate;
    }

    public Rectangle getTextLocation(final TextHitInfo offset) {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getTextLocation(offset);

      final Rectangle[] r = new Rectangle[1];
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getTextLocation(offset);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public TextHitInfo getLocationOffset(final int x, final int y) {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getLocationOffset(x, y);

      final TextHitInfo[] r = new TextHitInfo[1];
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getLocationOffset(x, y);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public int getInsertPositionOffset() {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getInsertPositionOffset();

      final int[] r = new int[1];
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getInsertPositionOffset();
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public AttributedCharacterIterator getCommittedText(final int beginIndex, final int endIndex,
                                                        final AttributedCharacterIterator.Attribute[] attributes) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        return myDelegate.getCommittedText(beginIndex,
                                           endIndex,
                                           attributes);
      }
      final AttributedCharacterIterator[] r = new AttributedCharacterIterator[1];
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getCommittedText(beginIndex, endIndex, attributes);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public int getCommittedTextLength() {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getCommittedTextLength();
      final int[] r = new int[1];
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getCommittedTextLength();
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }

    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    public AttributedCharacterIterator getSelectedText(final AttributedCharacterIterator.Attribute[] attributes) {
      if (ApplicationManager.getApplication().isDispatchThread()) return myDelegate.getSelectedText(attributes);

      final AttributedCharacterIterator[] r = new AttributedCharacterIterator[1];
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            r[0] = myDelegate.getSelectedText(attributes);
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      return r[0];
    }
  }

  private class MyInputMethodHandler implements InputMethodRequests {
    private String composedText;
    private int composedTextStart;
    private int composedTextEnd;

    public Rectangle getTextLocation(TextHitInfo offset) {
      Point caret = logicalPositionToXY(getCaretModel().getLogicalPosition());
      Rectangle r = new Rectangle(caret, new Dimension(1, getLineHeight()));
      Point p = getContentComponent().getLocationOnScreen();
      r.translate(p.x, p.y);

      return r;
    }

    public TextHitInfo getLocationOffset(int x, int y) {
      if (composedText != null) {
        Point p = getContentComponent().getLocationOnScreen();
        p.x = x - p.x;
        p.y = y - p.y;
        int pos = logicalPositionToOffset(xyToLogicalPosition(p));
        if (pos >= composedTextStart && pos <= composedTextEnd) {
          return TextHitInfo.leading(pos - composedTextStart);
        }
      }
      return null;
    }

    public int getInsertPositionOffset() {
      int composedStartIndex = 0;
      int composedEndIndex = 0;
      if (composedText != null) {
        composedStartIndex = composedTextStart;
        composedEndIndex = composedTextEnd;
      }

      int caretIndex = getCaretModel().getOffset();

      if (caretIndex < composedStartIndex) {
        return caretIndex;
      }
      else {
        if (caretIndex < composedEndIndex) {
          return composedStartIndex;
        }
        else {
          return caretIndex - (composedEndIndex - composedStartIndex);
        }
      }
    }

    private String getText(int startIdx, int endIdx) {
      CharSequence chars = getDocument().getCharsSequence();
      return chars.subSequence(startIdx, endIdx).toString();
    }

    public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex,
                                                        AttributedCharacterIterator.Attribute[] attributes) {
      int composedStartIndex = 0;
      int composedEndIndex = 0;
      if (composedText != null) {
        composedStartIndex = composedTextStart;
        composedEndIndex = composedTextEnd;
      }

      String committed;
      if (beginIndex < composedStartIndex) {
        if (endIndex <= composedStartIndex) {
          committed = getText(beginIndex, endIndex - beginIndex);
        }
        else {
          int firstPartLength = composedStartIndex - beginIndex;
          committed = getText(beginIndex, firstPartLength) +
                      getText(composedEndIndex, endIndex - beginIndex - firstPartLength);
        }
      }
      else {
        committed = getText(beginIndex + (composedEndIndex - composedStartIndex),
                            endIndex - beginIndex);
      }

      return new AttributedString(committed).getIterator();
    }

    public int getCommittedTextLength() {
      int length = getDocument().getTextLength();
      if (composedText != null) {
        length -= composedText.length();
      }
      return length;
    }

    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
      String text = getSelectionModel().getSelectedText();
      return text == null ? null : new AttributedString(text).getIterator();
    }

    private void createComposedString(int composedIndex, AttributedCharacterIterator text) {
      StringBuffer strBuf = new StringBuffer();

      // create attributed string with no attributes
      for (char c = text.setIndex(composedIndex);
           c != CharacterIterator.DONE; c = text.next()) {
        strBuf.append(c);
      }

      composedText = new String(strBuf);
    }

    private void setInputMethodCaretPosition(InputMethodEvent e) {
      if (composedText != null) {
        int dot;
        dot = composedTextStart;

        TextHitInfo caretPos = e.getCaret();
        if (caretPos != null) {
          int index = caretPos.getInsertionIndex();
          dot += index;
        }

        getCaretModel().moveToOffset(dot);
        getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }

    private void runUndoTransparent(final Runnable runnable) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(runnable);
            }
          }, "", null);
        }
      });
    }

    private void replaceInputMethodText(InputMethodEvent e) {
      int commitCount = e.getCommittedCharacterCount();
      AttributedCharacterIterator text = e.getText();
      int composedTextIndex;

      // old composed text deletion
      final Document doc = getDocument();

      if (composedText != null) {
        if (!isViewer() && doc.isWritable()) {
          runUndoTransparent(new Runnable() {
            public void run() {
              doc.deleteString(Math.max(0, composedTextStart), Math.min(composedTextEnd, doc.getTextLength()));
            }
          });
        }
        composedText = null;
      }

      if (text != null) {
        text.first();

        // committed text insertion
        if (commitCount > 0) {
          for (char c = text.current(); commitCount > 0; c = text.next(), commitCount--) {
            if (c >= 0x20 && c != 0x7F) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
              processKeyTyped(c);
            }
          }
        }

        // new composed text insertion
        if (!isViewer() && doc.isWritable()) {
          composedTextIndex = text.getIndex();
          if (composedTextIndex < text.getEndIndex()) {
            createComposedString(composedTextIndex, text);

            runUndoTransparent(new Runnable() {
              public void run() {
                EditorModificationUtil.insertStringAtCaret(EditorImpl.this, composedText, false, false);
              }
            });

            composedTextStart = getCaretModel().getOffset();
            composedTextEnd = getCaretModel().getOffset() + composedText.length();
          }
        }
      }
    }
  }

  private class MyMouseAdapter extends MouseAdapter {
    public void mousePressed(MouseEvent e) {
      runMousePressedCommand(e);
    }

    public void mouseReleased(MouseEvent e) {
      runMouseReleasedCommand(e);
      if (!e.isConsumed() && myMousePressedEvent != null && !myMousePressedEvent.isConsumed() &&
          Math.abs(e.getX() - myMousePressedEvent.getX()) < getSpaceWidth(Font.PLAIN) &&
          Math.abs(e.getY() - myMousePressedEvent.getY()) < getLineHeight()) {
        runMouseClickedCommand(e);
      }
      myMousePressedEvent = null;
    }

    public void mouseEntered(MouseEvent e) {
      runMouseEnteredCommand(e);
    }

    public void mouseExited(MouseEvent e) {
      runMouseExitedCommand(e);
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
        myGutterComponent.mouseExited(e);
      }

      HintManager.getInstance().getTooltipController().cancelTooltip(FOLDING_TOOLTIP_GROUP);
    }
  }

  private static final TooltipGroup FOLDING_TOOLTIP_GROUP = new TooltipGroup("FOLDING_TOOLTIP_GROUP", 10);

  private class MyMouseMotionListener implements MouseMotionListener {
    public void mouseDragged(MouseEvent e) {
      validateMousePointer(e);
      runMouseDraggedCommand(e);
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
        myGutterComponent.mouseDragged(e);
      }

      EditorMouseMotionListener[] listeners = myMouseMotionListeners.toArray(
        new EditorMouseMotionListener[myMouseMotionListeners.size()]);
      for (int i = 0; i < listeners.length; i++) {
        listeners[i].mouseDragged(event);
      }
    }

    public void mouseMoved(MouseEvent e) {
      validateMousePointer(e);
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (e.getSource() == myGutterComponent) {
        myGutterComponent.mouseMoved(e);
      }

      if (event.getArea() == EditorMouseEventArea.EDITING_AREA) {
        FoldRegion fold = myFoldingModel.getFoldingPlaceholderAt(e.getPoint());
        TooltipController controller = HintManager.getInstance().getTooltipController();
        if (fold != null) {
          DocumentFragment range = new DocumentFragment(myDocument, fold.getStartOffset(), fold.getEndOffset());
          final Point p = SwingUtilities.convertPoint((Component)e.getSource(),
                                                      e.getPoint(),
                                                      getComponent().getRootPane().getLayeredPane());
          controller.showTooltip(EditorImpl.this, p, range, false, FOLDING_TOOLTIP_GROUP);
        }
        else {
          controller.cancelTooltip(FOLDING_TOOLTIP_GROUP);
        }
      }

      EditorMouseMotionListener[] listeners = myMouseMotionListeners.toArray(
        new EditorMouseMotionListener[myMouseMotionListeners.size()]);
      for (int i = 0; i < listeners.length; i++) {
        listeners[i].mouseMoved(event);
      }
    }
  }

  private class MyColorSchemeDelegate implements EditorColorsScheme {
    private HashMap<TextAttributesKey, TextAttributes> myOwnAttributes = new HashMap<TextAttributesKey, TextAttributes>();
    private HashMap<ColorKey, Color> myOwnColors = new HashMap<ColorKey, Color>();
    private HashMap<EditorFontType, Font> myFontsMap = null;
    private Integer myFontSize = null;
    private String myFaceName = null;


    private EditorColorsScheme getGlobal() {
      return EditorColorsManager.getInstance().getGlobalScheme();
    }

    public String getName() {
      return getGlobal().getName();
    }

    protected void initFonts() {
      String editorFontName = getEditorFontName();
      int editorFontSize = getEditorFontSize();

      myFontsMap = new HashMap<EditorFontType, Font>();

      Font plainFont = new Font(editorFontName, Font.PLAIN, editorFontSize);
      Font boldFont = new Font(editorFontName, Font.BOLD, editorFontSize);
      Font italicFont = new Font(editorFontName, Font.ITALIC, editorFontSize);
      Font boldItalicFont = new Font(editorFontName, Font.BOLD + Font.ITALIC, editorFontSize);

      myFontsMap.put(EditorFontType.PLAIN, plainFont);
      myFontsMap.put(EditorFontType.BOLD, boldFont);
      myFontsMap.put(EditorFontType.ITALIC, italicFont);
      myFontsMap.put(EditorFontType.BOLD_ITALIC, boldItalicFont);

      reinitSettings();
    }

    public void setName(String name) {
      getGlobal().setName(name);
    }

    public TextAttributes getAttributes(TextAttributesKey key) {
      if (myOwnAttributes.containsKey(key)) return myOwnAttributes.get(key);
      return getGlobal().getAttributes(key);
    }

    public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
      myOwnAttributes.put(key, attributes);
    }

    public Color getColor(ColorKey key) {
      if (myOwnColors.containsKey(key)) return myOwnColors.get(key);
      return getGlobal().getColor(key);
    }

    public void setColor(ColorKey key, Color color) {
      myOwnColors.put(key, color);

      // These two are here because those attributes are cached and I do not whant the clients to call editor's reinit
      // settings in this case.
      myCaretModel.reinitSettings();
      mySelectionModel.reinitSettings();
    }

    public int getEditorFontSize() {
      if (myFontSize != null) return myFontSize.intValue();
      return getGlobal().getEditorFontSize();
    }

    public void setEditorFontSize(int fontSize) {
      if (fontSize < 8) fontSize = 8;
      if (fontSize > 20) fontSize = 20;
      myFontSize = new Integer(fontSize);
      initFonts();
    }

    public String getEditorFontName() {
      if (myFaceName != null) return myFaceName;
      return getGlobal().getEditorFontName();
    }

    public void setEditorFontName(String fontName) {
      myFaceName = fontName;
      initFonts();
    }

    public Font getFont(EditorFontType key) {
      if (myFontsMap != null) {
        Font font = myFontsMap.get(key);
        if (font != null) return font;
      }
      return getGlobal().getFont(key);
    }

    public void setFont(EditorFontType key, Font font) {
      if (myFontsMap == null) {
        initFonts();
      }
      myFontsMap.put(key, font);
      reinitSettings();
    }

    public float getLineSpacing() {
      return getGlobal().getLineSpacing();
    }

    public void setLineSpacing(float lineSpacing) {
      getGlobal().setLineSpacing(lineSpacing);
    }

    public Object clone() {
      return null;
    }

    public void readExternal(Element element) throws InvalidDataException {
    }

    public void writeExternal(Element element) throws WriteExternalException {
    }
  }

  private static class MyTransferHandler extends TransferHandler {
    private RangeMarker myDraggedRange = null;

    private static Editor getEditor(JComponent comp) {
      EditorComponentImpl editorComponent = (EditorComponentImpl)comp;
      return editorComponent.getEditor();
    }

    public boolean importData(final JComponent comp, final Transferable t) {
      final EditorImpl editor = (EditorImpl)getEditor(comp);

      final int caretOffset = editor.getCaretModel().getOffset();
      if (myDraggedRange != null &&
          myDraggedRange.getStartOffset() <= caretOffset &&
          caretOffset < myDraggedRange.getEndOffset()) {
        return false;
      }

      if (myDraggedRange != null) {
        editor.getCaretModel().moveToOffset(editor.mySavedCaretOffsetForDNDUndoHack);
      }

      CommandProcessor.getInstance().executeCommand(editor.myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                final int offset;
                editor.getSelectionModel().removeSelection();
                if (myDraggedRange != null) {
                  editor.getCaretModel().moveToOffset(caretOffset);
                  offset = caretOffset;
                }
                else {
                  offset = editor.getCaretModel().getOffset();
                }
                if (editor.getDocument().getRangeGuard(offset, offset) != null) return;

                EditorActionHandler pasteHandler = EditorActionManager.getInstance().getActionHandler(
                  IdeActions.ACTION_EDITOR_PASTE);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                Transferable backup = clipboard.getContents(this);
                ClipboardOwner clipboardOwner = new ClipboardOwner() {
                  public void lostOwnership(Clipboard clipboard, Transferable contents) {
                  }
                };
                clipboard.setContents(t, clipboardOwner);

                editor.putUserData(LAST_PASTED_REGION, null);
                pasteHandler.execute(editor, editor.getDataContext());
                clipboard.setContents(backup, clipboardOwner);

                TextRange range = editor.getUserData(LAST_PASTED_REGION);
                if (range != null) {
                  editor.getCaretModel().moveToOffset(range.getStartOffset());
                  editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
                }
              }
              catch (Exception exception) {
                LOG.error(exception);
              }
            }
          });
        }
      }, "Paste", DND_COMMAND_KEY);

      return true;
    }

    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
      Editor editor = getEditor(comp);
      if (editor.isViewer()) return false;
      if (!editor.getDocument().isWritable()) return false;

      int offset = editor.getCaretModel().getOffset();
      if (editor.getDocument().getRangeGuard(offset, offset) != null) return false;

      for (int i = 0; i < transferFlavors.length; i++) {
        DataFlavor transferFlavor = transferFlavors[i];
        if (transferFlavor.equals(DataFlavor.stringFlavor)) return true;
      }

      return false;
    }

    protected Transferable createTransferable(JComponent c) {
      Editor editor = getEditor(c);
      String s = editor.getSelectionModel().getSelectedText();
      if (s == null) return null;
      int selectionStart = editor.getSelectionModel().getSelectionStart();
      int selectionEnd = editor.getSelectionModel().getSelectionEnd();
      myDraggedRange = editor.getDocument().createRangeMarker(selectionStart, selectionEnd);

      return new StringSelection(s);
    }

    public int getSourceActions(JComponent c) {
      return COPY_OR_MOVE;
    }

    protected void exportDone(final JComponent source, Transferable data, int action) {
      if (data == null) return;

      if (action == MOVE && !getEditor(source).isViewer() && getEditor(source).getDocument().isWritable()) {
        CommandProcessor.getInstance().executeCommand(((EditorImpl)getEditor(source)).myProject, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                Document doc = getEditor(source).getDocument();
                doc.startGuardedBlockChecking();
                try {
                  doc.deleteString(myDraggedRange.getStartOffset(), myDraggedRange.getEndOffset());
                }
                catch (ReadOnlyFragmentModificationException e) {
                  EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
                }
                finally {
                  doc.stopGuardedBlockChecking();
                }
              }
            });
          }
        }, "Move selection", DND_COMMAND_KEY);
      }

      myDraggedRange = null;
    }
  }

  class EditorDocumentAdapter extends DocumentAdapter {
    public void beforeDocumentChange(DocumentEvent e) {
      beforeChangedUpdate(e);
    }

    public void documentChanged(DocumentEvent e) {
      changedUpdate(e);
    }
  }

  private class EditorSizeContainer {
    private TIntArrayList myLineWidths;
    private boolean myIsDirty;
    private int myOldEndLine;
    private Dimension mySize;

    public void reset() {
      int visLinesCount = getVisibleLineCount();
      myLineWidths = new TIntArrayList(visLinesCount + 300);
      int[] values = new int[visLinesCount];
      Arrays.fill(values, -1);
      myLineWidths.add(values);
      myIsDirty = true;
    }

    public void beforeChange(DocumentEvent e) {
      myOldEndLine = offsetToVisualPosition(e.getOffset() + e.getOldLength()).line;
    }

    public void changedUpdate(DocumentEvent e) {
      int startLine = offsetToVisualPosition(e.getOffset()).line;
      int newEndLine = offsetToVisualPosition(e.getOffset() + e.getNewLength()).line;
      int oldEndLine = myOldEndLine;

      if (myLineWidths.size() == 0) {
        reset();
      }
      else {
        int min = Math.min(oldEndLine, newEndLine);
        for (int i = startLine; i <= min; i++) myLineWidths.set(i, -1);
        if (newEndLine > oldEndLine) {
          int[] delta = new int[newEndLine - oldEndLine];
          Arrays.fill(delta, -1);
          myLineWidths.insert(oldEndLine + 1, delta);
        }
        else if (oldEndLine > newEndLine) {
          myLineWidths.remove(newEndLine + 1, oldEndLine - newEndLine);
        }
        myIsDirty = true;
      }
    }

    private void validateSizes() {
      if (!myIsDirty) return;

      CharSequence text = myDocument.getCharsNoThreadCheck();
      int lineCount = myLineWidths.size();
      int end = myDocument.getTextLength();
      int x = 0;
      for (int line = 0; line < lineCount; line++) {
        if (myLineWidths.getQuick(line) != -1) continue;
        x = 0;
        int offset = logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(line, 0)));

        if (offset >= myDocument.getTextLength()) {
          myLineWidths.set(line, 0);
          break;
        }

        IterationState state = new IterationState(EditorImpl.this, offset, false);
        int fontType = state.getMergedAttributes().getFontType();

        while (offset < end) {
          char c = text.charAt(offset);
          if (offset >= state.getEndOffset()) {
            state.advance();
            fontType = state.getMergedAttributes().getFontType();
          }

          FoldRegion collapsed = state.getCurrentFold();
          if (collapsed != null) {
            String placeholder = collapsed.getPlaceholderText();
            for (int i = 0; i < placeholder.length(); i++) {
              x += charWidth(fontType, placeholder.charAt(i));
            }
            offset = collapsed.getEndOffset();
          }
          else {
            if (c == '\t') {
              x = nextTabStop(x);
              offset++;
            }
            else {
              if (c == '\n') {
                myLineWidths.set(line, x);
                if (line + 1 >= lineCount || myLineWidths.getQuick(line + 1) != -1) break;
                offset++;
                x = 0;
                line++;
              }
              else {
                x += charWidth(fontType, c);
                offset++;
              }
            }
          }
        }
      }

      if (lineCount > 0) {
        myLineWidths.set(lineCount - 1, x);    // Last line can be non-zero length and won't be caught by in-loop procedure since latter only react on \n's
      }

      int maxWidth = 0;
      for (int i = 0; i < lineCount; i++) {
        maxWidth = Math.max(maxWidth, myLineWidths.getQuick(i));
      }

      mySize = new Dimension(maxWidth, getLineHeight() * lineCount);

      myIsDirty = false;
    }

    public Dimension getContentSize() {
      validateSizes();
      return mySize;
    }
  }

  public EditorGutter getGutter() {
    return getGutterComponentEx();
  }
}
