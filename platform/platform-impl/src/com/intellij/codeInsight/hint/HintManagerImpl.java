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
package com.intellij.codeInsight.hint;

import com.intellij.ide.IdeTooltip;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.BitUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
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

  private final List<HintInfo> myHintsStack = new ArrayList<>();
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
    @HideFlags final int flags;
    private final boolean reviveOnEditorChange;

    private HintInfo(LightweightHint hint, @HideFlags int flags, boolean reviveOnEditorChange) {
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

    myCaretMoveListener = new CaretAdapter() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        hideHints(HIDE_BY_ANY_KEY, false, false);
      }
    };

    final MyProjectManagerListener projectManagerListener = new MyProjectManagerListener();
    for (Project project : projectManager.getOpenProjects()) {
      projectManagerListener.projectOpened(project);
    }
    projectManager.addProjectManagerListener(projectManagerListener);

    myEditorMouseListener = new EditorMouseAdapter() {
      @Override
      public void mousePressed(EditorMouseEvent event) {
        hideAllHints();
      }
    };

    myVisibleAreaListener = new VisibleAreaListener() {
      @Override
      public void visibleAreaChanged(VisibleAreaEvent e) {
        updateScrollableHints(e);
        if (e.getOldRectangle() == null ||
            e.getOldRectangle().x != e.getNewRectangle().x ||
            e.getOldRectangle().y != e.getNewRectangle().y) {
          hideHints(HIDE_BY_SCROLLING, false, false);
        }
      }
    };

    myEditorFocusListener = new FocusAdapter() {
      @Override
      public void focusLost(final FocusEvent e) {
        //if (UIUtil.isFocusProxy(e.getOppositeComponent())) return;
        myHideAlarm.addRequest(new Runnable() {
          private boolean myNotFocused; // previous focus state

          @Override
          public void run() {
            // see implementation here: com.intellij.ui.popup.AbstractPopup.isFocused(java.awt.Component[])
            // the following method may return null while switching focus between popups:
            // KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()
            // http://docs.oracle.com/javase/7/docs/webnotes/tsg/TSG-Desktop/html/awt.html#gdabn
            boolean notFocused = !JBPopupFactory.getInstance().isChildPopupFocused(e.getComponent());
            if (myNotFocused && notFocused) {
              // hide all hints if a child popup is not focused now
              // and if it was not focused 200 milliseconds ago
              hideAllHints();
            }
            // TODO: find a way to replace this hack with com.intellij.openapi.wm.IdeFocusManager
            myNotFocused = notFocused;
          }
        }, 200);
      }

      @Override
      public void focusGained(FocusEvent e) {
        myHideAlarm.cancelAllRequests();
      }
    };

    myEditorDocumentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent event) {
        LOG.assertTrue(SwingUtilities.isEventDispatchThread());
        if (event.getOldLength() == 0 && event.getNewLength() == 0) return;
        HintInfo[] infos = getHintsStackArray();
        for (HintInfo info : infos) {
          if (BitUtil.isSet(info.flags, HIDE_BY_TEXT_CHANGE)) {
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

  public boolean isHint(Window component) {
    return myHintsStack.contains(component);
  }

  @NotNull
  private HintInfo[] getHintsStackArray() {
    return myHintsStack.toArray(new HintInfo[myHintsStack.size()]);
  }

  public boolean performCurrentQuestionAction() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myQuestionAction != null && myQuestionHint != null) {
      if (myQuestionHint.isVisible()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("performing an action:" + myQuestionAction);
        }
        if (myQuestionAction.execute()) {
          if (myQuestionHint != null) {
            myQuestionHint.hide();
          }
        }
        return true;
      }

      myQuestionAction = null;
      myQuestionHint = null;
    }

    return false;
  }


  private void updateScrollableHints(VisibleAreaEvent e) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (HintInfo info : getHintsStackArray()) {
      if (info.hint != null && BitUtil.isSet(info.flags, UPDATE_BY_SCROLLING)) {
        updateScrollableHintPosition(e, info.hint, BitUtil.isSet(info.flags, HIDE_IF_OUT_OF_EDITOR));
      }
    }
  }

  @Override
  public boolean hasShownHintsThatWillHideByOtherHint(boolean willShowTooltip) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (HintInfo hintInfo : getHintsStackArray()) {
      if (hintInfo.hint.isVisible() && BitUtil.isSet(hintInfo.flags, HIDE_BY_OTHER_HINT)) return true;
      if (willShowTooltip && hintInfo.hint.isAwtTooltip()) {
        // only one AWT tooltip can be visible, so this hint will hide even though it's not marked with HIDE_BY_OTHER_HINT
        return true;
      }
    }
    return false;
  }

  @Override
  public void dispose() {
    ActionManagerEx.getInstanceEx().removeAnActionListener(myAnActionListener);
  }

  private static void updateScrollableHintPosition(VisibleAreaEvent e, LightweightHint hint, boolean hideIfOutOfEditor) {
    if (hint.getComponent() instanceof ScrollAwareHint) {
      ((ScrollAwareHint)hint.getComponent()).editorScrolled();
    }

    if (!hint.isVisible()) return;

    Editor editor = e.getEditor();
    if (!editor.getComponent().isShowing() || editor.isOneLineMode()) return;
    Rectangle newRectangle = e.getOldRectangle();
    Rectangle oldRectangle = e.getNewRectangle();

    Point location = hint.getLocationOn(editor.getContentComponent());
    Dimension size = hint.getSize();

    int xOffset = location.x - oldRectangle.x;
    int yOffset = location.y - oldRectangle.y;
    location = new Point(newRectangle.x + xOffset, newRectangle.y + yOffset);

    Rectangle newBounds = new Rectangle(location.x, location.y, size.width, size.height);

    final boolean okToUpdateBounds = hideIfOutOfEditor ? oldRectangle.contains(newBounds) : oldRectangle.intersects(newBounds);
    if (okToUpdateBounds || hint.vetoesHiding()) {
      hint.setLocation(new RelativePoint(editor.getContentComponent(), location));
    }
    else {
      hint.hide();
    }
  }

  /**
   * In this method the point to show hint depends on current caret position.
   * So, first of all, editor will be scrolled to make the caret position visible.
   */
  public void showEditorHint(final LightweightHint hint, final Editor editor, @PositionFlags final short constraint, @HideFlags final int flags, final int timeout, final boolean reviveOnEditorChange) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    editor.getScrollingModel().runActionOnScrollingFinished(() -> {
      LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
      Point p = getHintPosition(hint, editor, pos, constraint);
      showEditorHint(hint, editor, p, flags, timeout, reviveOnEditorChange, createHintHint(editor, p, hint, constraint));
    });
  }

  /**
   * @param p                    point in layered pane coordinate system.
   * @param reviveOnEditorChange
   */
  public void showEditorHint(@NotNull final LightweightHint hint,
                             @NotNull Editor editor,
                             @NotNull Point p,
                             @HideFlags int flags,
                             int timeout,
                             boolean reviveOnEditorChange) {

    showEditorHint(hint, editor, p, flags, timeout, reviveOnEditorChange, HintManager.ABOVE);
  }

  public void showEditorHint(@NotNull final LightweightHint hint,
                             @NotNull Editor editor,
                             @NotNull Point p,
                             @HideFlags int flags,
                             int timeout,
                             boolean reviveOnEditorChange,
                             @PositionFlags short position) {

    showEditorHint(hint, editor, p, flags, timeout, reviveOnEditorChange, createHintHint(editor, p, hint, position));
  }

  public void showEditorHint(@NotNull final LightweightHint hint,
                             @NotNull Editor editor,
                             @NotNull Point p,
                             @HideFlags int flags,
                             int timeout,
                             boolean reviveOnEditorChange,
                             HintHint hintInfo) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    myHideAlarm.cancelAllRequests();

    hideHints(HIDE_BY_OTHER_HINT, false, false);

    if (editor != myLastEditor) {
      hideAllHints();
    }

    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) return;
    if (!ApplicationManager.getApplication().isActive()) return;

    updateLastEditor(editor);

    getPublisher().hintShown(editor.getProject(), hint, flags);

    Component component = hint.getComponent();

    // Set focus to control so that screen readers will announce the tooltip contents.
    // Users can press "ESC" to return to the editor.
    if (ScreenReader.isActive()) {
      hintInfo.setRequestFocus(true);
    }
    doShowInGivenLocation(hint, editor, p, hintInfo, true);

    ListenerUtil.addMouseListener(component, new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myHideAlarm.cancelAllRequests();
      }
    });
    ListenerUtil.addFocusListener(component, new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myHideAlarm.cancelAllRequests();
      }
    });

    if (BitUtil.isSet(flags, HIDE_BY_MOUSEOVER)) {
      ListenerUtil.addMouseMotionListener(component, new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          hideHints(HIDE_BY_MOUSEOVER, true, false);
        }
      });
    }

    myHintsStack.add(new HintInfo(hint, flags, reviveOnEditorChange));
    if (timeout > 0) {
      Timer timer = UIUtil.createNamedTimer("Hint timeout", timeout, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          hint.hide();
        }
      });
      timer.setRepeats(false);
      timer.start();
    }
  }

  @Override
  public void showHint(@NotNull final JComponent component, @NotNull RelativePoint p, int flags, int timeout) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    myHideAlarm.cancelAllRequests();

    hideHints(HIDE_BY_OTHER_HINT, false, false);

    final JBPopup popup =
      JBPopupFactory.getInstance().createComponentPopupBuilder(component, null).setRequestFocus(false).setResizable(false).setMovable(false)
        .createPopup();
    popup.show(p);

    ListenerUtil.addMouseListener(component, new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myHideAlarm.cancelAllRequests();
      }
    });
    ListenerUtil.addFocusListener(component, new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myHideAlarm.cancelAllRequests();
      }
    });

    final HintInfo info = new HintInfo(new LightweightHint(component) {
      @Override
      public void hide() {
        popup.cancel();
      }
    }, flags, false);
    myHintsStack.add(info);
    if (timeout > 0) {
      Timer timer = UIUtil.createNamedTimer("Popup timeout",timeout, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          popup.dispose();
        }
      });
      timer.setRepeats(false);
      timer.start();
    }
  }

  private static void doShowInGivenLocation(final LightweightHint hint, final Editor editor, Point p, HintHint hintInfo, boolean updateSize) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    JComponent externalComponent = getExternalComponent(editor);
    Dimension size = updateSize ? hint.getComponent().getPreferredSize() : hint.getComponent().getSize();

    if (hint.isRealPopup()) {
      final Point editorCorner = editor.getComponent().getLocation();
      SwingUtilities.convertPointToScreen(editorCorner, externalComponent);
      final Point point = new Point(p);
      SwingUtilities.convertPointToScreen(point, externalComponent);
      final Rectangle editorScreen = ScreenUtil.getScreenRectangle(point.x, point.y);

      SwingUtilities.convertPointToScreen(p, externalComponent);
      final Rectangle rectangle = new Rectangle(p, size);
      ScreenUtil.moveToFit(rectangle, editorScreen, null);
      p = rectangle.getLocation();
      SwingUtilities.convertPointFromScreen(p, externalComponent);
    }
    else if (externalComponent.getWidth() < p.x + size.width && !hintInfo.isAwtTooltip()) {
      p.x = Math.max(0, externalComponent.getWidth() - size.width);
    }

    if (hint.isVisible()) {
      if (updateSize) {
        hint.pack();
      }
      hint.updatePosition(hintInfo.getPreferredPosition());
      hint.updateLocation(p.x, p.y);
    }
    else {
      hint.show(externalComponent, p.x, p.y, editor.getContentComponent(), hintInfo);
    }
  }

  public static void updateLocation(final LightweightHint hint, final Editor editor, Point p) {
    doShowInGivenLocation(hint, editor, p, createHintHint(editor, p, hint, UNDER), false);
  }

  public static void adjustEditorHintPosition(final LightweightHint hint, final Editor editor, final Point p, @PositionFlags short constraint) {
    doShowInGivenLocation(hint, editor, p, createHintHint(editor, p, hint, constraint), true);
  }

  @Override
  public void hideAllHints() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (HintInfo info : getHintsStackArray()) {
      if (!info.hint.vetoesHiding()) {
        info.hint.hide();
      }
    }
    cleanup();
  }

  public void cleanup() {
    myHintsStack.clear();
    updateLastEditor(null);
  }

  /**
   * @return coordinates in layered pane coordinate system.
   */
  public Point getHintPosition(@NotNull LightweightHint hint, @NotNull Editor editor, @PositionFlags short constraint) {

    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    final DataContext dataContext = ((EditorEx)editor).getDataContext();
    final Rectangle dominantArea = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getData(dataContext);

    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    if (dominantArea != null) {
      return getHintPositionRelativeTo(hint, editor, constraint, dominantArea, pos);
    }

    JRootPane rootPane = editor.getComponent().getRootPane();
    if (rootPane != null) {
      JLayeredPane lp = rootPane.getLayeredPane();
      for (HintInfo info : getHintsStackArray()) {
        if (!info.hint.isSelectingHint()) continue;
        IdeTooltip tooltip = info.hint.getCurrentIdeTooltip();
        if (tooltip != null) {
          Point p = tooltip.getShowingPoint().getPoint(lp);
          if (info.hint != hint) {
            switch (constraint) {
              case ABOVE:
                if (tooltip.getPreferredPosition() == Balloon.Position.below) {
                  p.y -= tooltip.getPositionChangeY();
                }
                break;
              case UNDER:
              case RIGHT_UNDER:
                if (tooltip.getPreferredPosition() == Balloon.Position.above) {
                  p.y += tooltip.getPositionChangeY();
                }
                break;
              case RIGHT:
                if (tooltip.getPreferredPosition() == Balloon.Position.atLeft) {
                  p.x += tooltip.getPositionChangeX();
                }
                break;
              case LEFT:
                if (tooltip.getPreferredPosition() == Balloon.Position.atRight) {
                  p.x -= tooltip.getPositionChangeX();
                }
                break;
            }
          }
          return p;
        }

        Rectangle rectangle = info.hint.getBounds();
        JComponent c = info.hint.getComponent();
        rectangle = SwingUtilities.convertRectangle(c.getParent(), rectangle, lp);

        if (rectangle != null) {
          return getHintPositionRelativeTo(hint, editor, constraint, rectangle, pos);
        }
      }
    }

    return getHintPosition(hint, editor, pos, constraint);
  }

  private static Point getHintPositionRelativeTo(@NotNull final LightweightHint hint,
                                                 @NotNull final Editor editor,
                                                 @PositionFlags short constraint,
                                                 @NotNull final Rectangle lookupBounds,
                                                 final LogicalPosition pos) {

    JComponent externalComponent = getExternalComponent(editor);

    IdeTooltip ideTooltip = hint.getCurrentIdeTooltip();
    if (ideTooltip != null) {
      Point point = ideTooltip.getPoint();
      return SwingUtilities.convertPoint(ideTooltip.getComponent(), point, externalComponent);
    }

    Dimension hintSize = hint.getComponent().getPreferredSize();
    int layeredPaneHeight = externalComponent.getHeight();

    switch (constraint) {
      case LEFT: {
        int y = lookupBounds.y;
        if (y < 0) {
          y = 0;
        }
        else if (y + hintSize.height >= layeredPaneHeight) {
          y = layeredPaneHeight - hintSize.height;
        }
        return new Point(lookupBounds.x - hintSize.width, y);
      }

      case RIGHT: {
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
  public static Point getHintPosition(@NotNull LightweightHint hint,
                                      @NotNull Editor editor,
                                      @NotNull LogicalPosition pos,
                                      @PositionFlags short constraint) {
    return getHintPosition(hint, editor, pos, pos, constraint);
  }

  private static Point getHintPosition(@NotNull LightweightHint hint,
                                       @NotNull Editor editor,
                                       @NotNull LogicalPosition pos1,
                                       @NotNull LogicalPosition pos2,
                                       @PositionFlags short constraint) {
    return getHintPosition(hint, editor, pos1, pos2, constraint, Registry.is("editor.balloonHints"));
  }

  private static Point getHintPosition(@NotNull LightweightHint hint,
                                       @NotNull Editor editor,
                                       @NotNull LogicalPosition pos1,
                                       @NotNull LogicalPosition pos2,
                                       @PositionFlags short constraint,
                                       boolean showByBalloon) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return new Point();
    Point p = _getHintPosition(hint, editor, pos1, pos2, constraint, showByBalloon);
    JComponent externalComponent = getExternalComponent(editor);
    Dimension hintSize = hint.getComponent().getPreferredSize();
    if (constraint == ABOVE) {
      if (p.y < 0) {
        Point p1 = _getHintPosition(hint, editor, pos1, pos2, UNDER, showByBalloon);
        if (p1.y + hintSize.height <= externalComponent.getSize().height) {
          return p1;
        }
      }
    }
    else if (constraint == UNDER) {
      if (p.y + hintSize.height > externalComponent.getSize().height) {
        Point p1 = _getHintPosition(hint, editor, pos1, pos2, ABOVE, showByBalloon);
        if (p1.y >= 0) {
          return p1;
        }
      }
    }

    return p;
  }

  @NotNull
  public static JComponent getExternalComponent(@NotNull Editor editor) {
    JComponent externalComponent = editor.getComponent();
    JRootPane rootPane = externalComponent.getRootPane();
    if (rootPane == null) return externalComponent;
    JLayeredPane layeredPane = rootPane.getLayeredPane();
    return layeredPane != null ? layeredPane : rootPane;
  }

  private static Point _getHintPosition(@NotNull LightweightHint hint,
                                        @NotNull Editor editor,
                                        @NotNull LogicalPosition pos1,
                                        @NotNull LogicalPosition pos2,
                                        @PositionFlags short constraint,
                                        boolean showByBalloon) {
    Dimension hintSize = hint.getComponent().getPreferredSize();
    int line1 = pos1.line;
    int col1 = pos1.column;
    int line2 = pos2.line;
    int col2 = pos2.column;

    Point location;
    JComponent externalComponent = getExternalComponent(editor);
    JComponent internalComponent = editor.getContentComponent();
    if (constraint == RIGHT_UNDER) {
      Point p = editor.logicalPositionToXY(new LogicalPosition(line2, col2));
      if (!showByBalloon) {
        p.y += editor.getLineHeight();
      }
      location = SwingUtilities.convertPoint(internalComponent, p, externalComponent);
    }
    else {
      Point p = editor.logicalPositionToXY(new LogicalPosition(line1, col1));
      if (constraint == UNDER) {
        p.y += editor.getLineHeight();
      }
      location = SwingUtilities.convertPoint(internalComponent, p, externalComponent);
    }

    if (constraint == ABOVE && !showByBalloon) {
      location.y -= hintSize.height;
      int diff = location.x + hintSize.width - externalComponent.getWidth();
      if (diff > 0) {
        location.x = Math.max(location.x - diff, 0);
      }
    }

    if ((constraint == LEFT || constraint == RIGHT) && !showByBalloon) {
      location.y -= hintSize.height / 2;
      if (constraint == LEFT) {
        location.x -= hintSize.width;
      }
    }

    return location;
  }

  @Override
  public void showErrorHint(@NotNull Editor editor, @NotNull String text) {
    showErrorHint(editor, text, ABOVE);
  }

  @Override
  public void showErrorHint(@NotNull Editor editor, @NotNull String text, short position) {
    JComponent label = HintUtil.createErrorLabel(text);
    LightweightHint hint = new LightweightHint(label);
    Point p = getHintPosition(hint, editor, position);
    showEditorHint(hint, editor, p, HIDE_BY_ANY_KEY | HIDE_BY_TEXT_CHANGE | HIDE_BY_SCROLLING, 0, false, position);
  }

  @Override
  public void showInformationHint(@NotNull Editor editor, @NotNull String text) {
    JComponent label = HintUtil.createInformationLabel(text);
    showInformationHint(editor, label);
  }

  @Override
  public void showInformationHint(@NotNull Editor editor, @NotNull JComponent component) {
    // Set the accessible name so that screen readers announce the panel type (e.g. "Hint panel")
    // when the tooltip gets the focus.
    AccessibleContextUtil.setName(component, "Hint");
    showInformationHint(editor, component, true);
  }

  public void showInformationHint(@NotNull Editor editor, @NotNull JComponent component, boolean showByBalloon) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    LightweightHint hint = new LightweightHint(component);
    Point p = getHintPosition(hint, editor, ABOVE);
    showEditorHint(hint, editor, p, HIDE_BY_ANY_KEY | HIDE_BY_TEXT_CHANGE | HIDE_BY_SCROLLING, 0, false);
  }

  @Override
  public void showErrorHint(@NotNull Editor editor,
                            @NotNull String hintText,
                            int offset1,
                            int offset2,
                            short constraint,
                            int flags,
                            int timeout) {
    JComponent label = HintUtil.createErrorLabel(hintText);
    LightweightHint hint = new LightweightHint(label);
    final LogicalPosition pos1 = editor.offsetToLogicalPosition(offset1);
    final LogicalPosition pos2 = editor.offsetToLogicalPosition(offset2);
    final Point p = getHintPosition(hint, editor, pos1, pos2, constraint);
    showEditorHint(hint, editor, p, flags, timeout, false);
  }


  @Override
  public void showQuestionHint(@NotNull Editor editor, @NotNull String hintText, int offset1, int offset2, @NotNull QuestionAction action) {
    JComponent label = HintUtil.createQuestionLabel(hintText);
    LightweightHint hint = new LightweightHint(label);
    showQuestionHint(editor, offset1, offset2, hint, action, ABOVE);
  }

  public void showQuestionHint(@NotNull final Editor editor,
                               final int offset1,
                               final int offset2,
                               @NotNull final LightweightHint hint,
                               @NotNull final QuestionAction action,
                               @PositionFlags short constraint) {
    final LogicalPosition pos1 = editor.offsetToLogicalPosition(offset1);
    final LogicalPosition pos2 = editor.offsetToLogicalPosition(offset2);
    final Point p = getHintPosition(hint, editor, pos1, pos2, constraint);
    showQuestionHint(editor, p, offset1, offset2, hint, action, constraint);
  }


  public void showQuestionHint(@NotNull final Editor editor,
                               @NotNull final Point p,
                               final int offset1,
                               final int offset2,
                               @NotNull final LightweightHint hint,
                               @NotNull final QuestionAction action,
                               @PositionFlags short constraint) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    hideQuestionHint();
    TextAttributes attributes = new TextAttributes();
    attributes.setEffectColor(HintUtil.QUESTION_UNDERSCORE_COLOR);
    attributes.setEffectType(EffectType.LINE_UNDERSCORE);
    final RangeHighlighter highlighter = editor.getMarkupModel()
      .addRangeHighlighter(offset1, offset2, HighlighterLayer.ERROR + 1, attributes, HighlighterTargetArea.EXACT_RANGE);

    hint.addHintListener(new HintListener() {
      @Override
      public void hintHidden(EventObject event) {
        hint.removeHintListener(this);
        highlighter.dispose();

        if (myQuestionHint == hint) {
          myQuestionAction = null;
          myQuestionHint = null;
        }
      }
    });

    showEditorHint(hint, editor, p, HIDE_BY_ANY_KEY | HIDE_BY_TEXT_CHANGE | UPDATE_BY_SCROLLING | HIDE_IF_OUT_OF_EDITOR, 0, false,
                   createHintHint(editor, p, hint, constraint));
    myQuestionAction = action;
    myQuestionHint = hint;
  }

  public void hideQuestionHint() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myQuestionHint != null) {
      myQuestionHint.hide();
      myQuestionHint = null;
      myQuestionAction = null;
    }
  }

  public static HintHint createHintHint(Editor editor, Point p, LightweightHint hint, @PositionFlags short constraint) {
    return createHintHint(editor, p, hint, constraint, false);
  }

  //todo[nik,kirillk] perhaps 'createInEditorComponent' parameter should always be 'true'
  //old 'createHintHint' method uses LayeredPane as original component for HintHint so IdeTooltipManager.eventDispatched()
  //wasn't able to correctly hide tooltip after mouse move.
  public static HintHint createHintHint(Editor editor, Point p, LightweightHint hint, @PositionFlags short constraint, boolean createInEditorComponent) {
    JRootPane rootPane = editor.getComponent().getRootPane();
    if (rootPane == null) {
      return new HintHint(editor, p);
    }

    JLayeredPane lp = rootPane.getLayeredPane();
    HintHint hintInfo = new HintHint(editor, SwingUtilities.convertPoint(lp, p, editor.getContentComponent()));
    boolean showByBalloon = Registry.is("editor.balloonHints");
    if (showByBalloon) {
      if (!createInEditorComponent) {
        hintInfo = new HintHint(lp, p);
      }
      hintInfo.setAwtTooltip(true).setHighlighterType(true);
    }


    hintInfo.initStyleFrom(hint.getComponent());
    if (showByBalloon) {
      hintInfo.setBorderColor(new JBColor(Color.gray, Gray._140));
      hintInfo.setFont(hintInfo.getTextFont().deriveFont(Font.PLAIN));
      hintInfo.setCalloutShift((int)(editor.getLineHeight() * 0.1));
    }
    hintInfo.setPreferredPosition(Balloon.Position.above);
    if (constraint == UNDER || constraint == RIGHT_UNDER) {
      hintInfo.setPreferredPosition(Balloon.Position.below);
    }
    else if (constraint == RIGHT) {
      hintInfo.setPreferredPosition(Balloon.Position.atRight);
    }
    else if (constraint == LEFT) {
      hintInfo.setPreferredPosition(Balloon.Position.atLeft);
    }

    if (hint.isAwtTooltip()) {
      hintInfo.setAwtTooltip(true);
    }

    hintInfo.setPositionChangeShift(0, editor.getLineHeight());

    return hintInfo;
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

  private class MyAnActionListener implements AnActionListener {
    @Override
    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
      if (action instanceof ActionToIgnore) return;

      AnAction escapeAction = ActionManagerEx.getInstanceEx().getAction(IdeActions.ACTION_EDITOR_ESCAPE);
      if (action == escapeAction) return;

      hideHints(HIDE_BY_ANY_KEY, false, false);
    }


    @Override
    public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
    }

    @Override
    public void beforeEditorTyping(char c, DataContext dataContext) {
    }
  }

  /**
   * Hides all hints when selected editor changes. Unfortunately  user can change
   * selected editor by mouse. These clicks are not AnActions so they are not
   * fired by ActionManager.
   */
  private final class MyEditorManagerListener extends FileEditorManagerAdapter {
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      hideHints(0, false, true);
    }
  }

  /**
   * We have to spy for all opened projects to register MyEditorManagerListener into
   * all opened projects.
   */
  private final class MyProjectManagerListener extends ProjectManagerAdapter {
    @Override
    public void projectOpened(Project project) {
      project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, myEditorManagerListener);
    }

    @Override
    public void projectClosed(Project project) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      // avoid leak through com.intellij.codeInsight.hint.TooltipController.myCurrentTooltip
      TooltipController.getInstance().cancelTooltips();

      myQuestionAction = null;
      myQuestionHint = null;
      if (myLastEditor != null && project == myLastEditor.getProject()) {
        updateLastEditor(null);
      }
    }
  }

  boolean isEscapeHandlerEnabled() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (int i = myHintsStack.size() - 1; i >= 0; i--) {
      final HintInfo info = myHintsStack.get(i);
      if (!info.hint.isVisible()) {
        myHintsStack.remove(i);

        // We encountered situation when 'hint' instances use 'hide()' method as object destruction callback
        // (e.g. LineTooltipRenderer creates hint that overrides keystroke of particular action that produces hint and
        // de-registers it inside 'hide()'. That means that the hint can 'stuck' to old editor location if we just remove
        // it but don't call hide())
        info.hint.hide();
        continue;
      }

      if ((info.flags & (HIDE_BY_ESCAPE | HIDE_BY_ANY_KEY)) != 0) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hideHints(int mask, boolean onlyOne, boolean editorChanged) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    try {
      boolean done = false;

      for (int i = myHintsStack.size() - 1; i >= 0; i--) {
        final HintInfo info = myHintsStack.get(i);
        if (!info.hint.isVisible() && !info.hint.vetoesHiding()) {
          myHintsStack.remove(i);

          // We encountered situation when 'hint' instances use 'hide()' method as object destruction callback
          // (e.g. LineTooltipRenderer creates hint that overrides keystroke of particular action that produces hint and
          // de-registers it inside 'hide()'. That means that the hint can 'stuck' to old editor location if we just remove
          // it but don't call hide())
          info.hint.hide();
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
