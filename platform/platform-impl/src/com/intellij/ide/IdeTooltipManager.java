// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.Alarm;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

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

public class IdeTooltipManager implements Disposable, AWTEventListener, ApplicationComponent {
  public static final String IDE_TOOLTIP_PLACE = "IdeTooltip";
  public static final ColorKey TOOLTIP_COLOR_KEY = ColorKey.createColorKey("TOOLTIP", (Color)null);

  private static final Key<IdeTooltip> CUSTOM_TOOLTIP = Key.create("custom.tooltip");
  private static final MouseEventAdapter<Void> DUMMY_LISTENER = new MouseEventAdapter<>(null);

  public static final Color GRAPHITE_COLOR = new Color(100, 100, 100, 230);
  private RegistryValue myIsEnabled;

  private Component myCurrentComponent;
  private Component myQueuedComponent;

  private BalloonImpl myCurrentTipUi;
  private MouseEvent  myCurrentEvent;
  private boolean     myCurrentTipIsCentered;

  private Disposable myLastDisposable;

  private Runnable myHideRunnable;

  private final JBPopupFactory myPopupFactory;

  private boolean myShowDelay = true;

  private final Alarm myAlarm = new Alarm();

  private int myX;
  private int myY;

  private IdeTooltip myCurrentTooltip;
  private Runnable   myShowRequest;
  private IdeTooltip myQueuedTooltip;

  public IdeTooltipManager(JBPopupFactory popupFactory) {
    myPopupFactory = popupFactory;
  }

