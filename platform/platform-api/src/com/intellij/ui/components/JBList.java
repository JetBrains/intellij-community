// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.plaf.ListUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicListUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Anton Makeev
 * @author Konstantin Bulenkov
 */
public class JBList<E> extends JList<E> implements ComponentWithEmptyText, ComponentWithExpandableItems<Integer>{
  private StatusText myEmptyText;
  private ExpandableItemsHandler<Integer> myExpandableItemsHandler;

  @Nullable private AsyncProcessIcon myBusyIcon;
  private boolean myBusy;

  public JBList() {
    init();
  }

  public JBList(@NotNull ListModel<E> dataModel) {
    super(dataModel);
    init();
  }

  public JBList(@NotNull E... listData) {
    super(createDefaultListModel(listData));
    init();
  }

  @NotNull
  public static <T> DefaultListModel<T> createDefaultListModel(@NotNull T... items) {
    return createDefaultListModel(Arrays.asList(items));
  }

  @NotNull
  public static <T> DefaultListModel<T> createDefaultListModel(@NotNull Iterable<T> items) {
    DefaultListModel<T> model = new DefaultListModel<>();
    for (T item : items) {
      model.add(model.getSize(), item);
    }
    return model;
  }

  public JBList(@NotNull Collection<E> items) {
    this(createDefaultListModel(items));
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    if (!ScreenUtil.isStandardAddRemoveNotify(this))
      return;

    if (myBusyIcon != null) {
      remove(myBusyIcon);
      Disposer.dispose(myBusyIcon);
      myBusyIcon = null;
    }
  }

  @Override
  public void doLayout() {
    super.doLayout();

    if (myBusyIcon != null) {
      myBusyIcon.updateLocation(this);
    }
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (myBusyIcon != null) {
      myBusyIcon.updateLocation(this);
    }
  }

  @Override
  public void repaint(long tm, int x, int y, int width, int height) {
    if (width > 0 && height > 0) {
      ListUI ui = getUI();
      // do not paint a line background if layout orientation is not vertical
      if (ui instanceof WideSelectionListUI && JList.VERTICAL == getLayoutOrientation()) {
        x = 0;
        width = getWidth();
      }
      super.repaint(tm, x, y, width, height);
    }
  }

  @Override
  public void setUI(ListUI ui) {
    if (ui != null && Registry.is("ide.wide.selection.list.ui")) {
      Class<? extends ListUI> type = ui.getClass();
      if (type == BasicListUI.class) {
        ui = new WideSelectionListUI();
      }
    }
    super.setUI(ui);
  }

  public void setPaintBusy(boolean paintBusy) {
    if (myBusy == paintBusy) return;

    myBusy = paintBusy;
    updateBusy();
  }

