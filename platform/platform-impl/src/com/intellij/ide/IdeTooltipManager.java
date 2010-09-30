/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

public class IdeTooltipManager implements ApplicationComponent, AWTEventListener {

  private RegistryValue myIsEnabled;

  private Component myCurrentComponent;
  private Component myQueuedComponent;

  private BalloonImpl myCurrentTipUi;
  private MouseEvent myCurrentEvent;
  private boolean myCurrentTipIsCentered;

  private Runnable myHideRunnable;

  private JBPopupFactory myPopupFactory;
  private JLabel myTipLabel;

  private boolean myShowDelay = true;

  private Alarm myAlarm = new Alarm();

  private int myX;
  private int myY;
  private RegistryValue myMode;

  private IdeTooltip myCurrentTooltip;
  private Runnable myShowRequest;

  @NotNull
  @Override
  public String getComponentName() {
    return "IDE Tooltip Manager";
  }

  public IdeTooltipManager(JBPopupFactory popupFactory) {
    myPopupFactory = popupFactory;
  }

  @Override
  public void initComponent() {
    myMode = Registry.get("ide.tooltip.mode");

    myIsEnabled = Registry.get("ide.tooltip.callout");
    myIsEnabled.addListener(new RegistryValueListener.Adapter() {
      @Override
      public void afterValueChanged(RegistryValue value) {
        processEnabled();
      }
    }, ApplicationManager.getApplication());

    Toolkit.getDefaultToolkit().addAWTEventListener(this, MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);
    myTipLabel = new JLabel();
    myTipLabel.setOpaque(false);

    processEnabled();
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (!myIsEnabled.asBoolean()) return;

    MouseEvent me = (MouseEvent)event;
    Component c = me.getComponent();
    if (me.getID() == MouseEvent.MOUSE_ENTERED) {
      boolean canShow = true;
      if (me.getComponent() != myCurrentComponent) {
        canShow = hideCurrent(me);
      }
      if (canShow) {
        maybeShowFor(c, me);
      }
    } else if (me.getID() == MouseEvent.MOUSE_EXITED) {
      if (me.getComponent() == myCurrentComponent) {
        hideCurrent(me);
      }
    } else if (me.getID() == MouseEvent.MOUSE_MOVED) {
      if (me.getComponent() == myCurrentComponent) {
        if (myCurrentTipUi != null && myCurrentTipUi.wasFadedIn()) {
          if (hideCurrent(me)) {
            maybeShowFor(c, me);
          }
        } else {
          if (!myCurrentTipIsCentered) {
            myX = me.getX();
            myY = me.getY();
            maybeShowFor(c, me);
          }
        }
      } else if (myCurrentComponent == null) {
        //maybeShowFor(c, me);
      }
    } else if (me.getID() == MouseEvent.MOUSE_PRESSED) {
      if (me.getComponent() == myCurrentComponent) {
        hideCurrent(me);
      }
    }
  }

  private void maybeShowFor(Component c, MouseEvent me) {
    if (!(c instanceof JComponent)) return;

    JComponent comp = (JComponent)c;

    String tooltipText = comp.getToolTipText(me);
    if (tooltipText == null || tooltipText.trim().length() == 0) return;

    queueShow(comp, me, Boolean.TRUE.equals(comp.getClientProperty(UIUtil.CENTER_TOOLTIP)));
  }

  private void queueShow(final JComponent c, final MouseEvent me, final boolean toCenter) {
    final IdeTooltip tooltip = new IdeTooltip(c, me.getPoint(), myTipLabel) {
      @Override
      protected boolean beforeShow() {
        myCurrentEvent = me;

        String text = c.getToolTipText(myCurrentEvent);
        if (text == null || text.trim().length() == 0) return false;
        myTipLabel.setText(text);
        return true;
      }
    }.setToCenter(toCenter);

    show(tooltip, false);
  }

  public IdeTooltip show(final IdeTooltip tooltip, boolean now) {
    myAlarm.cancelAllRequests();

    hideCurrent(null);

    myQueuedComponent = tooltip.getComponent();

    myShowRequest = new Runnable() {
      @Override
      public void run() {
        if (myShowRequest == null) return;

        if (myQueuedComponent != tooltip.getComponent() || !tooltip.getComponent().isShowing()) {
          hideCurrent(null);
          return;
        }

        if (tooltip.beforeShow()) {
          show(tooltip, null);
        }
        else {
          hideCurrent(null);
        }
      }
    };

    if (now) {
      myShowRequest.run();
    } else {
      myAlarm.addRequest(myShowRequest, myShowDelay ? tooltip.getShowDelay() : tooltip.getInitialReshowDelay());
    }

    return tooltip;
  }

