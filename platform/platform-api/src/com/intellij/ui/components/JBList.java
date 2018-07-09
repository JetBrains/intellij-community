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
import com.intellij.util.containers.ContainerUtil;
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
          doCopyToClipboardAction();
        }
      };
      getActionMap().put("copy", newCopy);
    }
  }

  protected void doCopyToClipboardAction() {
    ArrayList<String> selected = new ArrayList<>();
    for (int index : getSelectedIndices()) {
      E value = getModel().getElementAt(index);
      String text = itemToText(index, value);
      ContainerUtil.addIfNotNull(selected, text);
    }

    if (selected.size() > 0) {
      String text = StringUtil.join(selected, "\n");
      CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }
  }

  @Nullable
  private String itemToText(int index, E value) {
    ListCellRenderer renderer = getCellRenderer();
    //noinspection unchecked
    Component c = renderer == null ? null : renderer.getListCellRendererComponent(this, value, index, true, true);
    SimpleColoredComponent coloredComponent = null;
    if (c instanceof JComponent) {
      coloredComponent = UIUtil.findComponentOfType((JComponent)c, SimpleColoredComponent.class);
    }
    return coloredComponent != null ? coloredComponent.getCharSequence(true).toString() :
           c instanceof JTextComponent ? ((JTextComponent)c).getText() :
           value != null ? value.toString() : null;
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
    setCellRenderer(new SelectionAwareListCellRenderer<>(fun));
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
}
