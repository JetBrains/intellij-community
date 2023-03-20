// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeTooltip;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.NlsContexts.HintText;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

public class HintManagerImpl extends HintManager {

  private static final Logger LOG = Logger.getInstance(HintManager.class);

  private final MyEditorManagerListener myEditorManagerListener;

  @ApiStatus.Internal
  public static int getPriority(QuestionAction action) {
    return action instanceof PriorityQuestionAction ? ((PriorityQuestionAction)action).getPriority() : 0;
  }

  public boolean canShowQuestionAction(QuestionAction action) {
    return ClientHintManager.getCurrentInstance().canShowQuestionAction(action);
  }

  public interface ActionToIgnore {
  }

  record HintInfo(LightweightHint hint, @HideFlags int flags, boolean reviveOnEditorChange) {
  }

  public static HintManagerImpl getInstanceImpl() {
    return (HintManagerImpl)ApplicationManager.getApplication().getService(HintManager.class);
  }

  public HintManagerImpl() {
    myEditorManagerListener = new MyEditorManagerListener();

    final MyProjectManagerListener projectManagerListener = new MyProjectManagerListener();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      projectManagerListener.projectOpened(project);
    }

    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect();
    busConnection.subscribe(ProjectManager.TOPIC, projectManagerListener);
  }

  /**
   * Sets whether the next {@code showXxx} call will request the focus to the
   * newly shown tooltip. Note the flag applies only to the next call, i.e. is
   * reset to {@code false} after any {@code showXxx} is called.
   *
   * <p>Note: This method was created to avoid the code churn associated with
   * creating an overload to every {@code showXxx} method with an additional
   * {@code boolean requestFocus} parameter </p>
   */
  @Override
  public void setRequestFocusForNextHint(boolean requestFocus) {
    ClientHintManager.getCurrentInstance().setRequestFocusForNextHint(requestFocus);
  }

  public boolean performCurrentQuestionAction() {
    return ClientHintManager.getCurrentInstance().performCurrentQuestionAction();
  }

  @Override
  public boolean hasShownHintsThatWillHideByOtherHint(boolean willShowTooltip) {
    return ClientHintManager.getCurrentInstance().hasShownHintsThatWillHideByOtherHint(willShowTooltip);
  }

  static void updateScrollableHintPosition(VisibleAreaEvent e, LightweightHint hint, boolean hideIfOutOfEditor) {
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
    //in some rare cases lookup can appear just on the edge with the editor, so don't hide it on every typing
    Rectangle newBoundsForIntersectionCheck = new Rectangle(location.x - 1, location.y - 1, size.width + 2, size.height + 2);

    final boolean okToUpdateBounds = hideIfOutOfEditor ? oldRectangle.contains(newBounds) : oldRectangle.intersects(newBoundsForIntersectionCheck);
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    editor.getScrollingModel().runActionOnScrollingFinished(() -> {
      LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
      Point p = getHintPosition(hint, editor, pos, constraint);
      HintHint hintInfo = createHintHint(editor, p, hint, constraint);
      getClientManager(editor).showEditorHint(hint, editor, hintInfo, p, flags, timeout,
                                              reviveOnEditorChange, null);
    });
  }

  /**
   * @param p                    point in layered pane coordinate system.
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
    HintHint hintHint = createHintHint(editor, p, hint, position).setShowImmediately(true);
    showEditorHint(hint, editor, p, flags, timeout, reviveOnEditorChange, hintHint);
  }

  public void showEditorHint(@NotNull final LightweightHint hint,
                             @NotNull Editor editor,
                             @NotNull Point p,
                             @HideFlags int flags,
                             int timeout,
                             boolean reviveOnEditorChange,
                             @NotNull HintHint hintInfo) {
    getClientManager(editor).showEditorHint(hint, editor, hintInfo, p, flags,
                                            timeout, reviveOnEditorChange, null);
  }

  @Override
  public void showHint(@NotNull final JComponent component, @NotNull RelativePoint p, int flags, int timeout) {
    showHint(component, p,flags,timeout,null);
  }

  @Override
  public void showHint(@NotNull final JComponent component, @NotNull RelativePoint p, int flags, int timeout, @Nullable Runnable onHintHidden) {
    ClientHintManager.getCurrentInstance().showHint(component, p, flags, timeout, onHintHidden);
  }

  static void doShowInGivenLocation(final LightweightHint hint, final Editor editor, Point p, HintHint hintInfo, boolean updateSize) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    JComponent externalComponent = getExternalComponent(editor);
    Dimension size = updateSize ? hint.getComponent().getPreferredSize() : hint.getComponent().getSize();

    if (hint.isRealPopup() || hintInfo.isPopupForced()) {
      final Point point = new Point(p);
      SwingUtilities.convertPointToScreen(point, externalComponent);
      final Rectangle editorScreen = ScreenUtil.getScreenRectangle(point.x, point.y);

      p = new Point(p);
      if (hintInfo.getPreferredPosition() == Balloon.Position.atLeft) {
        p.x -= size.width;
      }
      SwingUtilities.convertPointToScreen(p, externalComponent);
      final Rectangle rectangle = new Rectangle(p, size);
      ScreenUtil.moveToFit(rectangle, editorScreen, null);
      p = rectangle.getLocation();
      SwingUtilities.convertPointFromScreen(p, externalComponent);
      if (hintInfo.getPreferredPosition() == Balloon.Position.atLeft) {
        p.x += size.width;
      }
    }
    else if (externalComponent.getWidth() < p.x + size.width && !hintInfo.isAwtTooltip()) {
      p.x = Math.max(0, externalComponent.getWidth() - size.width);
    }

    if(hint.isShouldBeReopen()){
      hint.hide(true);
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
    ClientHintManager.getCurrentInstance().hideAllHints();
  }

  public void cleanup() {
    ClientHintManager.getCurrentInstance().cleanup();
  }

  /**
   * @return coordinates in layered pane coordinate system.
   */
  public Point getHintPosition(@NotNull LightweightHint hint, @NotNull Editor editor, @PositionFlags short constraint) {
    return ClientHintManager.getCurrentInstance().getHintPosition(hint, editor, constraint);
  }

  static Point getHintPositionRelativeTo(@NotNull final LightweightHint hint,
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
      case LEFT -> {
        int y = lookupBounds.y;
        if (y < 0) {
          y = 0;
        }
        else if (y + hintSize.height >= layeredPaneHeight) {
          y = layeredPaneHeight - hintSize.height;
        }
        return new Point(lookupBounds.x - hintSize.width, y);
      }
      case RIGHT -> {
        int y = lookupBounds.y;
        if (y < 0) {
          y = 0;
        }
        else if (y + hintSize.height >= layeredPaneHeight) {
          y = layeredPaneHeight - hintSize.height;
        }
        return new Point(lookupBounds.x + lookupBounds.width, y);
      }
      case ABOVE -> {
        Point posAboveCaret = getHintPosition(hint, editor, pos, ABOVE);
        return new Point(lookupBounds.x, Math.min(posAboveCaret.y, lookupBounds.y - hintSize.height));
      }
      case UNDER -> {
        Point posUnderCaret = getHintPosition(hint, editor, pos, UNDER);
        return new Point(lookupBounds.x, Math.max(posUnderCaret.y, lookupBounds.y + lookupBounds.height));
      }
      default -> {
        LOG.error("");
        return null;
      }
    }
  }

  /**
   * @return position of hint in layered pane coordinate system
   */
  public static Point getHintPosition(@NotNull LightweightHint hint,
                                      @NotNull Editor editor,
                                      @NotNull LogicalPosition pos,
                                      @PositionFlags short constraint) {
    VisualPosition visualPos = editor.logicalToVisualPosition(pos);
    return getHintPosition(hint, editor, visualPos, visualPos, constraint);
  }

  /**
   * @return position of hint in layered pane coordinate system
   */
  public static Point getHintPosition(@NotNull LightweightHint hint,
                                      @NotNull Editor editor,
                                      @NotNull VisualPosition pos,
                                      @PositionFlags short constraint) {
    return getHintPosition(hint, editor, pos, pos, constraint);
  }

  static Point getHintPosition(@NotNull LightweightHint hint,
                                       @NotNull Editor editor,
                                       @NotNull VisualPosition pos1,
                                       @NotNull VisualPosition pos2,
                                       @PositionFlags short constraint) {
    return getHintPosition(hint, editor, pos1, pos2, constraint, Registry.is("editor.balloonHints"));
  }

  static Point getHintPosition(@NotNull LightweightHint hint,
                                       @NotNull Editor editor,
                                       @NotNull VisualPosition pos1,
                                       @NotNull VisualPosition pos2,
                                       @PositionFlags short constraint,
                                       boolean showByBalloon) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return new Point();
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
                                        @NotNull VisualPosition pos1,
                                        @NotNull VisualPosition pos2,
                                        @PositionFlags short constraint,
                                        boolean showByBalloon) {
    Dimension hintSize = hint.getComponent().getPreferredSize();

    Point location;
    JComponent externalComponent = getExternalComponent(editor);
    JComponent internalComponent = editor.getContentComponent();
    if (constraint == RIGHT_UNDER) {
      Point p = editor.visualPositionToXY(pos2);
      if (!showByBalloon) {
        p.y += editor.getLineHeight();
      }
      location = SwingUtilities.convertPoint(internalComponent, p, externalComponent);
    }
    else {
      Point p = editor.visualPositionToXY(pos1);
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
  public void showErrorHint(@NotNull Editor editor, @NotNull @HintText String text) {
    showErrorHint(editor, text, ABOVE);
  }

  @Override
  public void showErrorHint(@NotNull Editor editor, @NotNull @HintText String text, short position) {
    JComponent label = HintUtil.createErrorLabel(text);
    LightweightHint hint = new LightweightHint(label);
    Point p = getClientManager(editor).getHintPosition(hint, editor, position);
    int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
    showEditorHint(hint, editor, p, flags, 0, false);
  }

  @Override
  public void showInformationHint(@NotNull Editor editor, @NotNull String text, @PositionFlags short position) {
    showInformationHint(editor, text, null, position);
  }

  @Override
  public void showInformationHint(@NotNull Editor editor, @NotNull String text, @Nullable HyperlinkListener listener) {
    showInformationHint(editor, text, listener, ABOVE);
  }

  private void showInformationHint(@NotNull Editor editor,
                                   @NotNull @HintText String text,
                                   @Nullable HyperlinkListener listener,
                                   @PositionFlags short position) {
    JComponent label = HintUtil.createInformationLabel(text, listener, null, null);
    showInformationHint(editor, label, position, null);
  }

  @Override
  public void showInformationHint(@NotNull Editor editor, @NotNull JComponent component) {
    showInformationHint(editor, component, null);
  }

  @Override
  public void showInformationHint(@NotNull Editor editor,
                                  @NotNull JComponent component,
                                  @Nullable Runnable onHintHidden) {
    // Set the accessible name so that screen readers announce the panel type (e.g. "Hint panel")
    // when the tooltip gets the focus.
    showInformationHint(editor, component, ABOVE, onHintHidden);
  }

  public void showInformationHint(@NotNull Editor editor,
                                  @NotNull JComponent component,
                                  @PositionFlags short position,
                                  @Nullable Runnable onHintHidden) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    LightweightHint hint = new LightweightHint(component);
    Point p = getClientManager(editor).getHintPosition(hint, editor, position);
    int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;

    AccessibleContextUtil.setName(hint.getComponent(), IdeBundle.message("information.hint.accessible.context.name"));
    if (onHintHidden != null) {
      hint.addHintListener((event) -> {
        onHintHidden.run();
      });
    }

    showEditorHint(hint, editor, p, flags, 0, false);
  }

  @NotNull
  private static ClientHintManager getClientManager(@NotNull Editor editor) {
    try (AccessToken ignored = ClientId.withClientId(ClientEditorManager.getClientId(editor))) {
      return ClientHintManager.getCurrentInstance();
    }
  }

  @Override
  public void showErrorHint(@NotNull Editor editor,
                            @NotNull @HintText String hintText,
                            int offset1,
                            int offset2,
                            short constraint,
                            int flags,
                            int timeout) {
    JComponent label = HintUtil.createErrorLabel(hintText);
    LightweightHint hint = new LightweightHint(label);
    final VisualPosition pos1 = editor.offsetToVisualPosition(offset1);
    final VisualPosition pos2 = editor.offsetToVisualPosition(offset2);
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
    final VisualPosition pos1 = editor.offsetToVisualPosition(offset1);
    final VisualPosition pos2 = editor.offsetToVisualPosition(offset2);
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
    int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.UPDATE_BY_SCROLLING |
                HintManager.HIDE_IF_OUT_OF_EDITOR | HintManager.DONT_CONSUME_ESCAPE;
    getClientManager(editor).showQuestionHint(editor, p, offset1, offset2, hint, flags, action, constraint);
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
      if (!hintInfo.isBorderColorSet()) {
        hintInfo.setBorderColor(new JBColor(Color.gray, Gray._140));
      }
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

  boolean isEscapeHandlerEnabled() {
    return ClientHintManager.getCurrentInstance().isEscapeHandlerEnabled();
  }

  @Override
  public boolean hideHints(int mask, boolean onlyOne, boolean editorChanged) {
    return ClientHintManager.getCurrentInstance().hideHints(mask, onlyOne, editorChanged);
  }

  static final class EditorHintListenerHolder {
    static final EditorHintListener ourEditorHintPublisher =
      ApplicationManager.getApplication().getMessageBus().syncPublisher(EditorHintListener.TOPIC);

    private EditorHintListenerHolder() {
    }
  }

  public static EditorHintListener getPublisher() {
    return EditorHintListenerHolder.ourEditorHintPublisher;
  }

  /**
   * Hides all hints when selected editor changes. Unfortunately  user can change
   * selected editor by mouse. These clicks are not AnActions so they are not
   * fired by ActionManager.
   */
  private final class MyEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      hideHints(0, false, true);
    }
  }

  /**
   * We have to spy for all opened projects to register MyEditorManagerListener into
   * all opened projects.
   */
  private final class MyProjectManagerListener implements ProjectManagerListener {
    @Override
    public void projectOpened(@NotNull Project project) {
      project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, myEditorManagerListener);
    }

    @Override
    public void projectClosed(@NotNull Project project) {
      ApplicationManager.getApplication().assertIsDispatchThread();

      // avoid leak through com.intellij.codeInsight.hint.TooltipController.myCurrentTooltip
      TooltipController.getInstance().cancelTooltips();
      ApplicationManager.getApplication().invokeLater(() -> hideHints(0, false, false));

      for (ClientHintManager instance : ClientHintManager.getAllInstances()) {
        instance.onProjectClosed(project);
      }
    }
  }
}
