// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.dsl.listCellRenderer.KotlinUIDslRendererComponent;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu;
import org.jetbrains.annotations.ApiStatus;
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
import java.awt.im.InputMethodRequests;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

/**
 * @author Anton Makeev
 * @author Konstantin Bulenkov
 */
public class JBList<E> extends JList<E> implements ComponentWithEmptyText, ComponentWithExpandableItems<Integer> {
  public static final String IGNORE_LIST_ROW_HEIGHT = "IgnoreListRowHeight";

  /**
   * Allows performance optimizations if the model and the renderer for all elements are immutable:
   * the same item is rendered with exactly the same content
   */
  @ApiStatus.Internal
  public static final String IMMUTABLE_MODEL_AND_RENDERER = "ImmutableModelAndRenderer";

  private StatusText myEmptyText;
  private ExpandableItemsHandler<Integer> myExpandableItemsHandler;

  private @Nullable AsyncProcessIcon myBusyIcon;
  private boolean myBusy;

  private int myDropTargetIndex = -1;
  private @NotNull Function<? super Integer, Integer> offsetFromElementTopForDnD = dropTargetIndex -> 0;


  public JBList() {
    init();
  }

  public JBList(@NotNull ListModel<E> dataModel) {
    super(dataModel);
    init();
  }

  public JBList(E @NotNull ... listData) {
    super(createDefaultListModel(listData));
    init();
  }

  public static @NotNull <T> DefaultListModel<T> createDefaultListModel(T @NotNull ... items) {
    return createDefaultListModel(Arrays.asList(items));
  }

  public static @NotNull <T> DefaultListModel<T> createDefaultListModel(@NotNull Iterable<? extends T> items) {
    DefaultListModel<T> model = new DefaultListModel<>();
    for (T item : items) {
      model.add(model.getSize(), item);
    }
    return model;
  }

