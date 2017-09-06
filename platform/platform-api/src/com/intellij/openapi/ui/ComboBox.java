/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.*;

/**
 * Due to many bugs and "features" in {@code JComboBox} implementation we provide
 * our own "patch". First of all it has correct preferred and minimum sizes that has sense
 * when combo box is editable. Also this implementation fixes some bugs with clicking
 * of default button. The SUN's combo box eats first "Enter" if the selected value from
 * the list and changed it. They say that combo box "commit" changes and only second
 * "Enter" clicks default button. This implementation clicks the default button
 * immediately. As the result of our patch combo box has internal wrapper for ComboBoxEditor.
 * It means that {@code getEditor} method always returns not the same value you set
 * by {@code setEditor} method. Moreover adding and removing of action listeners
 * isn't supported by the implementation of wrapper.
 *
 * @author Vladimir Kondratyev
 */
public class ComboBox<E> extends ComboBoxWithWidePopup<E> implements AWTEventListener {
  public static final String TABLE_CELL_EDITOR_PROPERTY = "tableCellEditor";

  private int myMinimumAndPreferredWidth;
  private boolean mySwingPopup = true;
  private JBPopup myJBPopup;
  protected boolean myPaintingNow;

  public ComboBox() {
    init(-1);
  }

  public ComboBox(int width) {
    init(width);
  }

  public ComboBox(@NotNull ComboBoxModel<E> model) {
    super(model);
    init(-1);
  }

  public ComboBox(@NotNull E[] items) {
    super(items);
    init(-1);
  }

  public ComboBox(@NotNull E[] items, int width) {
    super(items);
    init(width);
  }

  public ComboBox(@NotNull ComboBoxModel<E> model, int width) {
    super(model);
    init(width);
  }

