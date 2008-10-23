package com.intellij.debugger.ui.impl;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;

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
public class TipManager implements Disposable {
  private volatile boolean myIsDisposed = false;

  public static interface TipFactory {
    JComponent createToolTip (MouseEvent e);
  }


  private boolean isOverTip(MouseEvent e) {
    if (myCurrentTooltip != null) {
      if(!myCurrentTooltip.isShowing()) {
        hideTooltip(true);
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

  boolean myInsideComponent;

  private class MyMouseListener extends MouseAdapter {
    @Override
    public void mouseExited(final MouseEvent e) {
      myInsideComponent = false;
    }

    @Override
    public void mousePressed(final MouseEvent e) {
      if (myInsideComponent) {
        hideTooltip(true);
      }
    }

    @Override
    public void mouseEntered(final MouseEvent e) {
      myInsideComponent = true;
    }
  }


  private class MyMouseMotionListener extends MouseMotionAdapter {
    @Override
    public void mouseMoved(final MouseEvent e) {
      if (!myComponent.isShowing()) return;

      myInsideComponent = true;

      if (myCurrentTooltip == null) {
        final Rectangle compBounds = myComponent.getBounds();
        final Point compPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myComponent);
        if (compBounds.contains(compPoint)) {
          tryTooltip(e);
        }
      } else {
        if (!isOverTip(e)) {
          tryTooltip(e);
        }
      }
    }
  }

  private void tryTooltip(final MouseEvent e) {
    myShowAlarm.cancelAllRequests();
    myHideAlarm.cancelAllRequests();
    myShowAlarm.addRequest(new Runnable() {
      public void run() {
        if (!myIsDisposed) {
          showTooltip(e);
        }
      }
    }, DebuggerSettings.getInstance().VALUE_LOOKUP_DELAY);
  }

  private void showTooltip(MouseEvent e) {
    final MouseEvent me = SwingUtilities.convertMouseEvent(e.getComponent(), e, myComponent);

    JComponent newTip = myTipFactory.createToolTip(me);

    if (newTip == null) {
      hideTooltip(false);
      return;
    }

    if(newTip == myCurrentTooltip) {
      return;
    }

    hideTooltip(true);

    if(newTip != null && myComponent.isShowing()) {
      PopupFactory popupFactory = PopupFactory.getSharedInstance();
      final Point location = me.getPoint();
      final Component sourceComponent = me.getComponent();
      if (sourceComponent != null) {
        SwingUtilities.convertPointToScreen(location, sourceComponent);
      }
      myTipPopup = popupFactory.getPopup(myComponent, newTip, location.x, location.y);
      myInsideComponent = false;
      myTipPopup.show();
      myCurrentTooltip = newTip;
    }
  }

  public void hideTooltip() {
    hideTooltip(true);
  }
  
  public void hideTooltip(boolean now) {
    if (myTipPopup == null) return;

    if (now) {
      myHideAlarm.cancelAllRequests();
      myTipPopup.hide();
      myTipPopup = null;
      myCurrentTooltip = null;
    } else {
      myHideAlarm.addRequest(new Runnable() {
        public void run() {
          if (myInsideComponent) {
            hideTooltip(true);
          }
        }
      }, 250);
    }
  }

  private JComponent myCurrentTooltip;
  private Popup myTipPopup;
  private final TipFactory myTipFactory;
  private final JComponent myComponent;
  private MouseListener myMouseListener = new MyMouseListener();
  private MouseMotionListener myMouseMotionListener = new MyMouseMotionListener();

  private final Alarm myShowAlarm = new Alarm();
  private final Alarm myHideAlarm = new Alarm();


  private IdeGlassPane myGP;

  public TipManager(final JComponent component, TipFactory factory) {
    myTipFactory = factory;
    myComponent = component;

    new UiNotifyConnector.Once(component, new Activatable() {
      public void showNotify() {
        installListeners();
      }

      public void hideNotify() {
      }
    });
  }

  private void installListeners() {
    if (myIsDisposed) return;

    myGP = IdeGlassPaneUtil.find(myComponent);
    assert myGP != null;

    myGP.addMousePreprocessor(myMouseListener, this);
    myGP.addMouseMotionPreprocessor(myMouseMotionListener, this);
  }

  public void dispose() {
    Disposer.dispose(this);

    myIsDisposed = true;
    myShowAlarm.cancelAllRequests();
    myMouseListener = null;
    myMouseMotionListener = null;
  }

}