  @Override
  public void initComponent() {
    myIsEnabled = Registry.get("ide.tooltip.callout");
    myIsEnabled.addListener(new RegistryValueListener.Adapter() {
      @Override
      public void afterValueChanged(RegistryValue value) {
        processEnabled();
      }
    }, ApplicationManager.getApplication());

    Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

    ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        hideCurrent(null, action, event);
      }
    }, ApplicationManager.getApplication());

    processEnabled();
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (!myIsEnabled.asBoolean()) return;

    MouseEvent me = (MouseEvent)event;
    Component c = me.getComponent();
    if (me.getID() == MouseEvent.MOUSE_ENTERED) {
      boolean canShow = true;
      if (c != myCurrentComponent) {
        canShow = hideCurrent(me, null, null);
      }
      if (canShow) {
        maybeShowFor(c, me);
      }
    }
    else if (me.getID() == MouseEvent.MOUSE_EXITED) {
      //We hide tooltip (but not hint!) when it's shown over myComponent and mouse exits this component
      if (c == myCurrentComponent && myCurrentTooltip != null && !myCurrentTooltip.isHint() && myCurrentTipUi != null) {
        myCurrentTipUi.setAnimationEnabled(false);
        hideCurrent(null, null, null, null, false);
      }
      else if (c == myCurrentComponent || c == myQueuedComponent) {
        hideCurrent(me, null, null);
      }
    }
    else if (me.getID() == MouseEvent.MOUSE_MOVED) {
      if (c == myCurrentComponent || c == myQueuedComponent) {
        if (myCurrentTipUi != null && myCurrentTipUi.wasFadedIn()) {
          maybeShowFor(c, me);
        }
        else {
          if (!myCurrentTipIsCentered) {
            myX = me.getX();
            myY = me.getY();
            if (c instanceof JComponent && !isTooltipDefined((JComponent)c, me) && (myQueuedTooltip == null || !myQueuedTooltip.isHint())) {
              hideCurrent(me, null, null);//There is no tooltip or hint here, let's proceed it as MOUSE_EXITED
            }
            else {
              maybeShowFor(c, me);
            }
          }
        }
      }
      else if (myCurrentComponent == null && myQueuedComponent == null) {
        maybeShowFor(c, me);
      }
    }
    else if (me.getID() == MouseEvent.MOUSE_PRESSED) {
      boolean clickOnTooltip = myCurrentTipUi != null && myCurrentTipUi == JBPopupFactory.getInstance().getParentBalloonFor(c);
      if (c == myCurrentComponent || (clickOnTooltip && !myCurrentTipUi.isClickProcessor())) {
        hideCurrent(me, null, null, null, !clickOnTooltip);
      }
    }
    else if (me.getID() == MouseEvent.MOUSE_DRAGGED) {
      hideCurrent(me, null, null);
    }
  }

  private void maybeShowFor(Component c, MouseEvent me) {
    if (!(c instanceof JComponent)) return;

    JComponent comp = (JComponent)c;
    Window wnd = SwingUtilities.getWindowAncestor(comp);
    if (wnd == null) return;

    if (!wnd.isActive()) {
      if (JBPopupFactory.getInstance().isChildPopupFocused(wnd)) return;
    }

    if (!isTooltipDefined(comp, me)) return;

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

    queueShow(comp, me, centerStrict || centerDefault, shift, -shift, -shift);
  }

  private boolean isTooltipDefined(JComponent comp, MouseEvent me) {
    return !StringUtil.isEmpty(comp.getToolTipText(me)) || getCustomTooltip(comp) != null;
  }

  private void queueShow(final JComponent c, final MouseEvent me, final boolean toCenter, int shift, int posChangeX, int posChangeY) {
    IdeTooltip tooltip = getCustomTooltip(c);
    if (tooltip == null) {
      String aText = String.valueOf(c.getToolTipText(me));
      tooltip = new IdeTooltip(c, me.getPoint(), null, /*new Object()*/c, aText) {
        @Override
        protected boolean beforeShow() {
          myCurrentEvent = me;

          if (!c.isShowing()) return false;

          String text = c.getToolTipText(myCurrentEvent);
          if (text == null || text.trim().isEmpty()) return false;

          JLayeredPane layeredPane = UIUtil.getParentOfType(JLayeredPane.class, c);

          final JEditorPane pane = initPane(text, new HintHint(me).setAwtTooltip(true), layeredPane);
          final Wrapper wrapper = new Wrapper(pane);
          setTipComponent(wrapper);
          return true;
        }
      }.setToCenter(toCenter).setCalloutShift(shift).setPositionChangeShift(posChangeX, posChangeY).setLayer(Balloon.Layer.top);
    }

    show(tooltip, false);
  }

  public void setCustomTooltip(JComponent component, IdeTooltip tooltip) {
    UIUtil.putClientProperty(component, CUSTOM_TOOLTIP, tooltip);
    // We need to register a dummy mouse listener to make sure events will be generated for this specific component, not its parent
    component.removeMouseListener(DUMMY_LISTENER);
    component.removeMouseMotionListener(DUMMY_LISTENER);
    if (tooltip != null) {
      component.addMouseListener(DUMMY_LISTENER);
      component.addMouseMotionListener(DUMMY_LISTENER);
    }
  }

  public IdeTooltip getCustomTooltip(JComponent component) {
    return UIUtil.getClientProperty(component, CUSTOM_TOOLTIP);
  }

  public IdeTooltip show(final IdeTooltip tooltip, boolean now) {
    return show(tooltip, now, true);
  }

  public IdeTooltip show(final IdeTooltip tooltip, boolean now, final boolean animationEnabled) {
    myAlarm.cancelAllRequests();

    hideCurrent(null, tooltip, null, null);

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
        show(tooltip, null, animationEnabled);
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

  private void show(final IdeTooltip tooltip, @Nullable Runnable beforeShow, boolean animationEnabled) {
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
    Color border = tooltip.getBorderColor() != null ? tooltip.getBorderColor() : getBorderColor(true);

    BalloonBuilder builder = myPopupFactory.createBalloonBuilder(tooltip.getTipComponent())
      .setFillColor(bg)
      .setBorderColor(border)
      .setBorderInsets(tooltip.getBorderInsets())
      .setAnimationCycle(animationEnabled ? Registry.intValue("ide.tooltip.animationCycle") : 0)
      .setShowCallout(true)
      .setCalloutShift(small && tooltip.getCalloutShift() == 0 ? 2 : tooltip.getCalloutShift())
      .setPositionChangeXShift(tooltip.getPositionChangeX())
      .setPositionChangeYShift(tooltip.getPositionChangeY())
      .setHideOnKeyOutside(!tooltip.isExplicitClose())
      .setHideOnAction(!tooltip.isExplicitClose())
      .setRequestFocus(tooltip.isRequestFocus())
      .setLayer(tooltip.getLayer());
    tooltip.getTipComponent().setForeground(fg);
    tooltip.getTipComponent().setBorder(JBUI.Borders.empty(1, 3, 2, 3));
    tooltip.getTipComponent().setFont(tooltip.getFont() != null ? tooltip.getFont() : getTextFont(true));


    if (beforeShow != null) {
      beforeShow.run();
    }

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

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
  public Color getTextForeground(boolean awtTooltip) {
    return UIUtil.getToolTipForeground();
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
  public Color getLinkForeground(boolean awtTooltip) {
    return JBColor.blue;
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
  public Color getTextBackground(boolean awtTooltip) {
    Color color = EditorColorsUtil.getGlobalOrDefaultColor(TOOLTIP_COLOR_KEY);
    return color != null ? color : UIUtil.getToolTipBackground();
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
  public String getUlImg(boolean awtTooltip) {
    AllIcons.General.Mdot.getIconWidth();  // keep icon reference
    return UIUtil.isUnderDarcula() ? "/general/mdot-white.png" : "/general/mdot.png";
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
  public Color getBorderColor(boolean awtTooltip) {
    return new JBColor(Gray._160, new Color(91, 93, 95));
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
  public boolean isOwnBorderAllowed(boolean awtTooltip) {
    return !awtTooltip;
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
  public boolean isOpaqueAllowed(boolean awtTooltip) {
    return !awtTooltip;
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
  public Font getTextFont(boolean awtTooltip) {
    return UIManager.getFont("ToolTip.font");
  }

  public boolean hasCurrent() {
    return myCurrentTooltip != null;
  }

  public boolean hideCurrent(@Nullable MouseEvent me) {
    return hideCurrent(me, null, null, null);
  }

  private boolean hideCurrent(@Nullable MouseEvent me, @Nullable AnAction action, @Nullable AnActionEvent event) {
    return hideCurrent(me, null, action, event, myCurrentTipUi != null && myCurrentTipUi.isAnimationEnabled());
  }

  private boolean hideCurrent(@Nullable MouseEvent me,
                              @Nullable IdeTooltip tooltipToShow,
                              @Nullable AnAction action,
                              @Nullable AnActionEvent event) {
    return hideCurrent(me, tooltipToShow, action, event, myCurrentTipUi != null && myCurrentTipUi.isAnimationEnabled());
  }

  private boolean hideCurrent(@Nullable MouseEvent me, @Nullable IdeTooltip tooltipToShow, @Nullable AnAction action, @Nullable AnActionEvent event, final boolean animationEnabled) {
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
      myAlarm.addRequest(myHideRunnable, Registry.intValue("ide.tooltip.autoDismissDeadZone"));
    }
    else {
      myHideRunnable.run();
      myHideRunnable = null;
    }

    return true;
  }

  public void hideCurrentNow(boolean animationEnabled) {
    if (myCurrentTipUi != null) {
      myCurrentTipUi.setAnimationEnabled(animationEnabled);
      myCurrentTipUi.hide();
      myCurrentTooltip.onHidden();
      myShowDelay = false;
      myAlarm.addRequest(() -> myShowDelay = true, Registry.intValue("ide.tooltip.reshowDelay"));
    }

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
    }
    else {
      ToolTipManager.sharedInstance().setEnabled(true);
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
    return ApplicationManager.getApplication().getComponent(IdeTooltipManager.class);
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
    final Ref<Dimension> prefSize = new Ref<>(null);
    @NonNls String text = HintUtil.prepareHintText(html, hintHint);

    final boolean[] prefSizeWasComputed = {false};
    final JEditorPane pane = new JEditorPane() {
      @Override
      public Dimension getPreferredSize() {
        if (!isShowing() && layeredPane != null) {
          AppUIUtil.targetToDevice(this, layeredPane);
        }
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
            prefSize.set(new Dimension(fitWidth > minSize.width ? fitWidth : minSize.width, fixedWidthSize.height));
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
    };

    HTMLEditorKit kit = new UIUtil.JBHtmlEditorKit() {
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
                field.set(view, JBUI.scale(1));
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
