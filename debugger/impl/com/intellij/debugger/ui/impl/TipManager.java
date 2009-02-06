package com.intellij.debugger.ui.impl;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jan 29, 2004
 * Time: 4:23:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class TipManager implements Disposable, PopupMenuListener {

  private volatile boolean myIsDisposed = false;
  private boolean myPopupShown;
  private HideCanceller myHideCanceller;

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

  public JPopupMenu registerPopup(JPopupMenu menu) {
    menu.addPopupMenuListener(this);
    return menu;
  }

  public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
    myPopupShown = true;
  }

  public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
    onPopupClosed(e);
  }

  public void popupMenuCanceled(final PopupMenuEvent e) {
    onPopupClosed(e);
  }

  private void onPopupClosed(final PopupMenuEvent e) {
    myPopupShown = false;
    if (e.getSource() instanceof JPopupMenu) {
      ((JPopupMenu)e.getSource()).removePopupMenuListener(this);
    }
  }

  private class MyMouseMotionListener extends MouseMotionAdapter {
    @Override
    public void mouseMoved(final MouseEvent e) {
      if (!myComponent.isShowing()) return;

      myInsideComponent = true;

      if (myCurrentTooltip == null) {
        if (isInsideComponent(e)) {
          tryTooltip(e);
        }
      } else {
        if (!isOverTip(e)) {
          tryTooltip(e);
        }
      }
    }

  }

  private boolean isInsideComponent(final MouseEvent e) {
    final Rectangle compBounds = myComponent.getBounds();
    final Point compPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myComponent);
    final boolean insideCoponent = compBounds.contains(compPoint);
    return insideCoponent;
  }


  private void tryTooltip(final MouseEvent e) {
    myShowAlarm.cancelAllRequests();
    myHideAlarm.cancelAllRequests();
    myShowAlarm.addRequest(new Runnable() {
      public void run() {
        if (!myIsDisposed && !myPopupShown) {
          showTooltip(e);
        }
      }
    }, DebuggerSettings.getInstance().VALUE_LOOKUP_DELAY);
  }

  private void showTooltip(MouseEvent e) {
    final MouseEvent me = SwingUtilities.convertMouseEvent(e.getComponent(), e, myComponent);

    final JComponent newTip = myTipFactory.createToolTip(me);

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
      }, 100);
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

    final HideTooltipAction hide = new HideTooltipAction();
    hide.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)), myComponent);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        hide.unregisterCustomShortcutSet(myComponent);
      }
    });
  }

  private class HideTooltipAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      hideTooltip(true);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myTipPopup != null);
    }
  }

  private void installListeners() {
    if (myIsDisposed) return;

    myGP = IdeGlassPaneUtil.find(myComponent);
    assert myGP != null;

    myGP.addMousePreprocessor(myMouseListener, this);
    myGP.addMouseMotionPreprocessor(myMouseMotionListener, this);

    myHideCanceller = new HideCanceller();
    Toolkit.getDefaultToolkit().addAWTEventListener(myHideCanceller, MouseEvent.MOUSE_MOTION_EVENT_MASK);
  }

  public void dispose() {
    Disposer.dispose(this);

    hideTooltip(true);

    Toolkit.getDefaultToolkit().removeAWTEventListener(myHideCanceller);

    myIsDisposed = true;
    myShowAlarm.cancelAllRequests();
    myMouseListener = null;
    myMouseMotionListener = null;
  }

  private class HideCanceller implements AWTEventListener {

    public void eventDispatched(AWTEvent event) {
      if (myCurrentTooltip == null) return;

      if (event.getID() == MouseEvent.MOUSE_MOVED) {
        final MouseEvent me = (MouseEvent)event;
        if (myCurrentTooltip == me.getComponent() || SwingUtilities.isDescendingFrom(me.getComponent(), myCurrentTooltip)) {
          myHideAlarm.cancelAllRequests();
        }
      }
    }
  }
}