  /**
   * @param width preferred width of the combobox. Value {@code -1} means undefined.
   */
  private void init(int width) {
    myMinimumAndPreferredWidth = width;
    registerCancelOnEscape();
    UIUtil.installComboBoxCopyAction(this);
    final JButton arrowButton = UIUtil.findComponentOfType(this, JButton.class);
    if (arrowButton != null) {
      arrowButton.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (!mySwingPopup) {
            e.consume();
            setPopupVisible(true);
          }
        }
      });
    }
  }

  public static void registerTableCellEditor(@NotNull JComboBox comboBox, @NotNull TableCellEditor cellEditor) {
    comboBox.putClientProperty(TABLE_CELL_EDITOR_PROPERTY, cellEditor);
    comboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
  }

  public void registerTableCellEditor(@NotNull TableCellEditor cellEditor) {
    registerTableCellEditor(this, cellEditor);
  }

  @Override
  public void setPopupVisible(boolean visible) {
    if (!isSwingPopup()) {
      if (visible && (myJBPopup == null || myJBPopup.isDisposed())) {
        final JBList<E> list = createJBList(getModel());
        myJBPopup = JBPopupFactory.getInstance()
          .createListPopupBuilder(list)
          .setItemChoosenCallback(() -> {
            final Object value = list.getSelectedValue();
            if (value != null) {
              configureEditor(getEditor(), value);
              IdeFocusManager.getGlobalInstance().requestFocus(this, true);
              assert myJBPopup != null;
              getUI().setPopupVisible(this, false);
              myJBPopup.cancel();
            }
          })
          .setFocusOwners(new Component[]{this})
          .setMinSize(new Dimension(getWidth(), -1))
          .createPopup();
        list.setBorder(JBUI.Borders.empty());
        myJBPopup.showUnderneathOf(this);
        list.addFocusListener(new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            getUI().setPopupVisible(ComboBox.this, false);
            myJBPopup.cancel();
          }
        });
      }
      return;
    }

    if (getModel().getSize() == 0 && visible) return;
    if (visible && JBPopupFactory.getInstance().getChildFocusedPopup(this) != null) return;

    final boolean wasShown = isPopupVisible();
    super.setPopupVisible(visible);
    if (!wasShown
        && visible
        && isEditable()
        && !UIManager.getBoolean("ComboBox.isEnterSelectablePopup")) {
      final ComboBoxEditor editor = getEditor();
      final Object item = editor.getItem();
      final Object selectedItem = getSelectedItem();
      if (isSwingPopup() && (item == null || item != selectedItem)) {
        configureEditor(editor, selectedItem);
      }
    }
  }

  protected JBList<E> createJBList(ComboBoxModel<E> model) {
    return new JBList<>(model);
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (event.getID() == WindowEvent.WINDOW_OPENED
        && ApplicationManager.getApplication() != null /* Allow ComboBox on welcome wizard*/) {
      final WindowEvent we = (WindowEvent)event;
      for (JBPopup each : JBPopupFactory.getInstance().getChildPopups(this)) {
        if (each.getContent() != null && SwingUtilities.isDescendingFrom(each.getContent(), we.getWindow())) {
          super.setPopupVisible(false);
        }
      }
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();

    if (getParent() instanceof JTable) {
      putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
    }

    Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.WINDOW_EVENT_MASK);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    Toolkit.getDefaultToolkit().removeAWTEventListener(this);
    if (myJBPopup != null) {
      getUI().setPopupVisible(this, false);
      myJBPopup.cancel();

    }
  }

  @Nullable
  public ComboPopup getPopup() {
    return UIUtil.getComboBoxPopup(this);
  }

  public boolean isSwingPopup() {
    return mySwingPopup;
  }

  public void setSwingPopup(boolean swingPopup) {
    mySwingPopup = swingPopup;
  }

  public void setMinimumAndPreferredWidth(final int minimumAndPreferredWidth) {
    myMinimumAndPreferredWidth = minimumAndPreferredWidth;
  }

  private void registerCancelOnEscape() {
    registerKeyboardAction(e -> {
      final DialogWrapper dialogWrapper = DialogWrapper.findInstance(this);

      if (isPopupVisible()) {
        setPopupVisible(false);
      }
      else {
        //noinspection HardCodedStringLiteral
        final Object clientProperty = getClientProperty(TABLE_CELL_EDITOR_PROPERTY);
        if (clientProperty instanceof CellEditor) {
          // If combo box is inside editable table then we need to cancel editing
          // and do not close heavy weight dialog container (if any)
          ((CellEditor)clientProperty).cancelCellEditing();
        }
        else if (dialogWrapper != null) {
          dialogWrapper.doCancelAction();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  @Override
  public final void setEditor(final ComboBoxEditor editor) {
    super.setEditor(new MyEditor(this, editor));
  }

  @Override
  public final Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public final Dimension getPreferredSize() {
    int width = myMinimumAndPreferredWidth;
    final Dimension preferredSize = super.getPreferredSize();
    if (width < 0) {
      width = preferredSize.width;
    }

    return new Dimension(width, UIUtil.fixComboBoxHeight(preferredSize.height));
  }

  @Override
  public void paint(Graphics g) {
    try {
      myPaintingNow = true;
      super.paint(g);
      if (Boolean.TRUE != getClientProperty("JComboBox.isTableCellEditor") && isEditable) MacUIUtil.drawComboboxFocusRing(this, g);
    }
    finally {
      myPaintingNow = false;
    }
  }

  private static final class MyEditor implements ComboBoxEditor {
    private final JComboBox myComboBox;
    private final ComboBoxEditor myDelegate;

    public MyEditor(final JComboBox comboBox, final ComboBoxEditor delegate) {
      myComboBox = comboBox;
      myDelegate = delegate;
      if (myDelegate != null) {
        myDelegate.addActionListener(e -> {
          if (myComboBox.isPopupVisible()) {
            myComboBox.setPopupVisible(false);
          }
          else {
            final Object clientProperty = myComboBox.getClientProperty(TABLE_CELL_EDITOR_PROPERTY);
            if (clientProperty instanceof CellEditor) {
              // If combo box is inside editable table then we need to cancel editing
              // and do not close heavy weight dialog container (if any)
              ((CellEditor)clientProperty).stopCellEditing();
            }
            else {
              myComboBox.setSelectedItem(getItem());
              final JRootPane rootPane = myComboBox.getRootPane();
              if (rootPane != null) {
                final JButton button = rootPane.getDefaultButton();
                if (button != null) {
                  button.doClick();
                }
              }
            }
          }
        });
      }
    }

    @Override
    public void addActionListener(ActionListener l) { }

    @Override
    public void removeActionListener(ActionListener l) { }

    @Override
    public Component getEditorComponent() {
      return myDelegate == null ? null : myDelegate.getEditorComponent();
    }

    @Override
    public Object getItem() {
      return myDelegate == null ? null : myDelegate.getItem();
    }

    @Override
    public void selectAll() {
      if (myDelegate != null) {
        myDelegate.selectAll();
      }
    }

    @Override
    public void setItem(final Object obj) {
      if (myDelegate != null) {
        myDelegate.setItem(obj);
      }
    }
  }
}