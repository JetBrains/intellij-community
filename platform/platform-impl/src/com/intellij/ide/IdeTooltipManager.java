// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.application.options.RegistryManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;

// Android team doesn't want to use new mockito for now, so, class cannot be final
public class IdeTooltipManager implements Disposable, AWTEventListener {
  public static final ColorKey TOOLTIP_COLOR_KEY = ColorKey.createColorKey("TOOLTIP", null);

  private static final Key<IdeTooltip> CUSTOM_TOOLTIP = Key.create("custom.tooltip");
  private static final MouseEventAdapter<Void> DUMMY_LISTENER = new MouseEventAdapter<>(null);

  public static final Color GRAPHITE_COLOR = new Color(100, 100, 100, 230);
  private static final String IDE_TOOLTIP_CALLOUT_KEY = "ide.tooltip.callout";
  private static final String IDE_HELPTOOLTIP_ENABLED_KEY = "ide.helptooltip.enabled";
  private boolean isEnabled;

  private HelpTooltipManager helpTooltipManager;
  private boolean myHideHelpTooltip;

  private volatile Component myCurrentComponent;
  private volatile Component myQueuedComponent;
  private volatile Component myProcessingComponent;

  private Balloon myBalloon;

  private MouseEvent myCurrentEvent;
  private boolean myCurrentTipIsCentered;

  private Disposable lastDisposable;

  private Runnable hideRunnable;

  private boolean myShowDelay = true;

  private final Alarm myAlarm = new Alarm();

  private int myX;
  private int myY;

  private IdeTooltip currentTooltip;
  private Runnable myShowRequest;
  private IdeTooltip myQueuedTooltip;

