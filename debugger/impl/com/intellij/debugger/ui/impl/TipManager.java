package com.intellij.debugger.ui.impl;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.WeakMouseListener;
import com.intellij.debugger.ui.WeakMouseMotionListener;
import com.intellij.ui.ListenerUtil;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jan 29, 2004
 * Time: 4:23:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class TipManager {
  private volatile boolean myIsDisposed = false;
  
  public static interface TipFactory {
    JComponent createToolTip (MouseEvent e);
  }

  private class MyMouseListener extends MouseAdapter {
    public void mouseEntered(MouseEvent e) {
      tryTooltip(e);
    }

    private boolean isOverTip(MouseEvent e) {
      if (myCurrentTooltip != null) {
        if(!myCurrentTooltip.isShowing()) {
          hideTooltip();
          return false;
        }
        final Component eventOriginator = e.getComponent();
        if (eventOriginator == null) {
          return false;
        }
        final Point point = e.getPoint();
        SwingUtilities.convertPointToScreen(point, eventOriginator);

        final Rectangle bounds = myCurrentTooltip.getBounds();
        final Point tooltipLocationOnScreen = myCurrentTooltip.getLocationOnScreen();
        bounds.setLocation(tooltipLocationOnScreen.x, tooltipLocationOnScreen.y);

        return bounds.contains(point);
      }
      return false;
    }

    public void mouseExited(MouseEvent e) {
      myAlarm.cancelAllRequests();
      if (isOverTip(e)) {
        final JComponent currentTooltipComponent = myCurrentTooltip;
        ListenerUtil.addMouseListener(currentTooltipComponent, new MouseAdapter() {
          public void mouseExited(MouseEvent e) {
            if(!isOverTip(e)) {
              ListenerUtil.removeMouseListener(currentTooltipComponent, this);
              if (myCurrentTooltip != null) {
                hideTooltip();
              }
            }
          }
        });
      }
      else {
        hideTooltip();
      }
    }
  }

  private class MyMouseMotionListener extends MouseMotionAdapter {
    public void mouseMoved(MouseEvent e) {
      tryTooltip(e);
    }
  }

  private void tryTooltip(final MouseEvent e) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        if (!myIsDisposed) {
          showTooltip(e);
        }
      }
    }, DebuggerSettings.getInstance().VALUE_LOOKUP_DELAY);
  }

  private void showTooltip(MouseEvent e) {
    JComponent newTip = myTipFactory.createToolTip(e);
    if(newTip == myCurrentTooltip) {
      return;
    }

    hideTooltip();

    if(newTip != null && myComponent.isShowing()) {
      PopupFactory popupFactory = PopupFactory.getSharedInstance();
      final Point location = e.getPoint();
      final Component sourceComponent = e.getComponent();
      if (sourceComponent != null) {
        SwingUtilities.convertPointToScreen(location, sourceComponent);
      }
      myTipPopup = popupFactory.getPopup(myComponent, newTip, location.x, location.y);
      myTipPopup.show();
      myCurrentTooltip = newTip;
    }
  }

  public void hideTooltip() {
    if (myTipPopup != null) {
      myTipPopup.hide();
      myTipPopup = null;
    }
    myCurrentTooltip = null;
  }

  private JComponent myCurrentTooltip;
  private Popup myTipPopup;
  private final TipFactory myTipFactory;
  private final JComponent myComponent;
  private MouseListener myMouseListener = new MyMouseListener();
  private MouseMotionListener myMouseMotionListener = new MyMouseMotionListener();
  private final Alarm myAlarm = new Alarm();


  public TipManager(JComponent component, TipFactory factory) {
    new WeakMouseListener(component, myMouseListener);
    new WeakMouseMotionListener(component, myMouseMotionListener);

    myTipFactory = factory;
    myComponent = component;

  }

  public void dispose() {
    myIsDisposed = true;
    myAlarm.cancelAllRequests();
    myMouseListener = null;
    myMouseMotionListener = null;
  }

}
