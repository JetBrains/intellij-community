// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.application.options.RegistryManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.Application;
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
import com.intellij.openapi.util.Ref;
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
import javax.swing.text.*;
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
  private final RegistryValue myIsEnabled;
  private final RegistryValue myHelpTooltip;

  private HelpTooltipManager myHelpTooltipManager;
  private boolean myHideHelpTooltip;

  private volatile Component myCurrentComponent;
  private volatile Component myQueuedComponent;
  private volatile Component myProcessingComponent;

  private BalloonImpl myCurrentTipUi;
  private MouseEvent myCurrentEvent;
  private boolean myCurrentTipIsCentered;

  private Disposable myLastDisposable;

  private Runnable myHideRunnable;

  private boolean myShowDelay = true;

  private final Alarm myAlarm = new Alarm();

  private int myX;
  private int myY;

  private IdeTooltip myCurrentTooltip;
  private Runnable myShowRequest;
  private IdeTooltip myQueuedTooltip;

  public IdeTooltipManager() {
    RegistryManager registryManager = RegistryManager.getInstance();
    myIsEnabled = registryManager.get("ide.tooltip.callout");
    myHelpTooltip = registryManager.get("ide.helptooltip.enabled");

    RegistryValueListener listener = new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        processEnabled();
      }
    };
    Application app = ApplicationManager.getApplication();
    myIsEnabled.addListener(listener, app);
    myHelpTooltip.addListener(listener, app);

    Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

    app.getMessageBus().connect().subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
        hideCurrent(null, action, event);
      }
    });

    processEnabled();
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (!myIsEnabled.asBoolean()) {
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
            myCurrentTooltip != null &&
            !myCurrentTooltip.isHint() &&
            myCurrentTipUi != null) {
          myCurrentTipUi.setAnimationEnabled(false);
          hideCurrent(null, null, null, null, false);
        }
        else if (myProcessingComponent == myCurrentComponent || myProcessingComponent == myQueuedComponent) {
          hideCurrent(me, null, null);
        }
      }
      else if (me.getID() == MouseEvent.MOUSE_MOVED) {
        if (myProcessingComponent == myCurrentComponent || myProcessingComponent == myQueuedComponent) {
          if (myCurrentTipUi != null && myCurrentTipUi.wasFadedIn()) {
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
        boolean clickOnTooltip = myCurrentTipUi != null &&
                                 myCurrentTipUi == JBPopupFactory.getInstance().getParentBalloonFor(myProcessingComponent);
        if (myProcessingComponent == myCurrentComponent || (clickOnTooltip && !myCurrentTipUi.isClickProcessor())) {
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
      int row = ((JList)c).locationToIndex(me.getPoint());
      if (row > -1) {
        rowBounds = ((JList)c).getCellBounds(row, row);
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
      if (myHelpTooltipManager != null) {
        myCurrentComponent = c;
        myHideHelpTooltip = true;
        myHelpTooltipManager.showTooltip(c, me);
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
    } else if (myCurrentTooltip == tooltip) {
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
     if (!hasCurrent() || myCurrentComponent == null || myCurrentComponent != tooltipOwner)
       return;

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
    ComponentUtil.putClientProperty(component, CUSTOM_TOOLTIP, tooltip);
    // We need to register a dummy mouse listener to make sure events will be generated for this specific component, not its parent
    component.removeMouseListener(DUMMY_LISTENER);
    component.removeMouseMotionListener(DUMMY_LISTENER);
    if (tooltip != null) {
      component.addMouseListener(DUMMY_LISTENER);
      component.addMouseMotionListener(DUMMY_LISTENER);
    }
  }

  public IdeTooltip getCustomTooltip(JComponent component) {
    return ComponentUtil.getClientProperty(component, CUSTOM_TOOLTIP);
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

    if (myCurrentComponent == tooltip.getComponent() && myCurrentTipUi != null && !myCurrentTipUi.isDisposed()) {
      myCurrentTipUi.show(new RelativePoint(tooltip.getComponent(), effectivePoint), tooltip.getPreferredPosition());
      return;
    }

    if (myCurrentComponent == tooltip.getComponent() && effectivePoint.equals(new Point(myX, myY))) {
      return;
    }

    Color bg = tooltip.getTextBackground() != null ? tooltip.getTextBackground() : getTextBackground(true);
    Color fg = tooltip.getTextForeground() != null ? tooltip.getTextForeground() : getTextForeground(true);
    Color borderColor = tooltip.getBorderColor() != null ? tooltip.getBorderColor() : getBorderColor(true);

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


    myCurrentTipUi = (BalloonImpl)builder.createBalloon();
    myCurrentTipUi.setAnimationEnabled(animationEnabled);
    tooltip.setUi(myCurrentTipUi);
    myCurrentComponent = tooltip.getComponent();
    myX = effectivePoint.x;
    myY = effectivePoint.y;
    myCurrentTipIsCentered = toCenter;
    myCurrentTooltip = tooltip;
    myShowRequest = null;
    myQueuedComponent = null;
    myQueuedTooltip = null;

    myLastDisposable = myCurrentTipUi;
    Disposer.register(myLastDisposable, new Disposable() {
      @Override
      public void dispose() {
        myLastDisposable = null;
      }
    });

    myCurrentTipUi.show(new RelativePoint(tooltip.getComponent(), effectivePoint), tooltip.getPreferredPosition());
    myAlarm.addRequest(() -> {
      if (myCurrentTooltip == tooltip && tooltip.canBeDismissedOnTimeout()) {
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
    return JBUI.CurrentTheme.Link.linkColor();
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

  @SuppressWarnings("UnusedParameters")
  public Color getBorderColor(boolean awtTooltip) {
    return new JBColor(Gray._160, new Color(91, 93, 95));
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
    return myCurrentTooltip != null;
  }

  public boolean hideCurrent(@Nullable MouseEvent me) {
    return hideCurrent(me, null);
  }

  private boolean hideCurrent(@Nullable MouseEvent me, @Nullable AnAction action, @Nullable AnActionEvent event) {
    return hideCurrent(me, null, action, event, myCurrentTipUi != null && myCurrentTipUi.isAnimationEnabled());
  }

  private boolean hideCurrent(@Nullable MouseEvent me,
                              @Nullable IdeTooltip tooltipToShow) {
    return hideCurrent(me, tooltipToShow, null, null, myCurrentTipUi != null && myCurrentTipUi.isAnimationEnabled());
  }

  private boolean hideCurrent(@Nullable MouseEvent me, @Nullable IdeTooltip tooltipToShow, @Nullable AnAction action, @Nullable AnActionEvent event, final boolean animationEnabled) {
    if (myHelpTooltipManager != null && myHideHelpTooltip) {
      hideCurrentNow(false);
      return true;
    }

    if (myCurrentTooltip != null && me != null && myCurrentTooltip.isInside(new RelativePoint(me))) {
      if (me.getButton() == MouseEvent.NOBUTTON || myCurrentTipUi == null || myCurrentTipUi.isBlockClicks()) {
        return false;
      }
    }

    myShowRequest = null;
    myQueuedComponent = null;
    myQueuedTooltip = null;

    if (myCurrentTooltip == null) return true;

    if (myCurrentTipUi != null) {
      RelativePoint target = me != null ? new RelativePoint(me) : null;
      boolean isInsideOrMovingForward = target != null && (myCurrentTipUi.isInside(target) || myCurrentTipUi.isMovingForward(target));
      boolean canAutoHide = myCurrentTooltip.canAutohideOn(new TooltipEvent(me, isInsideOrMovingForward, action, event));
      boolean implicitMouseMove = me != null &&
                                  (me.getID() == MouseEvent.MOUSE_MOVED ||
                                   me.getID() == MouseEvent.MOUSE_EXITED ||
                                   me.getID() == MouseEvent.MOUSE_ENTERED);
      if (!canAutoHide
          || (isInsideOrMovingForward && implicitMouseMove)
          || (myCurrentTooltip.isExplicitClose() && implicitMouseMove)
          || (tooltipToShow != null && !tooltipToShow.isHint() && Comparing.equal(myCurrentTooltip, tooltipToShow))) {
        if (myHideRunnable != null) {
          myHideRunnable = null;
        }
        return false;
      }
    }

    myHideRunnable = () -> {
      if (myHideRunnable != null) {
        hideCurrentNow(animationEnabled);
        myHideRunnable = null;
      }
    };

    if (me != null && me.getButton() == MouseEvent.NOBUTTON) {
      myAlarm.addRequest(myHideRunnable, RegistryManager.getInstance().intValue("ide.tooltip.autoDismissDeadZone"));
    }
    else {
      myHideRunnable.run();
      myHideRunnable = null;
    }

    return true;
  }

  public void hideCurrentNow(boolean animationEnabled) {
    if (myHelpTooltipManager != null) {
      myHelpTooltipManager.hideTooltip();
    }

    if (myCurrentTipUi != null) {
      myCurrentTipUi.setAnimationEnabled(animationEnabled);
      myCurrentTipUi.hide();
      myCurrentTooltip.onHidden();
      myShowDelay = false;
      myAlarm.addRequest(() -> myShowDelay = true, RegistryManager.getInstance().intValue("ide.tooltip.reshowDelay"));
    }

    myHideHelpTooltip = false;
    myShowRequest = null;
    myCurrentTooltip = null;
    myCurrentTipUi = null;
    myCurrentComponent = null;
    myQueuedComponent = null;
    myQueuedTooltip = null;
    myCurrentEvent = null;
    myCurrentTipIsCentered = false;
    myX = -1;
    myY = -1;
  }

  private void processEnabled() {
    if (myIsEnabled.asBoolean()) {
      ToolTipManager.sharedInstance().setEnabled(false);
      if (myHelpTooltip.asBoolean()) {
        if (myHelpTooltipManager == null) {
          myHelpTooltipManager = new HelpTooltipManager();
        }
        return;
      }
    }
    else {
      ToolTipManager.sharedInstance().setEnabled(true);
    }
    if (myHelpTooltipManager != null) {
      myHelpTooltipManager.hideTooltip();
      myHelpTooltipManager = null;
    }
  }

  @Override
  public void dispose() {
    hideCurrentNow(false);
    if (myLastDisposable != null) {
      Disposer.dispose(myLastDisposable);
    }
  }

  public static IdeTooltipManager getInstance() {
    return ApplicationManager.getApplication().getService(IdeTooltipManager.class);
  }

  public void hide(@Nullable IdeTooltip tooltip) {
    if (myCurrentTooltip == tooltip || tooltip == null || tooltip == myQueuedTooltip) {
      hideCurrent(null, null, null);
    }
  }

  public void cancelAutoHide() {
    myHideRunnable = null;
  }


  public static JEditorPane initPane(@NonNls String text, final HintHint hintHint, @Nullable final JLayeredPane layeredPane) {
    return initPane(new Html(text), hintHint, layeredPane);
  }

  public static JEditorPane initPane(@NonNls Html html, final HintHint hintHint, @Nullable final JLayeredPane layeredPane) {
    return initPane(html, hintHint, layeredPane, true);
  }

  public static JEditorPane initPane(@NonNls Html html, final HintHint hintHint, @Nullable final JLayeredPane layeredPane,
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

    HTMLEditorKit kit = new JBHtmlEditorKit() {
      final HTMLFactory factory = new HTMLFactory() {
        @Override
        public View create(Element elem) {
          AttributeSet attrs = elem.getAttributes();
          Object elementName = attrs.getAttribute(AbstractDocument.ElementNameAttribute);
          Object o = elementName != null ? null : attrs.getAttribute(StyleConstants.NameAttribute);
          if (o instanceof HTML.Tag) {
            HTML.Tag kind = (HTML.Tag)o;
            if (kind == HTML.Tag.HR) {
              View view = super.create(elem);
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
          return super.create(elem);
        }
      };

      @Override
      public ViewFactory getViewFactory() {
        return factory;
      }
    };
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
}