  public IdeTooltipManager() {
    Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
        hideCurrent(null, action, event);
      }
    });

    RegistryManager registryManager = RegistryManager.getInstance();
    isEnabled = registryManager.is(IDE_TOOLTIP_CALLOUT_KEY);
    processEnabled(registryManager.is(IDE_HELPTOOLTIP_ENABLED_KEY));
  }

  static final class MyRegistryListener implements RegistryValueListener {
    @Override
    public void afterValueChanged(@NotNull RegistryValue value) {
      String key = value.getKey();
      if (key.equals(IDE_TOOLTIP_CALLOUT_KEY)) {
        IdeTooltipManager instance = getInstance();
        instance.isEnabled = value.asBoolean();
        instance.processEnabled(Registry.is(IDE_HELPTOOLTIP_ENABLED_KEY));
      }
      if (key.equals(IDE_HELPTOOLTIP_ENABLED_KEY)) {
        getInstance().processEnabled(value.asBoolean());
      }
    }
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (!isEnabled) {
      return;
    }

    MouseEvent me = (MouseEvent)event;
    myProcessingComponent = me.getComponent();
    try {
      if (me.getID() == MouseEvent.MOUSE_ENTERED) {
        boolean canShow = true;
        if (componentContextHasChanged(myProcessingComponent)) {
          canShow = hideCurrent(me, null, null);
        }
        if (canShow) {
          maybeShowFor(myProcessingComponent, me);
        }
      }
      else if (me.getID() == MouseEvent.MOUSE_EXITED) {
        // we hide tooltip (but not hint!) when it's shown over myComponent and mouse exits this component
        if (myProcessingComponent == myCurrentComponent &&
            currentTooltip != null &&
            !currentTooltip.isHint() &&
            myBalloon != null) {
          myBalloon.setAnimationEnabled(false);
          hideCurrent(null, null, null, null, false);
        }
        else if (myProcessingComponent == myCurrentComponent || myProcessingComponent == myQueuedComponent) {
          hideCurrent(me, null, null);
        }
      }
      else if (me.getID() == MouseEvent.MOUSE_MOVED) {
        if (myProcessingComponent == myCurrentComponent || myProcessingComponent == myQueuedComponent) {
          if (myBalloon != null && myBalloon.wasFadedIn()) {
            maybeShowFor(myProcessingComponent, me);
          }
          else {
            if (!myCurrentTipIsCentered) {
              myX = me.getX();
              myY = me.getY();
              if (myProcessingComponent instanceof JComponent &&
                  !isTooltipDefined((JComponent)myProcessingComponent, me) &&
                  (myQueuedTooltip == null || !myQueuedTooltip.isHint())) {
                hideCurrent(me, null, null);//There is no tooltip or hint here, let's proceed it as MOUSE_EXITED
              }
              else {
                maybeShowFor(myProcessingComponent, me);
              }
            }
          }
        }
        else if (myCurrentComponent == null && myQueuedComponent == null) {
          maybeShowFor(myProcessingComponent, me);
        }
        else if (myQueuedComponent == null) {
          hideCurrent(me);
        }
      }
      else if (me.getID() == MouseEvent.MOUSE_PRESSED) {
        boolean clickOnTooltip = myBalloon != null &&
                                 myBalloon == JBPopupFactory.getInstance().getParentBalloonFor(myProcessingComponent);
        if (myProcessingComponent == myCurrentComponent || (clickOnTooltip && !isClickProcessor(myBalloon))) {
          hideCurrent(me, null, null, null, !clickOnTooltip);
        }
      }
      else if (me.getID() == MouseEvent.MOUSE_DRAGGED) {
        hideCurrent(me, null, null);
      }
    }
    finally {
      myProcessingComponent = null;
    }
  }

  private boolean componentContextHasChanged(Component eventComponent) {
    if (eventComponent == myCurrentComponent) return false;

    if (myQueuedTooltip != null) {
      // The case when a tooltip is going to appear on the Component but the MOUSE_ENTERED event comes to the Component before it,
      // we dont want to hide the tooltip in that case (IDEA-194208)
      Point tooltipPoint = myQueuedTooltip.getPoint();
      if (tooltipPoint != null) {
        Component realQueuedComponent =
          SwingUtilities.getDeepestComponentAt(myQueuedTooltip.getComponent(), tooltipPoint.x, tooltipPoint.y);
        return eventComponent != realQueuedComponent;
      }
    }

    return true;
  }

  private void maybeShowFor(Component c, MouseEvent me) {
    showForComponent(c, me, false);
  }

  private void showForComponent(Component c, MouseEvent me, boolean now) {
    if (!(c instanceof JComponent)) return;

    JComponent comp = (JComponent)c;
    Window wnd = SwingUtilities.getWindowAncestor(comp);
    if (wnd == null) return;

    if (!wnd.isActive()) {
      if (JBPopupFactory.getInstance().isChildPopupFocused(wnd)) return;
    }

    if (!isTooltipDefined(comp, me)) {
      hideCurrent(null);
      return;
    }

    boolean centerDefault = Boolean.TRUE.equals(comp.getClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT));
    boolean centerStrict = Boolean.TRUE.equals(comp.getClientProperty(UIUtil.CENTER_TOOLTIP_STRICT));
    int shift = centerStrict ? 0 : centerDefault ? 4 : 0;

    // Balloon may appear exactly above useful content, such behavior is rather annoying.
    Rectangle rowBounds = null;
    if (c instanceof JTree) {
      TreePath path = ((JTree)c).getClosestPathForLocation(me.getX(), me.getY());
      if (path != null) {
        rowBounds = ((JTree)c).getPathBounds(path);
      }
    }
    else if (c instanceof JList) {
      int row = ((JList<?>)c).locationToIndex(me.getPoint());
      if (row > -1) {
        rowBounds = ((JList<?>)c).getCellBounds(row, row);
      }
    }
    if (rowBounds != null && rowBounds.y + 4 < me.getY()) {
      shift += me.getY() - rowBounds.y - 4;
    }

    showTooltipForEvent(comp, me, centerStrict || centerDefault, shift, -shift, -shift, now);
  }

  private boolean isTooltipDefined(JComponent comp, MouseEvent me) {
    return !StringUtil.isEmpty(comp.getToolTipText(me)) || getCustomTooltip(comp) != null;
  }


  private void showTooltipForEvent(final JComponent c,
                                   final MouseEvent me,
                                   final boolean toCenter,
                                   final int shift,
                                   final int posChangeX,
                                   final int posChangeY,
                                   final boolean now) {
    IdeTooltip tooltip = getCustomTooltip(c);
    if (tooltip == null) {
      if (helpTooltipManager != null) {
        myCurrentComponent = c;
        myHideHelpTooltip = true;
        helpTooltipManager.showTooltip(c, me);
        return;
      }

      String aText = String.valueOf(c.getToolTipText(me));
      tooltip = new IdeTooltip(c, me.getPoint(), null, /*new Object()*/c, aText) {
        @Override
        protected boolean beforeShow() {
          myCurrentEvent = me;

          if (!c.isShowing()) return false;

          String text = c.getToolTipText(myCurrentEvent);
          if (text == null || text.trim().isEmpty()) return false;

          Rectangle visibleRect = c.getParent() instanceof JViewport ? ((JViewport)c.getParent()).getViewRect() :
                                  IdeMouseEventDispatcher.isDiagramViewComponent(c) ? c.getBounds() : c.getVisibleRect();
          if (!visibleRect.contains(getPoint())) return false;

          JLayeredPane layeredPane = ComponentUtil.getParentOfType((Class<? extends JLayeredPane>)JLayeredPane.class, (Component)c);

          final JEditorPane pane = initPane(text, new HintHint(me).setAwtTooltip(true), layeredPane);
          final Wrapper wrapper = new Wrapper(pane);
          setTipComponent(wrapper);
          return true;
        }
      }.setToCenter(toCenter).setCalloutShift(shift).setPositionChangeShift(posChangeX, posChangeY).setLayer(Balloon.Layer.top);
    }
    else if (currentTooltip == tooltip) {
      return;//Don't re-show the same custom tooltip on every mouse movement
    }

    show(tooltip, now);
  }

  /**
   * Checks the component for tooltip visualization activities.
   * Can be called from non-dispatch threads.
   *
   * @return true if the component is taken a part in any tooltip activity
   */
  @ApiStatus.Experimental
  @Contract(value = "null -> false", pure = true)
  public boolean isProcessing(@Nullable Component tooltipOwner) {
    return tooltipOwner != null && (tooltipOwner == myCurrentComponent
                                    || tooltipOwner == myQueuedComponent
                                    || tooltipOwner == myProcessingComponent);
  }

  /**
   * Updates shown tooltip pop-up in current position with actual tooltip text if it is already visible.
   * The action is useful for background-calculated tooltip (ex. crumbs tooltips).
   * Does nothing in other cases.
   *
   * @param tooltipOwner for which the tooltip is updating
   */
  @ApiStatus.Experimental
  public void updateShownTooltip(@Nullable Component tooltipOwner) {
    if (!hasCurrent() || myCurrentComponent == null || myCurrentComponent != tooltipOwner) {
      return;
    }

    try {
      MouseEvent reposition;
      if (GraphicsEnvironment.isHeadless()) {
        reposition = myCurrentEvent;
      }
      else {
        Point topLeftComponent = myCurrentComponent.getLocationOnScreen();
        Point screenLocation = MouseInfo.getPointerInfo().getLocation();
        reposition = new MouseEvent(
          myCurrentEvent.getComponent(),
          myCurrentEvent.getID(),
          myCurrentEvent.getWhen(),
          myCurrentEvent.getModifiers(),
          screenLocation.x - topLeftComponent.x,
          screenLocation.y - topLeftComponent.y,
          screenLocation.x,
          screenLocation.y,
          myCurrentEvent.getClickCount(),
          myCurrentEvent.isPopupTrigger(),
          myCurrentEvent.getButton());
      }
      showForComponent(myCurrentComponent, reposition, true);
    }
    catch (IllegalComponentStateException ignore) {
    }
  }

  public void setCustomTooltip(JComponent component, IdeTooltip tooltip) {
    component.putClientProperty(CUSTOM_TOOLTIP, tooltip);
    // We need to register a dummy mouse listener to make sure events will be generated for this specific component, not its parent
    component.removeMouseListener(DUMMY_LISTENER);
    component.removeMouseMotionListener(DUMMY_LISTENER);
    if (tooltip != null) {
      component.addMouseListener(DUMMY_LISTENER);
      component.addMouseMotionListener(DUMMY_LISTENER);
    }
  }

  public IdeTooltip getCustomTooltip(JComponent component) {
    return ClientProperty.get(component, CUSTOM_TOOLTIP);
  }

  public IdeTooltip show(final IdeTooltip tooltip, boolean now) {
    return show(tooltip, now, true);
  }

  public IdeTooltip show(final IdeTooltip tooltip, boolean now, final boolean animationEnabled) {
    myAlarm.cancelAllRequests();

    hideCurrent(null, tooltip);

    myQueuedComponent = tooltip.getComponent();
    myQueuedTooltip = tooltip;

    myShowRequest = () -> {
      if (myShowRequest == null) {
        return;
      }

      if (myQueuedComponent != tooltip.getComponent() || !tooltip.getComponent().isShowing()) {
        hideCurrent(null, tooltip, null, null, animationEnabled);
        return;
      }

      if (tooltip.beforeShow()) {
        doShow(tooltip, animationEnabled);
      }
      else {
        hideCurrent(null, tooltip, null, null, animationEnabled);
      }
    };

    if (now) {
      myShowRequest.run();
    }
    else {
      myAlarm.addRequest(myShowRequest, myShowDelay ? tooltip.getShowDelay() : tooltip.getInitialReshowDelay());
    }

    return tooltip;
  }

  private void doShow(final IdeTooltip tooltip, boolean animationEnabled) {
    boolean toCenterX;
    boolean toCenterY;

    boolean toCenter = tooltip.isToCenter();
    boolean small = false;
    if (!toCenter && tooltip.isToCenterIfSmall()) {
      Dimension size = tooltip.getComponent().getSize();
      toCenterX = size.width < 64;
      toCenterY = size.height < 64;
      toCenter = toCenterX || toCenterY;
      small = true;
    }
    else {
      toCenterX = true;
      toCenterY = true;
    }

    Point effectivePoint = tooltip.getPoint();
    if (toCenter) {
      Rectangle bounds = tooltip.getComponent().getBounds();
      effectivePoint.x = toCenterX ? bounds.width / 2 : effectivePoint.x;
      effectivePoint.y = toCenterY ? bounds.height / 2 : effectivePoint.y;
    }

    if (myCurrentComponent == tooltip.getComponent() && myBalloon != null && !myBalloon.isDisposed()) {
      myBalloon.show(new RelativePoint(tooltip.getComponent(), effectivePoint), tooltip.getPreferredPosition());
      return;
    }

    if (myCurrentComponent == tooltip.getComponent() && effectivePoint.equals(new Point(myX, myY))) {
      return;
    }

    Color bg = tooltip.getTextBackground() != null ? tooltip.getTextBackground() : getTextBackground(true);
    Color fg = tooltip.getTextForeground() != null ? tooltip.getTextForeground() : getTextForeground(true);
    Color borderColor = tooltip.getBorderColor() != null ? tooltip.getBorderColor() : JBUI.CurrentTheme.Tooltip.borderColor();

    BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(tooltip.getTipComponent())
      .setFillColor(bg)
      .setBorderColor(borderColor)
      .setBorderInsets(tooltip.getBorderInsets())
      .setAnimationCycle(animationEnabled ? RegistryManager.getInstance().intValue("ide.tooltip.animationCycle") : 0)
      .setShowCallout(true)
      .setCalloutShift(small && tooltip.getCalloutShift() == 0 ? 2 : tooltip.getCalloutShift())
      .setPositionChangeXShift(tooltip.getPositionChangeX())
      .setPositionChangeYShift(tooltip.getPositionChangeY())
      .setHideOnKeyOutside(!tooltip.isExplicitClose())
      .setHideOnAction(!tooltip.isExplicitClose())
      .setRequestFocus(tooltip.isRequestFocus())
      .setLayer(tooltip.getLayer());
    tooltip.getTipComponent().setForeground(fg);
    tooltip.getTipComponent().setBorder(tooltip.getComponentBorder());
    tooltip.getTipComponent().setFont(tooltip.getFont() != null ? tooltip.getFont() : getTextFont(true));


    myBalloon = builder.createBalloon();

    myBalloon.setAnimationEnabled(animationEnabled);
    tooltip.setUi(myBalloon instanceof IdeTooltip.Ui ? (IdeTooltip.Ui)myBalloon : null);
    myCurrentComponent = tooltip.getComponent();
    myX = effectivePoint.x;
    myY = effectivePoint.y;
    myCurrentTipIsCentered = toCenter;
    currentTooltip = tooltip;
    myShowRequest = null;
    myQueuedComponent = null;
    myQueuedTooltip = null;

    lastDisposable = myBalloon;
    Disposer.register(lastDisposable, new Disposable() {
      @Override
      public void dispose() {
        lastDisposable = null;
      }
    });

    myBalloon.show(new RelativePoint(tooltip.getComponent(), effectivePoint), tooltip.getPreferredPosition());
    myAlarm.addRequest(() -> {
      if (currentTooltip == tooltip && tooltip.canBeDismissedOnTimeout()) {
        hideCurrent(null, null, null);
      }
    }, tooltip.getDismissDelay());
  }

  @SuppressWarnings("UnusedParameters")
  public Color getTextForeground(boolean awtTooltip) {
    return UIUtil.getToolTipForeground();
  }

  @SuppressWarnings("UnusedParameters")
  public Color getLinkForeground(boolean awtTooltip) {
    return JBUI.CurrentTheme.Link.Foreground.ENABLED;
  }

  @SuppressWarnings("UnusedParameters")
  public Color getTextBackground(boolean awtTooltip) {
    Color color = EditorColorsUtil.getGlobalOrDefaultColor(TOOLTIP_COLOR_KEY);
    return color != null ? color : UIUtil.getToolTipBackground();
  }

  @SuppressWarnings("UnusedParameters")
  public String getUlImg(boolean awtTooltip) {
    return StartupUiUtil.isUnderDarcula() ? "/general/mdot-white.png" : "/general/mdot.png";
  }


  /**
   * @deprecated use {@link JBUI.CurrentTheme.Tooltip#borderColor()} instead.
   */
  @SuppressWarnings("UnusedParameters")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public Color getBorderColor(boolean awtTooltip) {
    return JBUI.CurrentTheme.Tooltip.borderColor();
  }

  @SuppressWarnings("UnusedParameters")
  public boolean isOwnBorderAllowed(boolean awtTooltip) {
    return !awtTooltip;
  }

  @SuppressWarnings("UnusedParameters")
  public boolean isOpaqueAllowed(boolean awtTooltip) {
    return !awtTooltip;
  }

  @SuppressWarnings("UnusedParameters")
  public Font getTextFont(boolean awtTooltip) {
    return UIManager.getFont("ToolTip.font");
  }

  public boolean hasCurrent() {
    return currentTooltip != null;
  }

  public boolean hideCurrent(@Nullable MouseEvent me) {
    return hideCurrent(me, null);
  }

  private boolean hideCurrent(@Nullable MouseEvent me, @Nullable AnAction action, @Nullable AnActionEvent event) {
    return hideCurrent(me, null, action, event, isAnimationEnabled(myBalloon));
  }

  private boolean hideCurrent(@Nullable MouseEvent me,
                              @Nullable IdeTooltip tooltipToShow) {
    return hideCurrent(me, tooltipToShow, null, null, isAnimationEnabled(myBalloon));
  }

  private boolean hideCurrent(@Nullable MouseEvent me,
                              @Nullable IdeTooltip tooltipToShow,
                              @Nullable AnAction action,
                              @Nullable AnActionEvent event,
                              final boolean animationEnabled) {
    if (helpTooltipManager != null && myHideHelpTooltip) {
      hideCurrentNow(false);
      return true;
    }

    if (currentTooltip != null && me != null && currentTooltip.isInside(new RelativePoint(me))) {
      if (me.getButton() == MouseEvent.NOBUTTON || myBalloon == null || isBlockClicks(myBalloon)) {
        return false;
      }
    }

    myShowRequest = null;
    myQueuedComponent = null;
    myQueuedTooltip = null;

    if (currentTooltip == null) return true;

    if (myBalloon != null) {
      RelativePoint target = me != null ? new RelativePoint(me) : null;
      boolean isInsideOrMovingForward = target != null &&
                                        (isInside(myBalloon, target) || isMovingForward(myBalloon, target));
      boolean canAutoHide = currentTooltip.canAutohideOn(new TooltipEvent(me, isInsideOrMovingForward, action, event));
      boolean implicitMouseMove = me != null &&
                                  (me.getID() == MouseEvent.MOUSE_MOVED ||
                                   me.getID() == MouseEvent.MOUSE_EXITED ||
                                   me.getID() == MouseEvent.MOUSE_ENTERED);
      if (!canAutoHide
          || (isInsideOrMovingForward && implicitMouseMove)
          || (currentTooltip.isExplicitClose() && implicitMouseMove)
          || (tooltipToShow != null && !tooltipToShow.isHint() && Comparing.equal(currentTooltip, tooltipToShow))) {
        if (hideRunnable != null) {
          hideRunnable = null;
        }
        return false;
      }
    }

    hideRunnable = () -> {
      if (hideRunnable != null) {
        hideCurrentNow(animationEnabled);
        hideRunnable = null;
      }
    };

    if (me != null && me.getButton() == MouseEvent.NOBUTTON) {
      myAlarm.addRequest(hideRunnable, RegistryManager.getInstance().intValue("ide.tooltip.autoDismissDeadZone"));
    }
    else {
      hideRunnable.run();
      hideRunnable = null;
    }

    return true;
  }

  public void hideCurrentNow(boolean animationEnabled) {
    if (helpTooltipManager != null) {
      helpTooltipManager.hideTooltip();
    }

    if (myBalloon != null) {
      myBalloon.setAnimationEnabled(animationEnabled);
      myBalloon.hide();
      currentTooltip.onHidden();
      myShowDelay = false;
      myAlarm.addRequest(() -> myShowDelay = true, RegistryManager.getInstance().intValue("ide.tooltip.reshowDelay"));
    }

    myHideHelpTooltip = false;
    myShowRequest = null;
    currentTooltip = null;

    myBalloon = null;

    myCurrentComponent = null;
    myQueuedComponent = null;
    myQueuedTooltip = null;
    myCurrentEvent = null;
    myCurrentTipIsCentered = false;
    myX = -1;
    myY = -1;
  }

  private void processEnabled(boolean isHelpTooltipEnabled) {
    if (isEnabled) {
      ToolTipManager.sharedInstance().setEnabled(false);
      if (isHelpTooltipEnabled) {
        if (helpTooltipManager == null) {
          helpTooltipManager = new HelpTooltipManager();
        }
        return;
      }
    }
    else {
      ToolTipManager.sharedInstance().setEnabled(true);
    }
    if (helpTooltipManager != null) {
      helpTooltipManager.hideTooltip();
      helpTooltipManager = null;
    }
  }

  @Override
  public void dispose() {
    hideCurrentNow(false);
    if (lastDisposable != null) {
      Disposer.dispose(lastDisposable);
    }
  }

  public static IdeTooltipManager getInstance() {
    return ApplicationManager.getApplication().getService(IdeTooltipManager.class);
  }

  public void hide(@Nullable IdeTooltip tooltip) {
    if (currentTooltip == tooltip || tooltip == null || tooltip == myQueuedTooltip) {
      hideCurrent(null, null, null);
    }
  }

  public void cancelAutoHide() {
    hideRunnable = null;
  }

  public static JEditorPane initPane(@Tooltip String text, final HintHint hintHint, @Nullable final JLayeredPane layeredPane) {
    return initPane(new Html(text), hintHint, layeredPane, true);
  }

  public static JEditorPane initPane(@Tooltip Html html, final HintHint hintHint, @Nullable final JLayeredPane layeredPane,
                                     boolean limitWidthToScreen) {
    final Ref<Dimension> prefSize = new Ref<>(null);
    @NonNls String text = HintUtil.prepareHintText(html, hintHint);

    final boolean[] prefSizeWasComputed = {false};
    final JEditorPane pane = limitWidthToScreen ? new JEditorPane() {
      @Override
      public Dimension getPreferredSize() {
        if (!prefSizeWasComputed[0] && hintHint.isAwtTooltip()) {
          JLayeredPane lp = layeredPane;
          if (lp == null) {
            JRootPane rootPane = UIUtil.getRootPane(this);
            if (rootPane != null) {
              lp = rootPane.getLayeredPane();
            }
          }

          Dimension size;
          if (lp != null) {
            AppUIUtil.targetToDevice(this, lp);
            size = lp.getSize();
            prefSizeWasComputed[0] = true;
          }
          else {
            size = ScreenUtil.getScreenRectangle(0, 0).getSize();
          }
          int fitWidth = (int)(size.width * 0.8);
          Dimension prefSizeOriginal = super.getPreferredSize();
          if (prefSizeOriginal.width > fitWidth) {
            setSize(new Dimension(fitWidth, Integer.MAX_VALUE));
            Dimension fixedWidthSize = super.getPreferredSize();
            Dimension minSize = super.getMinimumSize();
            prefSize.set(new Dimension(Math.max(fitWidth, minSize.width), fixedWidthSize.height));
          }
          else {
            prefSize.set(new Dimension(prefSizeOriginal));
          }
        }

        Dimension s = prefSize.get() != null ? new Dimension(prefSize.get()) : super.getPreferredSize();
        Border b = getBorder();
        if (b != null) {
          JBInsets.addTo(s, b.getBorderInsets(this));
        }
        return s;
      }

      @Override
      public void setPreferredSize(Dimension preferredSize) {
        super.setPreferredSize(preferredSize);
        prefSize.set(preferredSize);
      }
    } : new JEditorPane();

    HTMLEditorKit kit = new HTMLEditorKitBuilder().withViewFactoryExtensions((elem, view) -> {
      AttributeSet attrs = elem.getAttributes();
      Object elementName = attrs.getAttribute(AbstractDocument.ElementNameAttribute);
      Object o = elementName != null ? null : attrs.getAttribute(StyleConstants.NameAttribute);
      if (o instanceof HTML.Tag) {
        HTML.Tag kind = (HTML.Tag)o;
        if (kind == HTML.Tag.HR) {
          try {
            Field field = view.getClass().getDeclaredField("size");
            field.setAccessible(true);
            field.set(view, JBUIScale.scale(1));
            return view;
          }
          catch (Exception ignored) {
            //ignore
          }
        }
      }
      return view;
    }).build();
    String editorFontName = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName();
    if (editorFontName != null) {
      String style = "font-family:\"" + StringUtil.escapeQuotes(editorFontName) + "\";font-size:95%;";
      kit.getStyleSheet().addRule("pre {" + style + "}");
      text = text.replace("<code>", "<code style='" + style + "'>");
    }
    pane.setEditorKit(kit);
    pane.setText(text);

    pane.setCaretPosition(0);
    pane.setEditable(false);

    if (hintHint.isOwnBorderAllowed()) {
      setBorder(pane);
      setColors(pane);
    }
    else {
      pane.setBorder(null);
    }

    if (!hintHint.isAwtTooltip()) {
      prefSizeWasComputed[0] = true;
    }

    final boolean opaque = hintHint.isOpaqueAllowed();
    pane.setOpaque(opaque);
    pane.setBackground(hintHint.getTextBackground());

    if (!limitWidthToScreen) AppUIUtil.targetToDevice(pane, layeredPane);

    return pane;
  }

  public static void setColors(JComponent pane) {
    pane.setForeground(JBColor.foreground());
    pane.setBackground(HintUtil.getInformationColor());
    pane.setOpaque(true);
  }

  public static void setBorder(JComponent pane) {
    pane.setBorder(
      BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black), JBUI.Borders.empty(0, 5)));
  }

  public boolean isQueuedToShow(IdeTooltip tooltip) {
    return Comparing.equal(myQueuedTooltip, tooltip);
  }

  private static boolean isClickProcessor(Balloon balloon) {
    return balloon instanceof BalloonImpl && ((BalloonImpl)balloon).isClickProcessor();
  }

  private static boolean isAnimationEnabled(Balloon balloon) {
    return balloon instanceof BalloonImpl && ((BalloonImpl)balloon).isAnimationEnabled();
  }

  private static boolean isBlockClicks(Balloon balloon) {
    return balloon instanceof BalloonImpl && ((BalloonImpl)balloon).isBlockClicks();
  }

  private static boolean isMovingForward(Balloon balloon, @NotNull RelativePoint target) {
    return balloon instanceof BalloonImpl && ((BalloonImpl)balloon).isMovingForward(target);
  }

  private static boolean isInside(Balloon balloon, RelativePoint target) {
    return balloon instanceof IdeTooltip.Ui && ((IdeTooltip.Ui)balloon).isInside(target);
  }
}