  private void show(final IdeTooltip tooltip, Runnable beforeShow) {
    boolean toCenterX;
    boolean toCenterY;

    boolean toCenter = tooltip.isToCenter();
    if (!toCenter && tooltip.isToCenterIfSmall()) {
      Dimension size = tooltip.getComponent().getSize();
      toCenterX = size.width < 64;
      toCenterY = size.height < 64;
      toCenter = toCenterX || toCenterY;
    } else {
      toCenterX = true;
      toCenterY = true;
    }

    Point effectivePoint = tooltip.getPoint();
    if (toCenter) {
      Rectangle bounds = tooltip.getComponent().getBounds();
      effectivePoint.x = toCenterX ? bounds.width / 2 : effectivePoint.x;
      effectivePoint.y = toCenterY ? (bounds.height / 2) : effectivePoint.y;
    }


    if (myCurrentComponent == tooltip.getComponent() && effectivePoint.equals(new Point(myX, myY))) {
      return;
    }

    Color bg = getTextBackground(true);
    Color fg = getTextForeground(true);
    Color border = getBorderColor(true);

    BalloonBuilder builder = myPopupFactory.createBalloonBuilder(tooltip.getTipComponent())
      .setPreferredPosition(tooltip.getPreferredPosition())
      .setFillColor(bg)
      .setBorderColor(border)
      .setAnimationCycle(150)
      .setShowCallout(true)
      .setCalloutShift(4);
    tooltip.getTipComponent().setForeground(fg);
    tooltip.getTipComponent().setBorder(new EmptyBorder(1, 3, 2, 3));
    tooltip.getTipComponent().setFont(getTextFont(true));


    if (beforeShow != null) {
      beforeShow.run();
    }

    myCurrentTipUi = (BalloonImpl)builder.createBalloon();
    myCurrentComponent = tooltip.getComponent();
    myX = effectivePoint.x;
    myY = effectivePoint.y;
    myCurrentTipIsCentered = toCenter;
    myCurrentTooltip = tooltip;
    myShowRequest = null;

    myCurrentTipUi.show(new RelativePoint(tooltip.getComponent(), effectivePoint), tooltip.getPreferredPosition());
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myCurrentTooltip == tooltip && tooltip.canBeDismissedOnTimeout()) {
          hideCurrent(null);
        }
      }
    }, tooltip.getDismissDelay());
  }


  public Color getTextForeground(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? Color.white : UIManager.getColor("ToolTip.foreground");
  }

  public Color getLinkForeground(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? new Color(209, 209, 255) : Color.blue;
  }

  public Color getTextBackground(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? new Color(100, 100, 100, 230) : UIManager.getColor("ToolTip.background");
  }

  public String getUlImg(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? "/general/mdot-white.png" : "/general/mdot.png";
  }

  public Color getBorderColor(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? getTextBackground(awtTooltip).darker() : Color.darkGray;
  }

  public boolean isOwnBorderAllowed(boolean awtTooltip) {
    return !awtTooltip;
  }

  public boolean isOpaqueAllowed(boolean awtTooltip) {
    return !awtTooltip;
  }

  public Font getTextFont(boolean awtTooltip) {
    return UIManager.getFont("ToolTip.font");
  }

  private boolean isUseSystemLook() {
    boolean useSystem;

    if ("default".equalsIgnoreCase(myMode.asString())) {
      useSystem = !SystemInfo.isMac;
    } else if ("system".equalsIgnoreCase(myMode.asString())) {
      useSystem = true;
    } else if ("graphite".equalsIgnoreCase(myMode.asString())) {
      useSystem = false;
    } else {
      useSystem = false;
    }
    return useSystem;
  }

  private boolean hideCurrent(@Nullable MouseEvent me) {
    myShowRequest = null;
    if (myCurrentTooltip == null) return true;

    if (me != null && myCurrentTipUi != null) {
      if (!myCurrentTooltip.canAutohideOn(new TooltipEvent(me, myCurrentTipUi.isInsideBalloon(me)))) {
        if (myHideRunnable != null) {
          myHideRunnable = null;
        }
        return false;
      }
    }

    myHideRunnable = new Runnable() {
      @Override
      public void run() {
        if (myHideRunnable != null) {
          hideCurrentNow();
          myHideRunnable = null;
        }
      }
    };

    if (me != null) {
      myAlarm.addRequest(myHideRunnable, Registry.intValue("ide.tooltip.autoDismissDeadZone"));
    } else {
      myHideRunnable.run();
      myHideRunnable = null;
    }

    return true;
  }

  private void hideCurrentNow() {
    if (myCurrentTipUi != null) {
      myCurrentTipUi.hide();
      myCurrentTooltip.onHidden();
      myShowDelay = false;
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          myShowDelay = true;
        }
      }, Registry.intValue("ide.tooltip.reshowDelay"));
    }

    myShowRequest = null;
    myCurrentTooltip = null;
    myCurrentTipUi = null;
    myCurrentComponent = null;
    myQueuedComponent = null;
    myCurrentEvent = null;
    myCurrentTipIsCentered = false;
    myX = -1;
    myY = -1;
  }

  private void processEnabled() {
    if (myIsEnabled.asBoolean()) {
      ToolTipManager.sharedInstance().setEnabled(false);
    } else {
      ToolTipManager.sharedInstance().setEnabled(true);
    }
  }

  @Override
  public void disposeComponent() {
  }

  public static IdeTooltipManager getInstance() {
    return ApplicationManager.getApplication().getComponent(IdeTooltipManager.class);
  }

  private boolean useGraphite(boolean awtHint) {
    return !isUseSystemLook() && awtHint;
  }

  public void hide(IdeTooltip tooltip) {
    if (myCurrentTooltip == tooltip || tooltip == null) {
      hideCurrent(null);
    }
  }

  public void cancelAutoHide() {
    myHideRunnable = null;
  }

}
