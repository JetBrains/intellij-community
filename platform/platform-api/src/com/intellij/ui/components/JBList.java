/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Anton Makeev
 * @author Konstantin Bulenkov
 */
public class JBList extends JList implements ComponentWithEmptyText, ComponentWithExpandableItems<Integer>{
  @NotNull private StatusText myEmptyText;
  @NotNull private ExpandableItemsHandler<Integer> myExpandableItemsHandler;

  @Nullable private AsyncProcessIcon myBusyIcon;
  private boolean myBusy;


  public JBList() {
    init();
  }

  public JBList(@NotNull ListModel dataModel) {
    super(dataModel);
    init();
  }

  public JBList(@NotNull Object... listData) {
    super(createDefaultListModel(listData));
    init();
  }

  @NotNull
  public static DefaultListModel createDefaultListModel(@NotNull Object... items) {
    final DefaultListModel model = new DefaultListModel();
    for (Object item : items) {
      model.add(model.getSize(), item);
    }
    return model;
  }

  public JBList(@NotNull Collection items) {
    this(ArrayUtil.toObjectArray(items));
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
  public void setCellRenderer(final ListCellRenderer cellRenderer) {
    // myExpandableItemsHandler may not yeb be initialized
    //noinspection ConstantConditions
    if (myExpandableItemsHandler == null) {
      super.setCellRenderer(cellRenderer);
      return;
    }
    super.setCellRenderer(new ExpandedItemListCellRendererWrapper(cellRenderer, myExpandableItemsHandler));
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
      public AccessibleJBListChild(JBList parent, int indexInParent) {
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