  public JBList(@NotNull Collection<? extends E> items) {
    this(createDefaultListModel(items));
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    if (!ScreenUtil.isStandardAddRemoveNotify(this)) {
      return;
    }

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
      // do not paint a line background if the layout orientation is not vertical
      if (ui instanceof WideSelectionListUI && getLayoutOrientation() == VERTICAL) {
        x = 0;
        width = getWidth();
      }
      super.repaint(tm, x, y, width, height);
    }
  }

  @Override
  public void setUI(ListUI ui) {
    if (ui != null && Registry.is("ide.wide.selection.list.ui", true)) {
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
        myBusyIcon = new AsyncProcessIcon(toString());
        myBusyIcon.setOpaque(false);
        myBusyIcon.setPaintPassiveIcon(false);
        add(myBusyIcon);
      }
    }

    //noinspection DuplicatedCode
    if (myBusyIcon != null) {
      if (myBusy) {
        myBusyIcon.resume();
      }
      else {
        myBusyIcon.suspend();
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

  public void setDropTargetIndex(int index) {
    if (index != myDropTargetIndex) {
      myDropTargetIndex = index;
      repaint();
    }
  }

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public void setOffsetFromElementTopForDnD(@NotNull Function<? super Integer, Integer> offsetFromElementTopForDnD) {
    this.offsetFromElementTopForDnD = offsetFromElementTopForDnD;
  }

  /** @deprecated use {@link #setOffsetFromElementTopForDnD(Function)} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings({"LambdaUnfriendlyMethodOverload", "UsagesOfObsoleteApi"})
  public void setOffsetFromElementTopForDnD(@NotNull com.intellij.util.Function<? super Integer, Integer> offsetFromElementTopForDnD) {
    setOffsetFromElementTopForDnD((Function<? super Integer, Integer>)offsetFromElementTopForDnD);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myEmptyText.paint(this, g);
    if (myDropTargetIndex < 0) {
      return;
    }
    int dropLineY;
    Rectangle rc;
    if (myDropTargetIndex == getModel().getSize()) {
      rc = getCellBounds(myDropTargetIndex-1, myDropTargetIndex-1);
      dropLineY = (int)rc.getMaxY()-1;
    }
    else {
      rc = getCellBounds(myDropTargetIndex, myDropTargetIndex);
      dropLineY = rc.y + offsetFromElementTopForDnD.apply(myDropTargetIndex);
    }
    Graphics2D g2d = (Graphics2D) g;
    g2d.setColor(PlatformColors.BLUE);
    g2d.setStroke(new BasicStroke(2.0f));
    g2d.drawLine(rc.x, dropLineY, rc.x+rc.width, dropLineY);
    g2d.drawLine(rc.x, dropLineY-2, rc.x, dropLineY+2);
    g2d.drawLine(rc.x+rc.width, dropLineY-2, rc.x+rc.width, dropLineY+2);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension s = getEmptyText().getPreferredSize();
    JBInsets.addTo(s, getInsets());
    Dimension size = super.getPreferredSize();
    return new Dimension(Math.max(s.width, size.width), Math.max(s.height, size.height));
  }

  protected final Dimension super_getPreferredSize() {
    return super.getPreferredSize();
  }

  private void init() {
    setSelectionBackground(UIUtil.getListSelectionBackground(true));
    setSelectionForeground(NamedColorUtil.getListSelectionForeground(true));
    installDefaultCopyAction();

    myEmptyText = new StatusText(this) {
      @Override
      protected boolean isStatusVisible() {
        return JBList.this.isEmpty();
      }
    };

    putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, myEmptyText.getWrappedFragmentsIterable());
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
      if (text != null) selected.add(text);
    }

    if (!selected.isEmpty()) {
      String text = StringUtil.join(selected, "\n");
      CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }
  }

  protected @Nullable String itemToText(int index, E value) {
    ListCellRenderer<? super E> renderer = getCellRenderer();
    Component c = renderer == null ? null : renderer.getListCellRendererComponent(this, value, index, true, true);
    if (c != null) {
      c = ExpandedItemRendererComponentWrapper.unwrap(c);
    }

    if (c instanceof KotlinUIDslRendererComponent uiDslRendererComponent) {
      return uiDslRendererComponent.getCopyText();
    }

    SimpleColoredComponent coloredComponent = null;
    if (c instanceof JComponent) {
      coloredComponent = UIUtil.findComponentOfType((JComponent)c, SimpleColoredComponent.class);
    }
    return coloredComponent != null ? coloredComponent.getCharSequence(true).toString() :
           c instanceof JTextComponent ? ((JTextComponent)c).getText() :
           c instanceof JLabel ? ((JLabel)c).getText() :
           value != null ? value.toString() : null;
  }

  public boolean isEmpty() {
    return getItemsCount() == 0;
  }

  public int getItemsCount() {
    ListModel<?> model = getModel();
    return model == null ? 0 : model.getSize();
  }

  @Override
  public @NotNull StatusText getEmptyText() {
    return myEmptyText;
  }

  public void setEmptyText(@NotNull @NlsContexts.StatusText String text) {
    myEmptyText.setText(text);
  }

  @Override
  public @NotNull ExpandableItemsHandler<Integer> getExpandableItemsHandler() {
    return myExpandableItemsHandler;
  }

  protected @NotNull ExpandableItemsHandler<Integer> createExpandableItemsHandler() {
    return ExpandableItemsHandlerFactory.install(this);
  }

  @Override
  public void setExpandableItemsEnabled(boolean enabled) {
    myExpandableItemsHandler.setEnabled(enabled);
  }

  @Override
  public void setCellRenderer(@NotNull ListCellRenderer<? super E> cellRenderer) {
    // myExpandableItemsHandler may not yeb be initialized
    if (myExpandableItemsHandler == null) {
      super.setCellRenderer(cellRenderer);
      return;
    }
    super.setCellRenderer(new ExpandedItemListCellRendererWrapper<>(cellRenderer, myExpandableItemsHandler));
  }


  /**
   * @see com.intellij.ui.treeStructure.Tree#getScrollableUnitIncrement(Rectangle, int, int)
   */
  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    int increment = super.getScrollableUnitIncrement(visibleRect, orientation, direction);
    return adjustIncrement(visibleRect, orientation, direction, increment);
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    int increment = super.getScrollableBlockIncrement(visibleRect, orientation, direction);
    // in the case of scrolling list up using a single-wheel rotation,
    // the block increment is used to calculate min scroll as `currScroll - blockIncrement`
    // the resulting y-position of visible rect never exceeds min scroll
    // see `javax.swing.plaf.basic.BasicScrollPaneUI.Handler.mouseWheelMoved` -> vertical handling part -> limitScroll
    return adjustIncrement(visibleRect, orientation, direction, increment);
  }


  private static int adjustIncrement(Rectangle visibleRect, int orientation, int direction, int increment) {
    if (increment == 0 && orientation == SwingConstants.VERTICAL && direction < 0) {
      return visibleRect.y;
    }
    return increment;
  }

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public void installCellRenderer(@NotNull Function<? super E, ? extends @NotNull JComponent> fun) {
    setCellRenderer(new SelectionAwareListCellRenderer<>(fun));
  }

  /** @deprecated use {@link #installCellRenderer(Function)} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings({"LambdaUnfriendlyMethodOverload", "UnnecessaryFullyQualifiedName", "UsagesOfObsoleteApi"})
  public void installCellRenderer(@NotNull com.intellij.util.NotNullFunction<? super E, ? extends JComponent> fun) {
    installCellRenderer((Function<? super E, ? extends @NotNull JComponent>)fun);
  }

  /** @deprecated Implement {@link com.intellij.openapi.actionSystem.UiDataProvider} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("UsagesOfObsoleteApi")
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

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleContextDelegateWithContextMenu(super.getAccessibleContext()) {
          @Override
          protected void doShowContextMenu() {
            ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, null, null, true);
          }

          @Override
          protected Container getDelegateParent() {
            return getParent();
          }
        };
      }
      return accessibleContext;
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
      return i < 0 || i >= getModel().getSize() ? null : new AccessibleJBListChild(JBList.this, i);
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      // In some cases, this method is called from the 'Access Bridge' thread instead of the AWT.
      // See https://code.google.com/p/android/issues/detail?id=193072
      return UIUtil.invokeAndWaitIfNeeded(() -> super.getAccessibleRole());
    }

    protected class AccessibleJBListChild extends AccessibleJListChild {
      public AccessibleJBListChild(JBList<E> parent, int indexInParent) {
        super(parent, indexInParent);
      }

      @Override
      public AccessibleRole getAccessibleRole() {
        // In some cases, this method is called from the 'Access Bridge' thread instead of the AWT.
        // See https://code.google.com/p/android/issues/detail?id=193072
        return UIUtil.invokeAndWaitIfNeeded(() -> super.getAccessibleRole());
      }
    }
  }

  @Override
  public InputMethodRequests getInputMethodRequests() {
    SpeedSearchSupply supply = SpeedSearchSupply.getSupply(this, true);
    if (supply == null) {
      return null;
    } else {
      return supply.getInputMethodRequests();
    }
  }
}
