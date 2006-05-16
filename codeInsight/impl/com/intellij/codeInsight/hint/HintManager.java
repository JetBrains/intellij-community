package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupAdapter;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.ui.Hint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListenerUtil;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

public class HintManager implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.HintManager");

  public static final short ABOVE = 1;
  public static final short UNDER = 2;
  public static final short LEFT = 3;
  public static final short RIGHT = 4;
  public static final short RIGHT_UNDER = 5;

  public static final int HIDE_BY_ESCAPE = 0x01;
  public static final int HIDE_BY_ANY_KEY = 0x02;
  public static final int HIDE_BY_LOOKUP_ITEM_CHANGE = 0x04;
  public static final int HIDE_BY_TEXT_CHANGE = 0x08;
  public static final int HIDE_BY_OTHER_HINT = 0x10;
  public static final int HIDE_BY_SCROLLING = 0x20;
  public static final int HIDE_IF_OUT_OF_EDITOR = 0x40;
  public static final int UPDATE_BY_SCROLLING = 0x80;

  private TooltipController myTooltipController;

  private AnActionListener myAnActionListener;
  private final MyEditorManagerListener myEditorManagerListener;
  private EditorMouseAdapter myEditorMouseListener;
  private FocusListener myEditorFocusListener;
  private DocumentListener myEditorDocumentListener;
  private VisibleAreaListener myVisibleAreaListener;
  private CaretListener myCaretMoveListener;

  private LightweightHint myQuestionHint = null;
  private QuestionAction myQuestionAction = null;

  private List<HintInfo> myHintsStack = new ArrayList<HintInfo>(); // Vector of HintInfo
  private Editor myLastEditor = null;
  private Alarm myHideAlarm = new Alarm();

  public static interface ActionToIgnore {
  }

  private static class HintInfo {
    final LightweightHint hint;
    final int flags;
    private boolean reviveOnEditorChange;

    public HintInfo(LightweightHint hint, int flags, boolean reviveOnEditorChange) {
      this.hint = hint;
      this.flags = flags;
      this.reviveOnEditorChange = reviveOnEditorChange;
    }
  }

  public static HintManager getInstance() {
    return ApplicationManager.getApplication().getComponent(HintManager.class);
  }

  public HintManager(ActionManagerEx actionManagerEx, ProjectManager projectManager, EditorActionManager editorActionManager) {
    myEditorManagerListener = new MyEditorManagerListener();

    myAnActionListener = new MyAnActionListener();
    actionManagerEx.addAnActionListener(myAnActionListener);

    myCaretMoveListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        hideHints(HIDE_BY_ANY_KEY, false, false);
      }
    };

    projectManager.addProjectManagerListener(new MyProjectManagerListener());

    myEditorMouseListener = new EditorMouseAdapter() {
      public void mousePressed(EditorMouseEvent event) {
        hideAllHints();
      }
    };

    myVisibleAreaListener = new VisibleAreaListener() {
      public void visibleAreaChanged(VisibleAreaEvent e) {
        updateScrollableHints(e);
        hideHints(HIDE_BY_SCROLLING, false, false);
      }
    };

    myEditorFocusListener = new FocusAdapter() {
      public void focusLost(FocusEvent e) {
        myHideAlarm.addRequest(
          new Runnable() {
            public void run() {
              hideAllHints();
            }
          },
          200
        );
      }

      public void focusGained(FocusEvent e) {
        myHideAlarm.cancelAllRequests();
      }
    };

    myEditorDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent event) {
        HintInfo[] infos = myHintsStack.toArray(new HintInfo[myHintsStack.size()]);
        for (HintInfo info : infos) {
          if ((info.flags & HIDE_BY_TEXT_CHANGE) != 0) {
            if (info.hint.isVisible()) {
              info.hint.hide();
            }
            myHintsStack.remove(info);
          }
        }

        if (myHintsStack.isEmpty()) {
          updateLastEditor(null);
        }
      }
    };

    editorActionManager.setActionHandler(IdeActions.ACTION_EDITOR_ESCAPE,
                                   new EscapeHandler(editorActionManager.getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE)));

    myTooltipController = new TooltipController();
  }

  @NotNull
  public String getComponentName() {
    return "HintManager";
  }

  public void initComponent() { }

  private void updateScrollableHints(VisibleAreaEvent e) {
    for (int i = 0; i < myHintsStack.size(); i++) {
      HintInfo info = myHintsStack.get(i);
      if (info.hint instanceof LightweightHint && (info.flags & UPDATE_BY_SCROLLING) != 0) {
        updateScrollableHintPosition(e, info.hint, (info.flags & HIDE_IF_OUT_OF_EDITOR) != 0);
      }
    }
  }

  public TooltipController getTooltipController() {
    return myTooltipController;
  }

  public boolean hasShownHintsThatWillHideByOtherHint() {
    for (HintInfo hintInfo : myHintsStack) {
      if (hintInfo.hint.isVisible() && (hintInfo.flags & HIDE_BY_OTHER_HINT) != 0) return true;
    }
    return false;
  }

  @Nullable
  public Hint findHintByType(Class klass){
    for (HintInfo hintInfo : myHintsStack) {
      if (klass.isInstance(hintInfo.hint.getComponent()) && hintInfo.hint.isVisible()){
        return hintInfo.hint;
      }
    }
    return null;
  }

  public void disposeComponent() {
    ActionManagerEx.getInstanceEx().removeAnActionListener(myAnActionListener);
  }

  private static void updateScrollableHintPosition(VisibleAreaEvent e, LightweightHint hint, boolean hideIfOutOfEditor) {
    if (hint.getComponent() instanceof IntentionHintComponent) {
      ((IntentionHintComponent)hint.getComponent()).closePopup();
    }

    Editor editor = e.getEditor();
    if (!editor.getComponent().isShowing() || editor.isOneLineMode()) return;
    Rectangle newRectangle = e.getOldRectangle();
    Rectangle oldRectangle = e.getNewRectangle();
    Rectangle bounds = hint.getBounds();
    Point location = bounds.getLocation();

    location = SwingUtilities.convertPoint(
      editor.getComponent().getRootPane().getLayeredPane(),
      location,
      editor.getContentComponent()
    );


    int xOffset = location.x - oldRectangle.x;
    int yOffset = location.y - oldRectangle.y;
    location = new Point(newRectangle.x + xOffset, newRectangle.y + yOffset);

    Rectangle newBounds = new Rectangle(location.x, location.y, bounds.width, bounds.height);

    final boolean valid = hideIfOutOfEditor ? oldRectangle.contains(newBounds) : oldRectangle.intersects(newBounds);
    if (valid) {
      location = SwingUtilities.convertPoint(
        editor.getContentComponent(),
        location,
        editor.getComponent().getRootPane().getLayeredPane()
      );

      hint.setLocation(location.x, location.y);
    }
    else {
      hint.hide();
    }
  }

  public void showEditorHint(LightweightHint hint, Editor editor, short constraint, int flags, int timeout, boolean reviveOnEditorChange) {
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    Point p = getHintPosition(hint, editor, pos, constraint);
    showEditorHint(hint, editor, p, flags, timeout, reviveOnEditorChange);
  }

  /**
   * @param p point in layered pane coordinate system.
   * @param reviveOnEditorChange
   */
  public void showEditorHint(final LightweightHint hint,
                             final Editor editor,
                             Point p,
                             int flags,
                             int timeout,
                             boolean reviveOnEditorChange) {
    myHideAlarm.cancelAllRequests();

    hideHints(HIDE_BY_OTHER_HINT, false, false);

    if (editor != myLastEditor) {
      hideAllHints();
    }

    if (!editor.getContentComponent().isShowing()) return;

    updateLastEditor(editor);

    Project project = editor.getProject();
    if (project != null) {
      LookupManager lookupManager = LookupManager.getInstance(project);
      Lookup lookup = lookupManager.getActiveLookup();
      if (lookup != null && (flags & HIDE_BY_LOOKUP_ITEM_CHANGE) != 0) {
        lookup.addLookupListener(
          new LookupAdapter() {
            public void currentItemChanged(LookupEvent event) {
              hint.hide();
            }

            public void itemSelected(LookupEvent event) {
              hint.hide();
            }

            public void lookupCanceled(LookupEvent event) {
              hint.hide();
            }
          }
        );
      }
    }

    Component component = hint.getComponent();

    JLayeredPane layeredPane = editor.getComponent().getRootPane().getLayeredPane();
    Dimension size = component.getPreferredSize();
    if(layeredPane.getWidth() < p.x + size.width) {
      p.x = Math.max(0, layeredPane.getWidth() - size.width);
    }
    hint.show(layeredPane, p.x, p.y, editor.getContentComponent());

    ListenerUtil.addMouseListener(
      component,
      new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          myHideAlarm.cancelAllRequests();
        }
      }
    );
    ListenerUtil.addFocusListener(
      component,
      new FocusAdapter() {
        public void focusGained(FocusEvent e) {
          myHideAlarm.cancelAllRequests();
        }
      }
    );

    final HintInfo info = new HintInfo(hint, flags, reviveOnEditorChange);
    myHintsStack.add(info);
    if (timeout > 0) {
      Timer timer = new Timer(
        timeout,
        new ActionListener() {
          public void actionPerformed(ActionEvent event) {
            hint.hide();
          }
        }
      );
      timer.setRepeats(false);
      timer.start();
    }
  }

  public void hideAllHints() {
    for (int i = 0; i < myHintsStack.size(); i++) {
      HintInfo info = myHintsStack.get(i);
      if (info.hint.isVisible()) {
        info.hint.hide();
      }
    }
    myHintsStack.clear();
    updateLastEditor(null);
  }

  /**
   * @return coordinates in layered pane coordinate system.
   */
  public Point getHintPosition(LightweightHint hint, Editor editor, short constraint) {
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    Project project = editor.getProject();
    Lookup lookup = project == null ? null : LookupManager.getInstance(project).getActiveLookup();

    if (lookup == null) {
      for (HintInfo info : myHintsStack) {
        if (!info.hint.isSelectingHint()) continue;
        final Rectangle rectangle = info.hint.getBounds();

        if (rectangle != null) {
          return getHintPositionRelativeTo(hint, editor, constraint, rectangle, rectangle, pos);
        }
      }
      return getHintPosition(hint, editor, pos, constraint);
    }
    else {
      Rectangle cellBounds = lookup.getCurrentItemBounds();
      if (cellBounds == null) {
        return getHintPosition(hint, editor, pos, constraint);
      }
      Rectangle lookupBounds = lookup.getBounds();
      return getHintPositionRelativeTo(hint, editor, constraint, cellBounds, lookupBounds, pos);
    }
  }

  private Point getHintPositionRelativeTo(final LightweightHint hint,
                                          final Editor editor,
                                          final short constraint,
                                          final Rectangle cellBounds,
                                          final Rectangle lookupBounds,
                                          final LogicalPosition pos) {
    Dimension hintSize = hint.getComponent().getPreferredSize();
    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
    int layeredPaneHeight = layeredPane.getHeight();

    switch (constraint) {
      case LEFT:
        {
          int y = cellBounds.y + (cellBounds.height - hintSize.height) / 2;
          if (y < 0) {
            y = 0;
          }
          else if (y + hintSize.height >= layeredPaneHeight) {
            y = layeredPaneHeight - hintSize.height;
          }
          return new Point(lookupBounds.x - hintSize.width, y);
        }

      case RIGHT:
        {
          int y = cellBounds.y + (cellBounds.height - hintSize.height) / 2;
          if (y < 0) {
            y = 0;
          }
          else if (y + hintSize.height >= layeredPaneHeight) {
            y = layeredPaneHeight - hintSize.height;
          }
          return new Point(lookupBounds.x + lookupBounds.width, y);
        }

      case ABOVE:
        Point posAboveCaret = getHintPosition(hint, editor, pos, ABOVE);
        return new Point(lookupBounds.x, Math.min(posAboveCaret.y, lookupBounds.y - hintSize.height));

      case UNDER:
        Point posUnderCaret = getHintPosition(hint, editor, pos, UNDER);
        return new Point(lookupBounds.x, Math.max(posUnderCaret.y, lookupBounds.y + lookupBounds.height));

      default:
        LOG.assertTrue(false);
        return null;
    }
  }

  /**
   * @return position of hint in layered pane coordinate system
   */
  public Point getHintPosition(LightweightHint hint, Editor editor, LogicalPosition pos, short constraint) {
    return getHintPosition(hint, editor, pos, pos, constraint);
  }

  private static Point getHintPosition(LightweightHint hint,
                                Editor editor,
                                LogicalPosition pos1,
                                LogicalPosition pos2,
                                short constraint) {
    Point p = _getHintPosition(hint, editor, pos1, pos2, constraint);
    JLayeredPane layeredPane = editor.getComponent().getRootPane().getLayeredPane();
    Dimension hintSize = hint.getComponent().getPreferredSize();
    if (constraint == ABOVE){
      if (p.y < 0){
        Point p1 = _getHintPosition(hint, editor, pos1, pos2, UNDER);
        if (p1.y + hintSize.height <= layeredPane.getSize().height) {
          return p1;
        }
      }
    }
    else if (constraint == UNDER){
      if (p.y + hintSize.height > layeredPane.getSize().height){
        Point p1 = _getHintPosition(hint, editor, pos1, pos2, ABOVE);
        if (p1.y >= 0) {
          return p1;
        }
      }
    }

    return p;
  }

  private static Point _getHintPosition(LightweightHint hint,
                                Editor editor,
                                LogicalPosition pos1,
                                LogicalPosition pos2,
                                short constraint) {
    Dimension hintSize = hint.getComponent().getPreferredSize();
    int line1 = pos1.line;
    int col1 = pos1.column;
    int line2 = pos2.line;
    int col2 = pos2.column;

    Point location;
    JLayeredPane layeredPane = editor.getComponent().getRootPane().getLayeredPane();
    JComponent internalComponent = editor.getContentComponent();
    if (constraint == RIGHT_UNDER) {
      Point p = editor.logicalPositionToXY(new LogicalPosition(line2, col2));
      p.y += editor.getLineHeight();
      location = SwingUtilities.convertPoint(internalComponent, p, layeredPane);
    }
    else {
      Point p = editor.logicalPositionToXY(new LogicalPosition(line1, col1));
      if (constraint == UNDER){
        p.y += editor.getLineHeight();
      }
      location = SwingUtilities.convertPoint(internalComponent, p, layeredPane);
    }

    if (constraint == ABOVE) {
      location.y -= hintSize.height;
      int diff = location.x + hintSize.width - layeredPane.getWidth();
      if (diff > 0) {
        location.x = Math.max (location.x - diff, 0);
      }
    }

    if (constraint == LEFT || constraint == RIGHT) {
      location.y -= hintSize.height / 2;
      if (constraint == LEFT) {
        location.x -= hintSize.width;
      }
    }

    return location;
  }

  public void showErrorHint(Editor editor, String text) {
    JLabel label = HintUtil.createErrorLabel(text);
    LightweightHint hint = new LightweightHint(label);
    Point p = getHintPosition(hint, editor, ABOVE);
    showEditorHint(hint, editor, p, HIDE_BY_ANY_KEY | HIDE_BY_TEXT_CHANGE | HIDE_BY_SCROLLING, 0, false);
  }

  public void showInformationHint(Editor editor, String text) {
    JLabel label = HintUtil.createInformationLabel(text);
    LightweightHint hint = new LightweightHint(label);
    Point p = getHintPosition(hint, editor, ABOVE);
    showEditorHint(hint, editor, p, HIDE_BY_ANY_KEY | HIDE_BY_TEXT_CHANGE | HIDE_BY_SCROLLING, 0, false);
  }

  public void showErrorHint(
    Editor editor,
    String hintText,
    int offset1,
    int offset2,
    short constraint,
    int flags,
    int timeout) {

    JLabel label = HintUtil.createErrorLabel(hintText);
    LightweightHint hint = new LightweightHint(label);
    final LogicalPosition pos1 = editor.offsetToLogicalPosition(offset1);
    final LogicalPosition pos2 = editor.offsetToLogicalPosition(offset2);
    final Point p = getHintPosition(hint, editor, pos1, pos2, constraint);
    showEditorHint(hint, editor, p, flags, timeout, false);
  }


  public void showQuestionHint(
    Editor editor,
    String hintText,
    int offset1,
    int offset2,
    QuestionAction action) {

    JLabel label = HintUtil.createQuestionLabel(hintText);
    LightweightHint hint = new LightweightHint(label);
    showQuestionHint(editor, offset1, offset2, hint, action, ABOVE);
  }

  public void showQuestionHint(
    final Editor editor,
    final int offset1,
    final int offset2,
    final LightweightHint hint,
    final QuestionAction action,
    final short constraint) {

    final LogicalPosition pos1 = editor.offsetToLogicalPosition(offset1);
    final LogicalPosition pos2 = editor.offsetToLogicalPosition(offset2);
    final Point p = getHintPosition(hint, editor, pos1, pos2, constraint);
    showQuestionHint(editor, p, offset1, offset2, hint, action);
  }

  public void showQuestionHint(final Editor editor,
                               final Point p,
                               final int offset1,
                               final int offset2,
                               final LightweightHint hint,
                               final QuestionAction action) {
    TextAttributes attributes = new TextAttributes();
    attributes.setEffectColor(HintUtil.QUESTION_UNDERSCORE_COLOR);
    attributes.setEffectType(EffectType.LINE_UNDERSCORE);
    final RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(offset1, offset2,
                                                                                     HighlighterLayer.ERROR + 1,
                                                                                     attributes,
                                                                                     HighlighterTargetArea.EXACT_RANGE);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myQuestionHint != null) {
            myQuestionHint.hide();
            myQuestionHint = null;
            myQuestionAction = null;
          }

          hint.addHintListener(
            new HintListener() {
              public void hintHidden(EventObject event) {
                editor.getMarkupModel().removeHighlighter(highlighter);

                if (myQuestionHint == hint) {
                  myQuestionAction = null;
                  myQuestionHint = null;
                }
                hint.removeHintListener(this);
              }
            }
          );

          showEditorHint(hint, editor, p,
                         HIDE_BY_ANY_KEY | HIDE_BY_TEXT_CHANGE | UPDATE_BY_SCROLLING | HIDE_IF_OUT_OF_EDITOR, 0, false);
          myQuestionAction = action;
          myQuestionHint = hint;
        }
      });
  }

  private void updateLastEditor(final Editor editor) {
    if (myLastEditor != editor) {
      if (myLastEditor != null) {
        myLastEditor.removeEditorMouseListener(myEditorMouseListener);
        myLastEditor.getContentComponent().removeFocusListener(myEditorFocusListener);
        myLastEditor.getDocument().removeDocumentListener(myEditorDocumentListener);
        myLastEditor.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
        myLastEditor.getCaretModel().removeCaretListener(myCaretMoveListener);
      }

      myLastEditor = editor;
      if (myLastEditor != null) {
        myLastEditor.addEditorMouseListener(myEditorMouseListener);
        myLastEditor.getContentComponent().addFocusListener(myEditorFocusListener);
        myLastEditor.getDocument().addDocumentListener(myEditorDocumentListener);
        myLastEditor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
        myLastEditor.getCaretModel().addCaretListener(myCaretMoveListener);
      }
    }
  }

  public boolean performCurrentQuestionAction() {
    if (myQuestionAction != null && myQuestionHint != null) {
      if (myQuestionHint.isVisible()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("performing an action:" + myQuestionAction);
        }
        if (myQuestionAction.execute()) {
          if (myQuestionHint != null) {
            myQuestionHint.hide();
          }
          return true;
        }
        else {
          return true;
        }
      }

      myQuestionAction = null;
      myQuestionHint = null;
    }

    return false;
  }

  private class MyAnActionListener implements AnActionListener {
    public void beforeActionPerformed(AnAction action, DataContext dataContext) {
      if (action instanceof ActionToIgnore) return;

      AnAction escapeAction = ActionManagerEx.getInstanceEx().getAction(IdeActions.ACTION_EDITOR_ESCAPE);
      if (action == escapeAction) return;

      hideHints(HIDE_BY_ANY_KEY, false, false);
    }

    public void beforeEditorTyping(char c, DataContext dataContext) {}
  }

  /**
   * Hides all hints when selected editor changes. Unfortunately  user can change
   * selected editor by mouse. These clicks are not AnActions so they are not
   * fired by ActionManager.
   */
  private final class MyEditorManagerListener extends FileEditorManagerAdapter {
    public void selectionChanged(FileEditorManagerEvent event) {
      hideHints(0, false, true);
    }
  }

  /**
   * We have to spy for all opened projects to register MyEditorManagerListener into
   * all opened projects.
   */
  private final class MyProjectManagerListener extends ProjectManagerAdapter {
    public void projectOpened(Project project) {
      FileEditorManager.getInstance(project).addFileEditorManagerListener(myEditorManagerListener);
    }

    public void projectClosed(Project project) {
      FileEditorManager.getInstance(project).removeFileEditorManagerListener(myEditorManagerListener);
      // avoid leak through com.intellij.codeInsight.hint.TooltipController.myCurrentTooltip
      getTooltipController().cancelTooltips();
    }
  }

  public static class EscapeHandler extends EditorActionHandler {
    private final EditorActionHandler myOriginalHandler;

    public EscapeHandler(EditorActionHandler originalHandler) {
      myOriginalHandler = originalHandler;
    }

    public void execute(Editor editor, DataContext dataContext) {
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      if (project == null || !getInstance().hideHints(HIDE_BY_ESCAPE | HIDE_BY_ANY_KEY, true, false)) {
        myOriginalHandler.execute(editor, dataContext);
      }
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);

      if (project != null) {
        HintManager hintManager = getInstance();
        for (int i = hintManager.myHintsStack.size() - 1; i >= 0; i--) {
          final HintInfo info = hintManager.myHintsStack.get(i);
          if (!info.hint.isVisible()) {
            hintManager.myHintsStack.remove(i);
            continue;
          }

          if ((info.flags & (HIDE_BY_ESCAPE | HIDE_BY_ANY_KEY)) != 0) {
            return true;
          }
        }
      }

      return myOriginalHandler.isEnabled(editor, dataContext);
    }
  }

  private boolean hideHints(int mask, boolean onlyOne, boolean editorChanged) {
    try {
      boolean done = false;

      for (int i = myHintsStack.size() - 1; i >= 0; i--) {
        final HintInfo info = myHintsStack.get(i);
        if (!info.hint.isVisible()) {
          myHintsStack.remove(i);
          continue;
        }

        if ((info.flags & mask) != 0 || editorChanged && !info.reviveOnEditorChange) {
          info.hint.hide();
          myHintsStack.remove(info);
          if (onlyOne) {
            return true;
          }
          done = true;
        }
      }

      return done;
    }
    finally {
      if (myHintsStack.isEmpty()) {
        updateLastEditor(null);
      }
    }
  }
}
