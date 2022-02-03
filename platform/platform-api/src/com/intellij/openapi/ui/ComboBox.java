// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.TableCellEditor;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;

/**
 * Due to many bugs and "features" of the default {@link JComboBox} we provide our own "patch".
 * First, it has correct preferred and minimum sizes that has sense when the combo box is editable.
 * Also, this implementation fixes some bugs with clicking of default button.
 * {@code JComboBox} eats first "Enter" if the selected value from the list and changed it
 * (they say that combo box "commits" changes and only second "Enter" clicks the default button).
 * This implementation clicks the default button immediately.
 * As the result of our patch, combo box has an internal wrapper for {@link ComboBoxEditor}.
 * It means that {@link #getEditor()} method always returns not the same value you set by {@link #setEditor(ComboBoxEditor)} method.
 * Moreover, adding and removing of action listeners isn't supported by the wrapper.
 *
 * @author Vladimir Kondratyev
 */
public class ComboBox<E> extends ComboBoxWithWidePopup<E> implements AWTEventListener {
  public static final String TABLE_CELL_EDITOR_PROPERTY = "tableCellEditor";

  private int myMinimumAndPreferredWidth;
  private boolean myUsePreferredSizeAsMinimum = true;

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

  public ComboBox(E @NotNull [] items) {
    super(items);
    init(-1);
  }

  public ComboBox(E @NotNull [] items, int width) {
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
    installComboBoxCopyAction(this);
  }

