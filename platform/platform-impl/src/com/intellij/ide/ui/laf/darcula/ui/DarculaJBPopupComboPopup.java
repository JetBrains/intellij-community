// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ComboBoxPopup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Locale;

import static com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED;

/**
 * @author gregsh
 */
@ApiStatus.Experimental
public class DarculaJBPopupComboPopup<T> implements ComboPopup, ComboBoxPopup.Context<T>,
                                                    ItemListener, MouseListener, MouseMotionListener, MouseWheelListener,
                                                    PropertyChangeListener, AncestorListener, Accessible {

  public static final String CLIENT_PROP = "ComboBox.jbPopup";
  public static final String USE_LIVE_UPDATE_MODEL = "ComboBox.jbPopup.supportUpdateModel";

  private final JComboBox<T> myComboBox;
  private final JList<T> myProxyList = new JBList<>();
  private ComboBoxPopup<T> myPopup;
  private boolean myJustClosedViaClick;

  private AccessibleDarculaJBPopupComboPopup accessibleContext;

  public DarculaJBPopupComboPopup(@NotNull JComboBox<T> comboBox) {
    myComboBox = comboBox;
    myProxyList.setModel(myComboBox.getModel());
    myProxyList.setSelectedValue(myComboBox.getModel().getSelectedItem(), false);
    myProxyList.setCellRenderer(myComboBox.getRenderer());
    myComboBox.addItemListener(this);
    myComboBox.addAncestorListener(this);
    myComboBox.addPropertyChangeListener(this);
  }

  @Nullable
  @Override
  public Project getProject() {
    return CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myComboBox));
  }

  @NotNull
  @Override
  public ListModel<T> getModel() {
    return myComboBox.getModel();
  }

  @NotNull
  @Override
  public ListCellRenderer<? super T> getRenderer() {
    return myComboBox.getRenderer();
  }

  @Override
  public int getMaximumRowCount() {
    return Math.max(10, myComboBox.getMaximumRowCount());
  }

  @Override
  public void onPopupStepCancelled() {
    myComboBox.firePopupMenuCanceled();
  }

  @Override
  public void show() {
    myJustClosedViaClick = false;
    if (myPopup != null) {
      if (myPopup.isVisible()) return;
      // onClosed() was not called for some reason
      myPopup.cancel();
    }

    //noinspection unchecked
    T selectedItem = (T)myComboBox.getSelectedItem();
    myPopup = createPopup(selectedItem);
    myPopup.addListener(new JBPopupListener() {

      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        myComboBox.firePopupMenuWillBecomeVisible();
        //model may drift from the time we decided to open the popup,
        //let's update it to make sure we did not miss anything
        if (useLiveUpdateWithModel()) {
          myPopup.syncWithModelChange();
        }
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myComboBox.firePopupMenuWillBecomeInvisible();
        myPopup = null;
      }
    });
    myPopup.addListener((AccessibleDarculaJBPopupComboPopup)getAccessibleContext());

    myPopup.setMinimumSize(myComboBox.getSize());
    myPopup.showUnderneathOf(myComboBox);
  }

  @Override
  public void configureList(@NotNull JList<T> list) {
    list.setFont(myComboBox.getFont());
    list.setForeground(myComboBox.getForeground());
    list.setBackground(myComboBox.getBackground());
    list.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, myComboBox.getClientProperty(ANIMATION_IN_RENDERER_ALLOWED));
  }

  @Override
  public void hide() {
    myJustClosedViaClick = false;
    if (myPopup == null) return;
    myPopup.cancel();
  }

  @Override
  public boolean isVisible() {
    return myPopup != null && myPopup.isVisible();
  }

  // in JDK 11 ComboPopup.getList returns JList<Object>, not raw JList
  @SuppressWarnings("unchecked")
  @Override
  public JList<Object> getList() {
    return (JList<Object>)myProxyList;
  }

  @Override
  public MouseListener getMouseListener() {
    return this;
  }

  @Override
  public MouseMotionListener getMouseMotionListener() {
    return this;
  }

  @Override
  public KeyListener getKeyListener() {
    return null;
  }

  @Override
  public void uninstallingUI() {
    myComboBox.removePropertyChangeListener(this);
    myComboBox.removeItemListener(this);
    myComboBox.removeAncestorListener(this);
  }

  @Override
  public void propertyChange(PropertyChangeEvent e) {
    if ("model".equals(e.getPropertyName()) && (myPopup == null)) {
      myProxyList.setModel(myComboBox.getModel());
      myProxyList.setCellRenderer(myComboBox.getRenderer());
      myProxyList.setSelectedValue(myComboBox.getModel().getSelectedItem(), false);
    }

    if (!isVisible()) return;

    String propertyName = e.getPropertyName();
    if ("renderer".equals(propertyName) ||
        "editable".equals(propertyName)) {
      hide();
    }

    if ("model".equals(propertyName) && myPopup != null) {
      if (!useLiveUpdateWithModel()) {
        hide();
      }
      else {
        myPopup.syncWithModelChange();
      }
    }
  }

  private boolean useLiveUpdateWithModel() {
    return Boolean.TRUE.equals(myComboBox.getClientProperty(USE_LIVE_UPDATE_MODEL));
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    if ((e.getStateChange() == ItemEvent.SELECTED) && (e.getItem() != null)) {
      myProxyList.setSelectedValue(e.getItem(), false);
    }
  }

  @Override
  public void mouseClicked(MouseEvent e) {
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.getSource() == getList()) return;
    if (!SwingUtilities.isLeftMouseButton(e) || !myComboBox.isEnabled()) return;

    if (myComboBox.isEditable()) {
      Component comp = myComboBox.getEditor().getEditorComponent();
      if ((!(comp instanceof JComponent)) || ((JComponent)comp).isRequestFocusEnabled()) {
        comp.requestFocus();
      }
    }
    else if (myComboBox.isRequestFocusEnabled()) {
      myComboBox.requestFocus();
    }
    if (myJustClosedViaClick) {
      myJustClosedViaClick = false;
      return;
    }
    if (isVisible()) {
      hide();
    }
    else {
      show();
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
  }

  @Override
  public void mouseDragged(MouseEvent e) {
  }

  @Override
  public void mouseMoved(MouseEvent e) {
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
  }

  @Override
  public void ancestorAdded(AncestorEvent event) {

  }

  @Override
  public void ancestorRemoved(AncestorEvent event) {

  }

  @Override
  public void ancestorMoved(AncestorEvent event) {
    hide();
  }

  protected ComboBoxPopup<T> createPopup(@Nullable T selectedItem) {
    return new ComboBoxPopup<>(this, selectedItem, value -> myComboBox.setSelectedItem(value)) {
      @Override
      public void cancel(InputEvent e) {
        if (e instanceof MouseEvent) {
          // we want the second click on combo-box just to close
          // and not to instantly show the popup again in the following
          // DarculaJBPopupComboPopup#mousePressed()
          Point point = new RelativePoint((MouseEvent)e).getPoint(myComboBox);
          myJustClosedViaClick = new Rectangle(myComboBox.getSize()).contains(point);
        }
        super.cancel(e);
      }
    };
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleDarculaJBPopupComboPopup();
    }
    return accessibleContext;
  }

  private class AccessibleDarculaJBPopupComboPopup extends AccessibleContext implements JBPopupListener {

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.POPUP_MENU;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet stateSet = new AccessibleStateSet();
      if (DarculaJBPopupComboPopup.this.isVisible()) {
        stateSet.add(AccessibleState.VISIBLE);
      }
      stateSet.add(AccessibleState.ENABLED);
      AccessibleContext ac = myComboBox.getAccessibleContext();
      if (ac != null) {
        Accessible ap = ac.getAccessibleParent();
        if (ap != null) {
          AccessibleContext pac = ap.getAccessibleContext();
          if (pac != null) {
            AccessibleSelection as = pac.getAccessibleSelection();
            if (as != null) {
              stateSet.add(AccessibleState.SELECTABLE);
              int i = ac.getAccessibleIndexInParent();
              if (i >= 0) {
                if (as.isAccessibleChildSelected(i)) {
                  stateSet.add(AccessibleState.SELECTED);
                }
              }
            }
          }
        }
      }
      return stateSet;
    }

    @Override
    public int getAccessibleIndexInParent() {
      return 0;
    }

    @Override
    public int getAccessibleChildrenCount() {
      return 0;
    }

    @Override
    public Accessible getAccessibleChild(int i) {
      return null;
    }

    @Override
    public Locale getLocale() throws IllegalComponentStateException {
      return myComboBox.getLocale();
    }

    @Override
    public void beforeShown(@NotNull LightweightWindowEvent event) {
      handlePopupIsVisibleEvent(true);
    }

    @Override
    public void onClosed(@NotNull LightweightWindowEvent event) {
      handlePopupIsVisibleEvent(false);
    }

    private void handlePopupIsVisibleEvent(boolean visible) {
      if (visible) {
        firePropertyChange(ACCESSIBLE_STATE_PROPERTY, null, AccessibleState.VISIBLE);
      }
      else {
        firePropertyChange(ACCESSIBLE_STATE_PROPERTY, AccessibleState.VISIBLE, null);
      }
    }
  }
}
