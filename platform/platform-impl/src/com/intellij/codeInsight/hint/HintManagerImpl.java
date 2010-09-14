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
package com.intellij.codeInsight.hint;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

public class HintManagerImpl extends HintManager implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.HintManager");

  private final AnActionListener myAnActionListener;
  private final MyEditorManagerListener myEditorManagerListener;
  private final EditorMouseAdapter myEditorMouseListener;
  private final FocusListener myEditorFocusListener;
  private final DocumentListener myEditorDocumentListener;
  private final VisibleAreaListener myVisibleAreaListener;
  private final CaretListener myCaretMoveListener;

  private LightweightHint myQuestionHint = null;
  private QuestionAction myQuestionAction = null;

  private final List<HintInfo> myHintsStack = new ArrayList<HintInfo>();
  private Editor myLastEditor = null;
  private final Alarm myHideAlarm = new Alarm();

  private static int getPriority(QuestionAction action) {
    return action instanceof PriorityQuestionAction ? ((PriorityQuestionAction)action).getPriority() : 0;
  }

  public boolean canShowQuestionAction(QuestionAction action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myQuestionAction == null || getPriority(myQuestionAction) <= getPriority(action);
  }

  public interface ActionToIgnore {
  }

  private static class HintInfo {
    final LightweightHint hint;
    final int flags;
    private final boolean reviveOnEditorChange;

    private HintInfo(LightweightHint hint, int flags, boolean reviveOnEditorChange) {
      this.hint = hint;
      this.flags = flags;
      this.reviveOnEditorChange = reviveOnEditorChange;
    }
  }

  public static HintManagerImpl getInstanceImpl() {
    return (HintManagerImpl)ServiceManager.getService(HintManager.class);
  }

  public HintManagerImpl(ActionManagerEx actionManagerEx, ProjectManager projectManager) {
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
      public void focusLost(final FocusEvent e) {
        myHideAlarm.addRequest(
          new Runnable() {
            public void run() {
              if (!JBPopupFactory.getInstance().isChildPopupFocused(e.getComponent())) {
                hideAllHints();
              }
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
        LOG.assertTrue(SwingUtilities.isEventDispatchThread());
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
  }

  private void updateScrollableHints(VisibleAreaEvent e) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (HintInfo info : myHintsStack) {
      if (info.hint instanceof LightweightHint && (info.flags & UPDATE_BY_SCROLLING) != 0) {
        updateScrollableHintPosition(e, info.hint, (info.flags & HIDE_IF_OUT_OF_EDITOR) != 0);
      }
    }
  }

  public boolean hasShownHintsThatWillHideByOtherHint() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (HintInfo hintInfo : myHintsStack) {
      if (hintInfo.hint.isVisible() && (hintInfo.flags & HIDE_BY_OTHER_HINT) != 0) return true;
    }
    return false;
  }

  public void dispose() {
    ActionManagerEx.getInstanceEx().removeAnActionListener(myAnActionListener);
  }

  private static void updateScrollableHintPosition(VisibleAreaEvent e, LightweightHint hint, boolean hideIfOutOfEditor) {
    if (hint.getComponent() instanceof ScrollAwareHint) {
      ((ScrollAwareHint)hint.getComponent()).editorScrolled();
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

      hint.updateBounds(location.x, location.y);
    }
    else {
      hint.hide();
    }
  }

  public void showEditorHint(LightweightHint hint, Editor editor, short constraint, int flags, int timeout, boolean reviveOnEditorChange) {
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    Point p = getHintPosition(hint, editor, pos, constraint);
    showEditorHint(hint, editor, p, flags, timeout, reviveOnEditorChange, new HintHint(editor, p));
  }

  /**
   * @param p point in layered pane coordinate system.
   * @param reviveOnEditorChange
   * @param hintInfo
   */
  public void showEditorHint(@NotNull final LightweightHint hint,
                             @NotNull Editor editor,
                             @NotNull Point p,
                             int flags,
                             int timeout, boolean reviveOnEditorChange) {

    showEditorHint(hint, editor, p, flags, timeout, reviveOnEditorChange, new HintHint(editor, p));
  }

  public void showEditorHint(@NotNull final LightweightHint hint,
                             @NotNull Editor editor,
                             @NotNull Point p,
                             int flags,
                             int timeout,
                             boolean reviveOnEditorChange, HintHint hintInfo) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    myHideAlarm.cancelAllRequests();

    hideHints(HIDE_BY_OTHER_HINT, false, false);

    if (editor != myLastEditor) {
      hideAllHints();
    }

    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) return;

    updateLastEditor(editor);

    getPublisher().hintShown(editor.getProject(), hint, flags);

    Component component = hint.getComponent();

    doShowInGivenLocation(hint, editor, p, hintInfo);

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

  public void showHint(@NotNull final JComponent component, @NotNull RelativePoint p, int flags, int timeout) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    myHideAlarm.cancelAllRequests();

    hideHints(HIDE_BY_OTHER_HINT, false, false);

    final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null)
        .setRequestFocus(false)
        .setResizable(false)
        .setMovable(false)
        .createPopup();
    popup.show(p);

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

    final HintInfo info = new HintInfo(new LightweightHint(component){
      public void hide() {
        popup.cancel();
      }
    }, flags, false);
    myHintsStack.add(info);
    if (timeout > 0) {
      Timer timer = new Timer(
        timeout,
        new ActionListener() {
          public void actionPerformed(ActionEvent event) {
            popup.dispose();
          }
        }
      );
      timer.setRepeats(false);
      timer.start();
    }
  }

  private static void doShowInGivenLocation(final LightweightHint hint, final Editor editor, final Point p, HintHint hintInfo) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    JLayeredPane layeredPane = editor.getComponent().getRootPane().getLayeredPane();
    Dimension size = hint.getComponent().getPreferredSize();
    if(layeredPane.getWidth() < p.x + size.width) {
      p.x = Math.max(0, layeredPane.getWidth() - size.width);
    }
    if (hint.isVisible()) {
      hint.updateBounds(p.x, p.y);
    }
    else {
      hint.show(layeredPane, p.x, p.y, editor.getContentComponent(), hintInfo);
    }
  }

  public static void adjustEditorHintPosition(final LightweightHint hint, final Editor editor, final Point p) {
    doShowInGivenLocation(hint, editor, p, new HintHint(editor, p));
  }

  public void hideAllHints() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    List<HintInfo> hints = new ArrayList<HintInfo>(myHintsStack);
    for (HintInfo info : hints) {
        info.hint.hide();
    }
    myHintsStack.clear();
    updateLastEditor(null);
  }

  /**
   * @return coordinates in layered pane coordinate system.
   */
  public Point getHintPosition(LightweightHint hint, Editor editor, short constraint) {
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    final DataContext dataContext = ((EditorEx)editor).getDataContext();
    final Rectangle dominantArea = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getData(dataContext);

    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    if (dominantArea == null) {
      for (HintInfo info : myHintsStack) {
        if (!info.hint.isSelectingHint()) continue;
        final Rectangle rectangle = info.hint.getBounds();

        if (rectangle != null) {
          return getHintPositionRelativeTo(hint, editor, constraint, rectangle, pos);
        }
      }
    }
    else {
      return getHintPositionRelativeTo(hint, editor, constraint, dominantArea, pos);
    }

    return getHintPosition(hint, editor, pos, constraint);
  }

  private static Point getHintPositionRelativeTo(final LightweightHint hint,
                                                 final Editor editor,
                                                 final short constraint,
                                                 final Rectangle lookupBounds,
                                                 final LogicalPosition pos) {
    Dimension hintSize = hint.getComponent().getPreferredSize();
    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
    int layeredPaneHeight = layeredPane.getHeight();

    switch (constraint) {
      case LEFT:
        {
          int y = lookupBounds.y;
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
          int y = lookupBounds.y;
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
  public static Point getHintPosition(LightweightHint hint, Editor editor, LogicalPosition pos, short constraint) {
    return getHintPosition(hint, editor, pos, pos, constraint);
  }

  private static Point getHintPosition(LightweightHint hint, Editor editor, LogicalPosition pos1, LogicalPosition pos2, short constraint) {
    Point p = _getHintPosition(hint, editor, pos1, pos2, constraint);
    JLayeredPane layeredPane = editor.getComponent().getRootPane().getLayeredPane();
    Dimension hintSize = hint.getComponent().getPreferredSize();
    if (constraint == ABOVE) {
      if (p.y < 0) {
        Point p1 = _getHintPosition(hint, editor, pos1, pos2, UNDER);
        if (p1.y + hintSize.height <= layeredPane.getSize().height) {
          return p1;
        }
      }
    }
    else if (constraint == UNDER) {
      if (p.y + hintSize.height > layeredPane.getSize().height) {
        Point p1 = _getHintPosition(hint, editor, pos1, pos2, ABOVE);
        if (p1.y >= 0) {
          return p1;
        }
      }
    }

    return p;
  }

  private static Point _getHintPosition(LightweightHint hint, Editor editor, LogicalPosition pos1, LogicalPosition pos2, short constraint) {
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

  public void showErrorHint(@NotNull Editor editor, @NotNull String text) {
    JLabel label = HintUtil.createErrorLabel(text);
    LightweightHint hint = new LightweightHint(label);
    Point p = getHintPosition(hint, editor, ABOVE);
    showEditorHint(hint, editor, p, HIDE_BY_ANY_KEY | HIDE_BY_TEXT_CHANGE | HIDE_BY_SCROLLING, 0, false);
  }

  public void showInformationHint(@NotNull Editor editor, @NotNull String text) {
    JLabel label = HintUtil.createInformationLabel(text);
    showInformationHint(editor, label);
  }

  @Override
  public void showInformationHint(@NotNull Editor editor, @NotNull JComponent component) {
    LightweightHint hint = new LightweightHint(component);
    Point p = getHintPosition(hint, editor, ABOVE);
    showEditorHint(hint, editor, p, HIDE_BY_ANY_KEY | HIDE_BY_TEXT_CHANGE | HIDE_BY_SCROLLING, 0, false);
  }

  public void showErrorHint(@NotNull Editor editor, @NotNull String hintText, int offset1, int offset2, short constraint, int flags, int timeout) {
    JLabel label = HintUtil.createErrorLabel(hintText);
    LightweightHint hint = new LightweightHint(label);
    final LogicalPosition pos1 = editor.offsetToLogicalPosition(offset1);
    final LogicalPosition pos2 = editor.offsetToLogicalPosition(offset2);
    final Point p = getHintPosition(hint, editor, pos1, pos2, constraint);
    showEditorHint(hint, editor, p, flags, timeout, false);
  }


  public void showQuestionHint(@NotNull Editor editor, @NotNull String hintText, int offset1, int offset2, @NotNull QuestionAction action) {
    JLabel label = HintUtil.createQuestionLabel(hintText);
    LightweightHint hint = new LightweightHint(label);
    showQuestionHint(editor, offset1, offset2, hint, action, ABOVE);
  }

  public void showQuestionHint(@NotNull final Editor editor,
                               final int offset1,
                               final int offset2,
                               @NotNull final LightweightHint hint,
                               @NotNull final QuestionAction action,
                               final short constraint) {
    final LogicalPosition pos1 = editor.offsetToLogicalPosition(offset1);
    final LogicalPosition pos2 = editor.offsetToLogicalPosition(offset2);
    final Point p = getHintPosition(hint, editor, pos1, pos2, constraint);
    showQuestionHint(editor, p, offset1, offset2, hint, action);
  }


  public void showQuestionHint(@NotNull final Editor editor,
                               @NotNull final Point p,
                               final int offset1,
                               final int offset2,
                               @NotNull final LightweightHint hint,
                               @NotNull final QuestionAction action) {
    TextAttributes attributes = new TextAttributes();
    attributes.setEffectColor(HintUtil.QUESTION_UNDERSCORE_COLOR);
    attributes.setEffectType(EffectType.LINE_UNDERSCORE);
    final RangeHighlighter highlighter = editor.getMarkupModel()
      .addRangeHighlighter(offset1, offset2, HighlighterLayer.ERROR + 1, attributes, HighlighterTargetArea.EXACT_RANGE);
    if (myQuestionHint != null) {
      myQuestionHint.hide();
      myQuestionHint = null;
      myQuestionAction = null;
    }

    hint.addHintListener(new HintListener() {
      public void hintHidden(EventObject event) {
        if (!editor.isDisposed()) {
          editor.getMarkupModel().removeHighlighter(highlighter);
        }

        if (myQuestionHint == hint) {
          myQuestionAction = null;
          myQuestionHint = null;
        }
        hint.removeHintListener(this);
      }
    });

    showEditorHint(hint, editor, p, HIDE_BY_ANY_KEY | HIDE_BY_TEXT_CHANGE | UPDATE_BY_SCROLLING | HIDE_IF_OUT_OF_EDITOR, 0, false);
    myQuestionAction = action;
    myQuestionHint = hint;
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
    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
      if (action instanceof ActionToIgnore) return;

      AnAction escapeAction = ActionManagerEx.getInstanceEx().getAction(IdeActions.ACTION_EDITOR_ESCAPE);
      if (action == escapeAction) return;

      hideHints(HIDE_BY_ANY_KEY, false, false);
    }


    public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
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
      project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, myEditorManagerListener);
    }

    public void projectClosed(Project project) {
      // avoid leak through com.intellij.codeInsight.hint.TooltipController.myCurrentTooltip
      TooltipController.getInstance().cancelTooltips();

      myQuestionAction = null;
      myQuestionHint = null;
    }
  }

  boolean isEscapeHandlerEnabled() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (int i = myHintsStack.size() - 1; i >= 0; i--) {
      final HintInfo info = myHintsStack.get(i);
      if (!info.hint.isVisible()) {
        myHintsStack.remove(i);
        continue;
      }

      if ((info.flags & (HIDE_BY_ESCAPE | HIDE_BY_ANY_KEY)) != 0) {
        return true;
      }
    }
    return false;
  }

  public boolean hideHints(int mask, boolean onlyOne, boolean editorChanged) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
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

  private static class EditorHintListenerHolder {
    private static final EditorHintListener ourEditorHintPublisher =
        ApplicationManager.getApplication().getMessageBus().syncPublisher(EditorHintListener.TOPIC);

    private EditorHintListenerHolder() {
    }
  }

  private static EditorHintListener getPublisher() {
    return EditorHintListenerHolder.ourEditorHintPublisher;
  }
}