  private static void installComboBoxCopyAction(JComboBox<?> comboBox) {
    ComboBoxEditor editor = comboBox.getEditor();
    Component editorComponent = editor != null ? editor.getEditorComponent() : null;
    if (!(editorComponent instanceof JTextComponent)) return;
    InputMap inputMap = ((JTextComponent)editorComponent).getInputMap();
    for (KeyStroke keyStroke : inputMap.allKeys()) {
      if (DefaultEditorKit.copyAction.equals(inputMap.get(keyStroke))) {
        comboBox.getInputMap().put(keyStroke, DefaultEditorKit.copyAction);
      }
    }
    comboBox.getActionMap().put(DefaultEditorKit.copyAction, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!(e.getSource() instanceof JComboBox)) return;
        @SuppressWarnings("unchecked") JComboBox<Object> comboBox = (JComboBox<Object>)e.getSource();
        String text;
        Object selectedItem = comboBox.getSelectedItem();
        if (selectedItem instanceof String) {
          text = (String)selectedItem;
        }
        else {
          Component component = comboBox.getRenderer().getListCellRendererComponent(new JList<>(), selectedItem, 0, false, false);
          if (component instanceof JLabel) {
            text = ((JLabel)component).getText();
          }
          else if (component != null) {
            String str = component.toString();
            // skip default Component.toString and handle SimpleColoredComponent case
            text = str == null || str.startsWith(component.getClass().getName() + '[') ? null : str;
          }
          else {
            text = null;
          }
        }
        if (text != null) {
          JTextField textField = new JTextField(text);
          textField.selectAll();
          textField.copy();
        }
      }
    });
  }

  public static void registerTableCellEditor(@NotNull JComboBox<?> comboBox, @NotNull TableCellEditor cellEditor) {
    comboBox.putClientProperty(TABLE_CELL_EDITOR_PROPERTY, cellEditor);
    comboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
  }

  public void registerTableCellEditor(@NotNull TableCellEditor cellEditor) {
    registerTableCellEditor(this, cellEditor);
  }

  @SuppressWarnings("unchecked")
  public E getItem() {
    return (E)getSelectedItem();
  }

  public void setItem(E item) {
    setSelectedItem(item);
  }

  @Override
  public void setPopupVisible(boolean visible) {
    if (getModel().getSize() == 0 && visible) {
      return;
    }
    if (visible) {
      JBPopupFactory jbPopupFactory = getPopupFactory();
      if (jbPopupFactory != null /* allow ComboBox on welcome wizard */ && jbPopupFactory.getChildFocusedPopup(this) != null) {
        return;
      }
    }

    boolean wasShown = isPopupVisible();
    super.setPopupVisible(visible);
    if (!wasShown && visible && isEditable() && !UIManager.getBoolean("ComboBox.isEnterSelectablePopup")) {
      ComboBoxEditor editor = getEditor();
      Object item = editor.getItem(), selectedItem = getSelectedItem();
      if (item == null || item != selectedItem) {
        configureEditor(editor, selectedItem);
      }
    }
  }

  private static @Nullable JBPopupFactory getPopupFactory() {
    return ApplicationManager.getApplication() != null ? JBPopupFactory.getInstance() : null;
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (event.getID() != WindowEvent.WINDOW_OPENED) {
      return;
    }

    JBPopupFactory jbPopupFactory = getPopupFactory();
    if (jbPopupFactory == null) {
      // allow ComboBox on welcome wizard
      return;
    }

    WindowEvent we = (WindowEvent)event;
    for (JBPopup each : jbPopupFactory.getChildPopups(this)) {
      if (!each.isDisposed() && SwingUtilities.isDescendingFrom(each.getContent(), we.getWindow())) {
        super.setPopupVisible(false);
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
  }

  public @Nullable ComboPopup getPopup() {
    return UIUtil.getComboBoxPopup(this);
  }

  /**
   * The {@code false} parameter value enables JBPopup instead of
   * the default ComboBox popup.
   *
   * @param swingPopup {@code false} to enable JBPopup
   * @see ComboBoxPopupState
   * @see com.intellij.ide.ui.laf.darcula.ui.DarculaJBPopupComboPopup
   */
  public void setSwingPopup(boolean swingPopup) {
    putClientProperty("ComboBox.jbPopup", swingPopup ? null : true);
    super.setEditor(null);
    updateUI();
  }

  public boolean isSwingPopup() {
    return getClientProperty("ComboBox.jbPopup") == null;
  }

  @Override
  public void setKeySelectionManager(KeySelectionManager aManager) {
    super.setKeySelectionManager(aManager == null || isSwingPopup() ? aManager : new KeySelectionManager() {
      @Override
      public int selectionForKey(char aKey, ComboBoxModel aModel) {
        showPopup();
        return -1;
      }
    });
  }

  public void setMinimumAndPreferredWidth(int minimumAndPreferredWidth) {
    myMinimumAndPreferredWidth = minimumAndPreferredWidth;
  }

  public boolean isUsePreferredSizeAsMinimum() {
    return myUsePreferredSizeAsMinimum;
  }

  public void setUsePreferredSizeAsMinimum(boolean usePreferredSizeAsMinimum) {
    myUsePreferredSizeAsMinimum = usePreferredSizeAsMinimum;
  }

  private void registerCancelOnEscape() {
    registerKeyboardAction(e -> {
      DialogWrapper dialogWrapper = DialogWrapper.findInstance(this);

      if (isPopupVisible()) {
        setPopupVisible(false);
      }
      else {
        Object clientProperty = getClientProperty(TABLE_CELL_EDITOR_PROPERTY);
        if (clientProperty instanceof CellEditor) {
          // If combo box is inside editable table then we need to cancel editing
          // and do not close heavyweight dialog container (if any)
          ((CellEditor)clientProperty).cancelCellEditing();
        }
        else if (dialogWrapper != null) {
          dialogWrapper.doCancelAction();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  @Override
  public final void setEditor(ComboBoxEditor editor) {
    super.setEditor(new MyEditor(this, editor));
  }

  @Override
  public final Dimension getMinimumSize() {
    return myUsePreferredSizeAsMinimum ? getPreferredSize() : super.getMinimumSize();
  }

  @Override
  public Dimension getPreferredSize() {
    int width = myMinimumAndPreferredWidth;
    Dimension preferredSize = super.getPreferredSize();
    if (width < 0) {
      width = preferredSize.width;
    }
    return new Dimension(width, preferredSize.height);
  }

  @Override
  public void paint(Graphics g) {
    try {
      myPaintingNow = true;
      super.paint(g);
    }
    finally {
      myPaintingNow = false;
    }
  }

  @ApiStatus.Experimental
  public void initBrowsableEditor(@NotNull Runnable browseAction, @Nullable Disposable parentDisposable) {
    ComboBoxEditor editor = new BasicComboBoxEditor() {
      @Override
      protected JTextField createEditorComponent() {
        JTextField editor = new ExtendableTextField().addBrowseExtension(browseAction, parentDisposable);
        editor.setBorder(null);
        return editor;
      }
    };
    setEditor(editor);
    setEditable(true);
  }

  private static final class MyEditor implements ComboBoxEditor {
    private final JComboBox<?> myComboBox;
    private final ComboBoxEditor myDelegate;

    MyEditor(JComboBox<?> comboBox, ComboBoxEditor delegate) {
      myComboBox = comboBox;
      myDelegate = delegate;
      if (myDelegate != null) {
        myDelegate.addActionListener(e -> {
          if (myComboBox.isPopupVisible()) {
            myComboBox.setPopupVisible(false);
          }
          else {
            Object clientProperty = myComboBox.getClientProperty(TABLE_CELL_EDITOR_PROPERTY);
            if (clientProperty instanceof CellEditor) {
              // If combo box is inside editable table then we need to cancel editing
              // and do not close heavyweight dialog container (if any)
              ((CellEditor)clientProperty).stopCellEditing();
            }
            else {
              myComboBox.setSelectedItem(getItem());
              JRootPane rootPane = myComboBox.getRootPane();
              if (rootPane != null) {
                JButton button = rootPane.getDefaultButton();
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
    public void setItem(Object obj) {
      if (myDelegate != null) {
        myDelegate.setItem(obj);
      }
    }
  }
}
