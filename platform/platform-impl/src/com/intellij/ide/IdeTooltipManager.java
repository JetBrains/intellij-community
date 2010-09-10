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
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

public class IdeTooltipManager implements ApplicationComponent, AWTEventListener {

  private RegistryValue myIsEnabled;

  private Component myCurrentComponent;
  private Balloon myCurrentTip;
  private MouseEvent myCurrentEvent;

  private JBPopupFactory myPopupFactory;
  private JLabel myTipLabel;

  private boolean myShowDelay = true;

  private Alarm myShowAlarm = new Alarm();
  private Alarm myHideAlarm = new Alarm();

  private int myX;
  private int myY;
  private RegistryValue myMode;

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
      if (me.getComponent() != myCurrentComponent) {
        hideCurrent();
      }
      maybeShowFor(c, me);

    } else if (me.getID() == MouseEvent.MOUSE_EXITED) {
      if (me.getComponent() == myCurrentComponent) {
        hideCurrent();
      }
    } else if (me.getID() == MouseEvent.MOUSE_MOVED) {
      if (me.getComponent() == myCurrentComponent) {
        if (myCurrentTip != null && myCurrentTip.wasFadedIn()) {
          hideCurrent();
        } else {
          myX = me.getX();
          myY = me.getY();
        }
      } else if (myCurrentComponent == null) {
        maybeShowFor(c, me);
      }
    } else if (me.getID() == MouseEvent.MOUSE_PRESSED) {
      if (me.getComponent() == myCurrentComponent) {
        hideCurrent();
      }
    }
  }

  private void maybeShowFor(Component c, MouseEvent me) {
    if (!(c instanceof JComponent)) return;

    JComponent comp = (JComponent)c;

    String tooltipText = comp.getToolTipText(me);
    boolean toCenter = Boolean.TRUE.equals(comp.getClientProperty(UIUtil.CENTER_TOOLTIP));

    if (tooltipText == null || tooltipText.trim().length() == 0) return;


    queueShow(comp, me, toCenter);
  }

  private void queueShow(final JComponent c, MouseEvent me, final boolean toCenter) {
    myShowAlarm.cancelAllRequests();
    hideCurrent();

    myCurrentComponent = c;
    myCurrentEvent = me;
    myX = me.getX();
    myY = me.getY();
    myShowAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        show(c, toCenter);
      }
    }, myShowDelay ? 1500 : 900);
  }

  private void show(JComponent c, boolean toCenter) {
    if (myCurrentComponent != c || !c.isShowing()) return;

    String text = c.getToolTipText(myCurrentEvent);

    if (text == null || text.trim().length() == 0) return;

    myTipLabel.setText(text);

    boolean useSystem;

    if ("default".equalsIgnoreCase(myMode.asString())) {
      useSystem = false;
    } else if ("system".equalsIgnoreCase(myMode.asString())) {
      useSystem = true;
    } else if ("graphite".equalsIgnoreCase(myMode.asString())) {
      useSystem = false;
    } else {
      useSystem = false;
    }

    Color bg = useSystem ? UIManager.getColor("ToolTip.background") : new Color(100, 100, 100, 230);
    Color fg = useSystem ? UIManager.getColor("ToolTip.foreground") : Color.white;
    Color border = useSystem ? Color.darkGray : bg.darker();

    BalloonBuilder builder = myPopupFactory.createBalloonBuilder(myTipLabel)
      .setPreferredPosition(Balloon.Position.above)
      .setFillColor(bg)
      .setBorderColor(border)
      .setAnimationCycle(150)
      .setShowCallout(true)
      .setCalloutShift(4);
    myTipLabel.setForeground(fg);
    myTipLabel.setBorder(new EmptyBorder(1, 3, 2, 3));
    myTipLabel.setFont(UIManager.getFont("ToolTip.font"));
    myCurrentTip = builder.createBalloon();

    boolean toCenterX;
    boolean toCenterY;

    if (!toCenter) {
      Dimension size = c.getSize();
      toCenterX = size.width < 64;
      toCenterY = size.height < 64;
      toCenter = toCenterX || toCenterY;
    } else {
      toCenterX = true;
      toCenterY = true;
    }

    Point point = new Point(myX, myY);
    if (toCenter) {
      Rectangle bounds = c.getBounds();
      point.x = toCenterX ? bounds.width / 2 : point.x;
      point.y = toCenterY ? (bounds.height / 2) : point.y;
    }

    myCurrentTip.show(new RelativePoint(c, point), Balloon.Position.above);
  }

  private void hideCurrent() {
    if (myCurrentTip != null) {
      myCurrentTip.hide();
      myShowDelay = false;
      myHideAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          myShowDelay = true;
        }
      }, 1000);
    }
    myCurrentTip = null;
    myCurrentComponent = null;
    myCurrentEvent = null;
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
}
