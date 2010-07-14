/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.dnd;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AwtVisitor;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.MouseDragGestureRecognizer;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

/**
 * Utility tool to patch DnD listeners to enable multiple selection when original dnd is switched off
 *
 * @author spleaner
 */
public class DnDEnabler implements Activatable, Disposable {

  @NonNls public static final String KEY = "DragAndDropMultipleSelectionEnabler";

  private final AWTEventListener myAwtListener = new MyAwtListener();
  private List<EventListener> myMouseListeners;
  //private List<MouseListener> myMousePreprocessors = new ArrayList<MouseListener>();
  private final DnDAware myDnDSource;
  private MouseListener myOriginalDragGestureRecognizer;

  private final LafManagerListener myLafManagerListener = new LafManagerListener() {
    public void lookAndFeelChanged(LafManager source) {
      // todo[spleaner]: does default listeners are recreated onSetUI() and what 'bout custom listeners??
      onSetUI();
    }
  };
  private MouseListener myTooltipListener1;
  private MouseListener myTooltipListener2;

  public DnDEnabler(@NotNull final DnDAware source, Disposable parent) {
    myDnDSource = source;
    final Component component = source.getComponent();

    LafManager.getInstance().addLafManagerListener(myLafManagerListener);

    final UiNotifyConnector connector = new UiNotifyConnector(component, this);// todo: disposable???
    Disposer.register(this, connector);
    Disposer.register(parent, this);

    onSetUI();
  }

  public void dispose() {
    LafManager.getInstance().removeLafManagerListener(myLafManagerListener);
    myOriginalDragGestureRecognizer = null;
  }

  public void showNotify() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(myAwtListener);
    Toolkit.getDefaultToolkit().addAWTEventListener(myAwtListener, MouseEvent.MOUSE_EVENT_MASK);
  }

  public void hideNotify() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(myAwtListener);
  }

  public void onSetUI() {
    myMouseListeners = new ArrayList<EventListener>();
    new AwtVisitor(myDnDSource.getComponent()) {
      public boolean visit(Component component) {
        EventListener[] mouseListeners = component.getListeners(MouseListener.class);
        if (mouseListeners.length > 0) {
          ContainerUtil.addAll(myMouseListeners, mouseListeners);
          for (EventListener each : mouseListeners) {
            if (each instanceof MouseDragGestureRecognizer) {
              myOriginalDragGestureRecognizer = (MouseListener)each;
              myMouseListeners.remove(each);
            }

            component.removeMouseListener((MouseListener)each);
          }
        }

        return false;
      }
    };

    readTooltipListeners();
  }

  private void readTooltipListeners() {
    final ToolTipManager manager = ToolTipManager.sharedInstance();

    myTooltipListener1 = manager;
    try {

//todo kirillk to detach mouseMotion listeners as well
      final Field moveBefore = manager.getClass().getDeclaredField("moveBeforeEnterListener");
      if (!MouseListener.class.isAssignableFrom(moveBefore.getType())) return;
      moveBefore.setAccessible(true);
      myTooltipListener2 = (MouseListener)moveBefore.get(manager);
    }
    catch (Exception ignored) {
    }
  }

  private static void dispatchMouseEvent(MouseListener listener, MouseEvent e) {
    if (listener != null) {
      int id = e.getID();
      switch (id) {
        case MouseEvent.MOUSE_PRESSED:
          listener.mousePressed(e);
          break;
        case MouseEvent.MOUSE_RELEASED:
          listener.mouseReleased(e);
          break;
        case MouseEvent.MOUSE_CLICKED:
          listener.mouseClicked(e);
          break;
        case MouseEvent.MOUSE_EXITED:
          listener.mouseExited(e);
          break;
        case MouseEvent.MOUSE_ENTERED:
          listener.mouseEntered(e);
          break;
      }
    }
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isPressedToSelection(MouseEvent e) {
    if (MouseEvent.MOUSE_PRESSED != e.getID()) return false;
    return isToSelection(e);
  }

  private boolean isToSelection(final MouseEvent e) {
    if (!isPureButton1Event(e)) return false;
    return e.getClickCount() == 1 && myDnDSource.isOverSelection(e.getPoint());
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isPopupToSelection(MouseEvent e) {
    return e.isPopupTrigger() && myDnDSource.isOverSelection(e.getPoint());
  }

  private static boolean isPureButton1Event(MouseEvent event) {
    int button1 = MouseEvent.BUTTON1_MASK | MouseEvent.BUTTON1_DOWN_MASK;
    return (event.getModifiersEx() | button1) == button1;
  }

  private class MyAwtListener implements AWTEventListener {
    public void eventDispatched(AWTEvent event) {
      if (event instanceof MouseEvent) {
        MouseEvent e = (MouseEvent)event;

        Component comp = myDnDSource.getComponent();
        if (e.getComponent() != comp) return;

        //for (MouseListener each : myMousePreprocessors) {
        //  dispatchMouseEvent(each, e);
        //  if (e.isConsumed()) return;
        //}

        if (e.getComponent() == comp) {
          boolean shouldProcessTooltipManager = true;
          if (e.getComponent() instanceof JComponent) {
            final JComponent c = (JComponent)e.getComponent();
            if (c.getToolTipText() == null) {
              shouldProcessTooltipManager = false;
            }
          }

          if (isPressedToSelection(e)) {
            if (myDnDSource.getComponent().isFocusable()) {
              myDnDSource.getComponent().requestFocus();
            }
          }
          else {
            final boolean popupToSelection = isPopupToSelection(e);
            if (!e.isConsumed()) {
              assert e.getComponent() != null : "component is null! IDEADEV-6339";
              final EventListener[] eventListeners = myMouseListeners.toArray(new EventListener[myMouseListeners.size()]);
              for (EventListener each : eventListeners) {
                if (!shouldProcessTooltipManager) {
                  if (each == myTooltipListener1 || each == myTooltipListener2) continue;
                }

                if (popupToSelection) {
                  if (each != null && each.getClass().getName().indexOf("BasicTreeUI$DragFixHandler") >= 0) continue;
                }

                if (isToSelection(e) && e.getID() == MouseEvent.MOUSE_RELEASED) {
                  myDnDSource.dropSelectionButUnderPoint(e.getPoint());
                }

                dispatchMouseEvent((MouseListener)each, e);
                if (e.isConsumed()) break;
              }

              if (shouldProcessTooltipManager) {
                ((JComponent)e.getComponent()).setToolTipText(null);
              }
            }
          }

          if (myOriginalDragGestureRecognizer != null && !shouldIgnore(e, comp)) {
            dispatchMouseEvent(myOriginalDragGestureRecognizer, e);
          }
        }
      }
    }
  }

  private static boolean shouldIgnore(MouseEvent event, Component c) {
    return c == null || !c.isEnabled()
                         || !SwingUtilities.isLeftMouseButton(event);
  }

}
