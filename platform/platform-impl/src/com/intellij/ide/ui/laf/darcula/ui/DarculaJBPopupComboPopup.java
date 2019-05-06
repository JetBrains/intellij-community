// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author gregsh
 */
@ApiStatus.Experimental
public class DarculaJBPopupComboPopup<T> implements ComboPopup,
                                                    ItemListener, MouseListener, MouseMotionListener, MouseWheelListener,
                                                    PropertyChangeListener, Serializable {

  public static final String CLIENT_PROP = "ComboBox.jbPopup";

  private final JComboBox<T> myComboBox;
  private final JList<T> myProxyList = new JBList<>();
  private ListPopupImpl myPopup;

  public DarculaJBPopupComboPopup(@NotNull JComboBox<T> comboBox) {
    myComboBox = comboBox;
    myProxyList.setModel(comboBox.getModel());
  }

  @Override
  public void show() {
    if (myPopup != null) return;
    ArrayList<T> items = new ArrayList<>(myComboBox.getModel().getSize());
    for (int i = 0, size = myComboBox.getModel().getSize(); i < size; i++) {
      items.add(myComboBox.getModel().getElementAt(i));
    }
    BaseListPopupStep<T> step = new BaseListPopupStep<T>("", items) {
      @Nullable
      @Override
      public PopupStep onChosen(T selectedValue, boolean finalChoice) {
        myComboBox.setSelectedItem(selectedValue);
        return FINAL_CHOICE;
      }

      @Override
      public void canceled() {
        myComboBox.firePopupMenuCanceled();
      }

      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }

      @NotNull
      @Override
      public String getTextFor(T value) {
        Component component = myComboBox.getRenderer().getListCellRendererComponent(myProxyList, value, -1, false, false);
        return component instanceof TitledSeparator || component instanceof JSeparator ? "" :
               component instanceof SimpleColoredComponent ?
               ((SimpleColoredComponent)component).getCharSequence(false).toString() : String.valueOf(value);
      }

      @Override
      public boolean isSelectable(T value) {
        Component component = myComboBox.getRenderer().getListCellRendererComponent(myProxyList, value, -1, false, false);
        return !(component instanceof TitledSeparator || component instanceof JSeparator);
      }
    };
    step.setDefaultOptionIndex(myComboBox.getSelectedIndex());
    myPopup = new ListPopupImpl(step, 10);
    myPopup.setRequestFocus(false);
    myPopup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        myComboBox.firePopupMenuWillBecomeVisible();
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myComboBox.firePopupMenuWillBecomeInvisible();
        myPopup = null;
        myProxyList.setCellRenderer(new DefaultListCellRenderer());
        myProxyList.setModel(myComboBox.getModel());
      }
    });
    //noinspection unchecked
    JList<T> list = myPopup.getList();
    configureList(list);
    Border border = UIManager.getBorder("ComboPopup.border");
    if (border != null) {
      myPopup.getContent().setBorder(border);
    }

    myProxyList.setCellRenderer(list.getCellRenderer());
    myProxyList.setModel(list.getModel());
    myPopup.setMinimumSize(myComboBox.getSize());
    myPopup.showUnderneathOf(myComboBox);
  }

  protected void configureList(@NotNull JList<T> list) {
    list.setFont(myComboBox.getFont());
    list.setForeground(myComboBox.getForeground());
    list.setBackground(myComboBox.getBackground());
    list.setSelectionForeground(UIManager.getColor("ComboBox.selectionForeground"));
    list.setSelectionBackground(UIManager.getColor("ComboBox.selectionBackground"));
    list.setBorder(null);
    list.setCellRenderer(myComboBox.getRenderer());
    list.setFocusable(false);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  @Override
  public void hide() {
    if (myPopup == null) return;
    myPopup.cancel();
  }

  @Override
  public boolean isVisible() {
    return myPopup != null && myPopup.isVisible();
  }

  @Override
  public JList getList() {
    return myProxyList;
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
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
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
  public void propertyChange(PropertyChangeEvent evt) {
  }
}