  private void updateBusy() {
    if (myBusy) {
      if (myBusyIcon == null) {
        myBusyIcon = new AsyncProcessIcon(toString()).setUseMask(false);
        myBusyIcon.setOpaque(false);
        myBusyIcon.setPaintPassiveIcon(false);
        add(myBusyIcon);
      }
    }

    if (myBusyIcon != null) {
      if (myBusy) {
        myBusyIcon.resume();
      }
      else {
        myBusyIcon.suspend();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          if (myBusyIcon != null) {
            repaint();
          }
        });
      }
      if (myBusyIcon != null) {
        myBusyIcon.updateLocation(this);
      }
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myEmptyText.paint(this, g);
  }

  @Override
  public Dimension getPreferredSize() {
    if (getModel().getSize() == 0 && !StringUtil.isEmpty(getEmptyText().getText())) {
      Dimension s = getEmptyText().getPreferredSize();
      JBInsets.addTo(s, getInsets());
      return s;
    } else {
      return super.getPreferredSize();
    }
  }

  private void init() {
    setSelectionBackground(UIUtil.getListSelectionBackground());
    setSelectionForeground(UIUtil.getListSelectionForeground());
    installDefaultCopyAction();

    myEmptyText = new StatusText(this) {
      @Override
      protected boolean isStatusVisible() {
        return JBList.this.isEmpty();
      }
    };

    myExpandableItemsHandler = createExpandableItemsHandler();
    setCellRenderer(new DefaultListCellRenderer());
  }

  private void installDefaultCopyAction() {
    final Action copy = getActionMap().get("copy");
    if (copy == null || copy instanceof UIResource) {
      Action newCopy = new AbstractAction() {
        @Override
        public boolean isEnabled() {
          return getSelectedIndex() != -1;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
          ArrayList<String> selected = new ArrayList<>();
          JBList list = JBList.this;
          ListCellRenderer renderer = list.getCellRenderer();
          if (renderer != null) {
            for (int index : getSelectedIndices()) {
              Object value = list.getModel().getElementAt(index);
              //noinspection unchecked
              Component c = renderer.getListCellRendererComponent(list, value, index, true, true);
              SimpleColoredComponent coloredComponent = null;
              if (c instanceof JComponent) {
                coloredComponent = UIUtil.findComponentOfType((JComponent)c, SimpleColoredComponent.class);
              }
              if (coloredComponent != null) {
                selected.add(coloredComponent.toString());
              }
              else if (c instanceof JTextComponent) {
                selected.add(((JTextComponent)c).getText());
              }
              else if (value != null) {
                selected.add(value.toString());
              }
            }
          }

          if (selected.size() > 0) {
            String text = StringUtil.join(selected, " ");
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
          }
        }
      };
      getActionMap().put("copy", newCopy);
    }
  }

  public boolean isEmpty() {
    return getItemsCount() == 0;
  }

  public int getItemsCount() {
    ListModel model = getModel();
    return model == null ? 0 : model.getSize();
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myEmptyText;
  }

  public void setEmptyText(@NotNull String text) {
    myEmptyText.setText(text);
  }

  @Override
  @NotNull
  public ExpandableItemsHandler<Integer> getExpandableItemsHandler() {
    return myExpandableItemsHandler;
  }

  @NotNull
  protected ExpandableItemsHandler<Integer> createExpandableItemsHandler() {
    return ExpandableItemsHandlerFactory.install(this);
  }

  @Override
  public void setExpandableItemsEnabled(boolean enabled) {
    myExpandableItemsHandler.setEnabled(enabled);
  }

  @Override
  public void setCellRenderer(@NotNull ListCellRenderer<? super E> cellRenderer) {
    // myExpandableItemsHandler may not yeb be initialized
    //noinspection ConstantConditions
    if (myExpandableItemsHandler == null) {
      super.setCellRenderer(cellRenderer);
      return;
    }
    super.setCellRenderer(new ExpandedItemListCellRendererWrapper<>(cellRenderer, myExpandableItemsHandler));
  }

  public <T> void installCellRenderer(@NotNull final NotNullFunction<T, JComponent> fun) {
    setCellRenderer(new DefaultListCellRenderer() {
      @NotNull
      @Override
      public Component getListCellRendererComponent(@NotNull JList list,
                                                    Object value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        @SuppressWarnings({"unchecked"})
        final JComponent comp = fun.fun((T)value);
        comp.setOpaque(true);
        if (isSelected) {
          comp.setBackground(list.getSelectionBackground());
          comp.setForeground(list.getSelectionForeground());
        }
        else {
          comp.setBackground(list.getBackground());
          comp.setForeground(list.getForeground());
        }
        for (JLabel label : UIUtil.findComponentsOfType(comp, JLabel.class)) {
          label.setForeground(UIUtil.getListForeground(isSelected));
        }
        return comp;
      }
    });
  }

  public void setDataProvider(@NotNull DataProvider provider) {
    DataManager.registerDataProvider(this, provider);
  }

  public void disableEmptyText() {
    getEmptyText().setText("");
  }

  public static class StripedListCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (!isSelected && index % 2 == 0) {
        setBackground(UIUtil.getDecoratedRowColor());
      }
      return this;
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJBList();
    }
    return accessibleContext;
  }

  protected class AccessibleJBList extends AccessibleJList {
    @Override
    public Accessible getAccessibleAt(Point p) {
      return getAccessibleChild(locationToIndex(p));
    }

    @Override
    public Accessible getAccessibleChild(int i) {
      if (i < 0 || i >= getModel().getSize()) {
        return null;
      } else {
        return new AccessibleJBListChild(JBList.this, i);
      }
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      // In some cases, this method is called from the Access Bridge thread
      // instead of the AWT thread. See https://code.google.com/p/android/issues/detail?id=193072
      return UIUtil.invokeAndWaitIfNeeded(() -> super.getAccessibleRole());
    }

    protected class AccessibleJBListChild extends AccessibleJListChild {
      public AccessibleJBListChild(JBList<E> parent, int indexInParent) {
        super(parent, indexInParent);
      }

      @Override
      public AccessibleRole getAccessibleRole() {
        // In some cases, this method is called from the Access Bridge thread
        // instead of the AWT thread. See https://code.google.com/p/android/issues/detail?id=193072
        return UIUtil.invokeAndWaitIfNeeded(() -> super.getAccessibleRole());
      }
    }
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredScrollableViewportSize(this);
  }

  @NotNull
  static Dimension getPreferredScrollableViewportSize(@NotNull JList list) {
    Dimension size = list.getPreferredSize();
    if (size == null) return new Dimension();
    if (JList.VERTICAL != list.getLayoutOrientation()) return size;

    int fixedWidth = list.getFixedCellWidth();
    int fixedHeight = list.getFixedCellHeight();

    ListModel model = list.getModel();
    int modelRows = model == null ? 0 : model.getSize();
    if (modelRows <= 0) {
      if (fixedWidth <= 0) fixedWidth = Registry.intValue("ide.preferred.scrollable.viewport.fixed.width");
      if (fixedWidth <= 0) fixedWidth = JBUI.scale(256); // scaled value from JDK
      if (fixedHeight <= 0) fixedHeight = Registry.intValue("ide.preferred.scrollable.viewport.fixed.height");
      if (fixedHeight <= 0) fixedHeight = JBUI.scale(16); // scaled value from JDK
    }
    int visibleRows = list.getVisibleRowCount();
    if (visibleRows <= 0) visibleRows = Registry.intValue("ide.preferred.scrollable.viewport.visible.rows");

    boolean addExtraSpace = 0 < visibleRows && visibleRows < modelRows && Registry.is("ide.preferred.scrollable.viewport.extra.space");
    Insets insets = list.getInsets();
    if (0 < fixedWidth && 0 < fixedHeight) {
      size.width = insets != null ? insets.left + insets.right + fixedWidth : fixedWidth;
      size.height = fixedHeight * visibleRows;
      if (addExtraSpace) size.height += fixedHeight / 2;
    }
    else if (addExtraSpace) {
      Rectangle bounds = list.getCellBounds(visibleRows, visibleRows);
      size.height = bounds != null ? bounds.y + bounds.height / 2 : 0;
    }
    else if (visibleRows > 0) {
      int lastRow = Math.min(visibleRows, modelRows) - 1;
      Rectangle bounds = list.getCellBounds(lastRow, lastRow);
      size.height = bounds != null ? bounds.y + bounds.height : 0;
    }
    else {
      size.height = 0;
    }
    if (insets != null) size.height += insets.top + insets.bottom;
    return size;
  }
}
