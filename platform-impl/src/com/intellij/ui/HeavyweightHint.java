package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.util.EventListener;
import java.util.EventObject;

public class HeavyweightHint implements Hint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.LightweightHint");

  private final JComponent myComponent;
  private final boolean myFocusableWindowState;
  private final EventListenerList myListenerList;

  private JWindow myWindow;

  public HeavyweightHint(final JComponent component) {
    this(component, true);
  }

  public HeavyweightHint(@NotNull final JComponent component, final boolean focusableWindowState) {
    myComponent = component;
    myFocusableWindowState = focusableWindowState;
    myListenerList = new EventListenerList();
  }

  /**
   * Shows the hint as the window
   */
  public void show(JComponent parentComponent, int x, int y, JComponent focusBackComponent) {
    Dimension preferredSize = myComponent.getPreferredSize();

    LOG.assertTrue(parentComponent.isShowing());

    Window windowAncestor = SwingUtilities.getWindowAncestor(parentComponent);
    LOG.assertTrue(windowAncestor != null);

    myWindow = new JWindow(windowAncestor);
    myWindow.setFocusableWindowState(myFocusableWindowState);

    Point locationOnScreen = parentComponent.getLocationOnScreen();

    myWindow.getContentPane().setLayout(new BorderLayout());
    myWindow.getContentPane().add(myComponent, BorderLayout.CENTER);
    myWindow.setBounds(locationOnScreen.x + x, locationOnScreen.y + y, preferredSize.width, preferredSize.height);
    myWindow.pack();
    myWindow.setVisible(true);
  }

  protected void fireHintHidden() {
    final EventListener[] listeners = myListenerList.getListeners(HintListener.class);
    for (EventListener listener1 : listeners) {
      HintListener listener = (HintListener) listener1;
      listener.hintHidden(new EventObject(this));
    }
  }

  public Dimension getPreferredSize(){
    return myComponent.getPreferredSize();
  }

  public boolean isVisible() {
    return myComponent.isShowing();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  /**
   * Hides and disposes hint window
   */
  public void hide() {
    if(myWindow != null){
      myWindow.dispose();
      myWindow = null;
    }
    fireHintHidden();
  }

  public void addHintListener(HintListener listener) {
    myListenerList.add(HintListener.class, listener);
  }

  public void removeHintListener(HintListener listener) {
    myListenerList.remove(HintListener.class, listener);
  }
}