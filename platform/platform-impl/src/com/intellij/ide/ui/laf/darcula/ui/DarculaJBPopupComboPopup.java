// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author gregsh
 */
@ApiStatus.Experimental
public class DarculaJBPopupComboPopup<T> implements ComboPopup,
                                                    ItemListener, MouseListener, MouseMotionListener, MouseWheelListener,
                                                    PropertyChangeListener, AncestorListener {

  public static final String CLIENT_PROP = "ComboBox.jbPopup";
  public static final String USE_LIVE_UPDATE_MODEL = "ComboBox.jbPopup.supportUpdateModel";

  private final JComboBox<T> myComboBox;
  private final JList<T> myProxyList = new JBList<>();
  private MyListPopupImpl myPopup;
  private boolean myJustClosedViaClick;

  public DarculaJBPopupComboPopup(@NotNull JComboBox<T> comboBox) {
    myComboBox = comboBox;
    myProxyList.setModel(comboBox.getModel());
    myComboBox.addPropertyChangeListener(this);
    myComboBox.addItemListener(this);
    myComboBox.addAncestorListener(this);
  }

  @NotNull
  private ArrayList<T> copyItemsFromModel() {
    ComboBoxModel<T> model = myComboBox.getModel();
    return copyItemsFromModel(model);
  }

  @NotNull
  private ArrayList<T> copyItemsFromModel(@NotNull ListModel<T> model) {
    ArrayList<T> items = new ArrayList<>(model.getSize());
    for (int i = 0, size = model.getSize(); i < size; i++) {
      items.add(model.getElementAt(i));
    }
    return items;
  }

  @Override
  public void show() {
    myJustClosedViaClick = false;
    if (myPopup != null) {
      if (myPopup.isVisible()) return;
      // onClosed() was not called for some reason
      myPopup.cancel();
    }

    MyBasePopupState step = new MyBasePopupState(() -> myComboBox.getModel()) {
      @Override
      public void canceled() {
        myComboBox.firePopupMenuCanceled();
      }
    };
    step.setDefaultOptionIndex(myComboBox.getSelectedIndex());
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myComboBox));
    myPopup = new MyListPopupImpl(project, step) {
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
    JList<T> list = myPopup.getList();
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
    list.setCellRenderer(new MyDelegateRenderer());
    list.setFocusable(false);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  protected void customizeListRendererComponent(JComponent component) {
    component.setBorder(JBUI.Borders.empty(2, 8));
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

  @Override
  public JList<T> getList() {
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
    myComboBox.removePropertyChangeListener(this);
    myComboBox.removeItemListener(this);
    myComboBox.removeAncestorListener(this);
  }

  @Override
  public void propertyChange(PropertyChangeEvent e) {
    if (!isVisible()) return;

    String propertyName = e.getPropertyName();
    if ("renderer".equals(propertyName) ||
        "editable".equals(propertyName)) {
      hide();
    }

    if ("model".equals(propertyName) && myPopup != null) {
      if (!Boolean.TRUE.equals(myComboBox.getClientProperty(USE_LIVE_UPDATE_MODEL))) {
        hide();
      }
      else {
        syncWithModelChange();
      }
    }
  }

  private void syncWithModelChange() {
    //noinspection unchecked,rawtypes
    List<T> values = ((BaseListPopupStep)myPopup.getStep()).getValues();
    values.clear();
    values.addAll(copyItemsFromModel());
    JList<?> popupList = myPopup.getList();
    myPopup.updateVisibleRowCount();
    ((ListPopupModel<?>)popupList.getModel()).syncModel();
    popupList.setVisibleRowCount(Math.min(values.size(), Math.max(10, myComboBox.getMaximumRowCount())));
    myPopup.setSize(popupList.getPreferredSize());
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

  private class MyDelegateRenderer implements ListCellRenderer<T> {
    @Override
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      //noinspection unchecked
      Component component = myComboBox.getRenderer().getListCellRendererComponent(list, (T)value, index, isSelected, cellHasFocus);
      if (component instanceof JComponent &&
          !(component instanceof JSeparator || component instanceof TitledSeparator)) {
        customizeListRendererComponent((JComponent)component);
      }
      return component;
    }
  }

  // a helper to implement proper cast
  private static abstract class MyStaticBasePopupState<T> extends BaseListPopupStep<T> {
    protected MyStaticBasePopupState(List<? extends T> values) {
      super("", values);
    }
  }

  private class MyBasePopupState extends MyStaticBasePopupState<T> {
    private final Supplier<ListModel<T>> myGetComboboxModel;

    private MyBasePopupState(@NotNull Supplier<ListModel<T>> getComboboxModel) {
      super(copyItemsFromModel(getComboboxModel.get()));
      myGetComboboxModel = getComboboxModel;
    }

    @Nullable
    @Override
    @SuppressWarnings("rawtypes")
    public PopupStep onChosen(T selectedValue, boolean finalChoice) {
      ListModel<T> model = myGetComboboxModel.get();
      if (model instanceof ComboBoxPopupState) {
        //noinspection unchecked
        ListModel<T> nextModel = ((ComboBoxPopupState<T>)model).onChosen(selectedValue);
        if (nextModel != null) {
          return new MyBasePopupState(() -> nextModel);
        }
      }

      // this call could show some more controls...
      ApplicationManager.getApplication().invokeLater(() -> {
        myComboBox.setSelectedItem(selectedValue);
      }, ModalityState.stateForComponent(myComboBox));

      return FINAL_CHOICE;
    }

    @Override
    public boolean hasSubstep(T selectedValue) {
      ListModel<T> model = myGetComboboxModel.get();
      if (model instanceof ComboBoxPopupState) {
        //noinspection unchecked
        return ((ComboBoxPopupState<T>)model).hasSubstep(selectedValue);
      }
      return super.hasSubstep(selectedValue);
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
             component instanceof JLabel ? ((JLabel)component).getText() :
             component instanceof SimpleColoredComponent ?
             ((SimpleColoredComponent)component).getCharSequence(false).toString() : String.valueOf(value);
    }

    @Override
    public boolean isSelectable(T value) {
      Component component = myComboBox.getRenderer().getListCellRendererComponent(myProxyList, value, -1, false, false);
      return !(component instanceof TitledSeparator || component instanceof JSeparator);
    }
  }

  private class MyListPopupImpl extends ListPopupImpl {
    MyListPopupImpl(@Nullable Project project,
                    @NotNull BaseListPopupStep<T> step) {
      super(project, step);
    }

    MyListPopupImpl(@Nullable Project project,
                    @Nullable WizardPopup aParent,
                    @NotNull BaseListPopupStep<T> aStep, Object parentValue) {
      super(project, aParent, aStep, parentValue);
    }

    @Override
    protected void updateVisibleRowCount() {
      super.updateVisibleRowCount();
    }

    @NotNull
    @Override
    protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
      if (step instanceof MyStaticBasePopupState) {
        //noinspection unchecked
        return new MyListPopupImpl(getProject(), parent, (MyStaticBasePopupState<T>)step, parentValue);
      }

      throw new IllegalArgumentException(step.getClass().toString());
    }

    @Override
    public JList<T> getList() {
      //noinspection unchecked
      return super.getList();
    }

    @Override
    protected boolean beforeShow() {
      setMaxRowCount(Math.max(10, myComboBox.getMaximumRowCount()));
      setRequestFocus(false);

      JList<T> list = getList();
      configureList(list);
      Border border = UIManager.getBorder("ComboPopup.border");
      if (border != null) {
        getContent().setBorder(border);
      }

      return super.beforeShow();
    }
  }
}
