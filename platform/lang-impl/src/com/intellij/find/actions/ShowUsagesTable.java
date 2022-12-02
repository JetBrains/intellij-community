// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.ide.util.gotoByName.ModelDiff;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageToPsiElementProvider;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ShowUsagesTable extends JBTable implements DataProvider {
  final Usage MORE_USAGES_SEPARATOR = new UsageAdapter();
  final Usage USAGES_OUTSIDE_SCOPE_SEPARATOR = new UsageAdapter();
  final Usage USAGES_FILTERED_OUT_SEPARATOR = new UsageAdapter();

  private final ShowUsagesTableCellRenderer myRenderer;
  private final UsageView myUsageView;

  ShowUsagesTable(@NotNull ShowUsagesTableCellRenderer renderer, UsageView usageView) {
    myRenderer = renderer;
    myUsageView = usageView;
    ScrollingUtil.installActions(this);
    HintUpdateSupply.installDataContextHintUpdateSupply(this);
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (LangDataKeys.POSITION_ADJUSTER_POPUP.is(dataId)) {
      return PopupUtil.getPopupContainerFor(this);
    }
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      List<Object> selection = Arrays.stream(getSelectedRows())
        .mapToObj(o -> getValueAt(o, 0))
        .collect(Collectors.toList());
      return (DataProvider)slowId -> getSlowData(slowId, selection);
    }
    return null;
  }

  private static @Nullable Object getSlowData(@NotNull String dataId, @NotNull List<Object> selection) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      Object single = ContainerUtil.getOnlyItem(selection);
      return single == null ? null : getPsiElementForHint(single);
    }
    return null;
  }

  @Override
  public int getRowHeight() {
    if (ExperimentalUI.isNewUI()) {
      Insets innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets();
      return JBUI.CurrentTheme.List.rowHeight() + innerInsets.top + innerInsets.bottom;
    }

    return super.getRowHeight() + 2 * ShowUsagesTableCellRenderer.MARGIN;
  }

  @NotNull
  Runnable prepareTable(@NotNull Runnable appendMoreUsageRunnable, @NotNull Runnable showInMaximalScopeRunnable) {
    SpeedSearchBase<JTable> speedSearch = new MySpeedSearch(this);
    speedSearch.setComparator(new SpeedSearchComparator(false));

    setRowHeight(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class).getIconHeight() + 2);
    setShowGrid(false);
    setShowVerticalLines(false);
    setShowHorizontalLines(false);
    setTableHeader(null);
    setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);
    setIntercellSpacing(new Dimension(0, 0));

    final AtomicReference<java.util.List<Object>> selectedUsages = new AtomicReference<>();
    final AtomicBoolean moreUsagesSelected = new AtomicBoolean();
    final AtomicBoolean outsideScopeUsagesSelected = new AtomicBoolean();
    final AtomicReference<ShowUsagesAction.FilteredOutUsagesNode> filteredOutUsagesSelected = new AtomicReference<>();
    getSelectionModel().addListSelectionListener(e -> {
      selectedUsages.set(null);
      outsideScopeUsagesSelected.set(false);
      moreUsagesSelected.set(false);
      filteredOutUsagesSelected.set(null);
      java.util.List<Object> usages = null;

      for (int i : getSelectedRows()) {
        Object value = getValueAt(i, 0);
        if (value instanceof UsageNode) {
          Usage usage = ((UsageNode)value).getUsage();
          if (usage == USAGES_OUTSIDE_SCOPE_SEPARATOR) {
            outsideScopeUsagesSelected.set(true);
            usages = null;
            break;
          }
          if (usage == MORE_USAGES_SEPARATOR) {
            moreUsagesSelected.set(true);
            usages = null;
            break;
          }
          if (usage == USAGES_FILTERED_OUT_SEPARATOR) {
            filteredOutUsagesSelected.set((ShowUsagesAction.FilteredOutUsagesNode)value);
            usages = null;
            break;
          }
          if (usages == null) usages = new ArrayList<>();
          usages.add(usage instanceof UsageInfo2UsageAdapter ? ((UsageInfo2UsageAdapter)usage).getUsageInfo().copy() : usage);
        }
      }

      selectedUsages.set(usages);
    });

    return () -> {
      if (moreUsagesSelected.get()) {
        appendMoreUsageRunnable.run();
        return;
      }
      if (outsideScopeUsagesSelected.get()) {
        showInMaximalScopeRunnable.run();
        return;
      }
      if (filteredOutUsagesSelected.get() != null) {
        filteredOutUsagesSelected.get().onSelected();
        return;
      }

      List<Object> usages = selectedUsages.get();
      if (usages != null) {
        for (Object usage : usages) {
          if (usage instanceof UsageInfo) {
            UsageInfo usageInfo = (UsageInfo)usage;
            UsageViewUtil.navigateTo(usageInfo, true);

            PsiElement element = usageInfo.getElement();
            if (element != null) {
              UsageViewStatisticsCollector.logItemChosen(element.getProject(), myUsageView, CodeNavigateSource.ShowUsagesPopup,
                                                         element.getLanguage(), null);
            }
          }
          else if (usage instanceof Navigatable) {
            ((Navigatable)usage).navigate(true);
          }
        }
      }
    };
  }

  public boolean isSeparatorNode(@Nullable Usage node) {
    return node == USAGES_OUTSIDE_SCOPE_SEPARATOR
           ||node == MORE_USAGES_SEPARATOR
           ||node == USAGES_FILTERED_OUT_SEPARATOR;
  }

  @Nullable
  private static PsiElement getPsiElementForHint(Object selectedValue) {
    if (selectedValue instanceof UsageNode) {
      final Usage usage = ((UsageNode)selectedValue).getUsage();
      if (usage instanceof UsageInfo2UsageAdapter) {
        final PsiElement element = ((UsageInfo2UsageAdapter)usage).getElement();
        if (element != null) {
          final PsiElement view = UsageToPsiElementProvider.findAppropriateParentFrom(element);
          return view == null ? element : view;
        }
      }
    }
    return null;
  }

  private static int calcColumnCount(@NotNull List<UsageNode> data) {
    return data.isEmpty() || data.get(0) instanceof ShowUsagesAction.StringNode ? 1 : 4;
  }

  @NotNull
  MyModel setTableModel(@NotNull final List<UsageNode> data) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final int columnCount = calcColumnCount(data);
    MyModel model = getModel() instanceof MyModel ? (MyModel)getModel() : null;
    if (model == null || model.getColumnCount() != columnCount) {
      model = new MyModel(data, columnCount);
      setModel(model);

      for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
        TableColumn column = getColumnModel().getColumn(i);
        column.setPreferredWidth(0);
        column.setCellRenderer(myRenderer);
      }
    }
    return model;
  }

  private static class MySpeedSearch extends SpeedSearchBase<JTable> {
    MySpeedSearch(@NotNull ShowUsagesTable table) {
      super(table);
    }

    @Override
    protected int getSelectedIndex() {
      return getTable().getSelectedRow();
    }

    @Override
    protected int getElementCount() {
      return ((MyModel)getTable().getModel()).getItems().size();
    }

    @Override
    protected Object getElementAt(int viewIndex) {
      return ((MyModel)getTable().getModel()).getItems().get(getTable().convertRowIndexToModel(viewIndex));
    }

    @Override
    protected String getElementText(@NotNull Object element) {
      if (!(element instanceof UsageNode)) return element.toString();
      UsageNode node = (UsageNode)element;
      if (node instanceof ShowUsagesAction.StringNode) return "";
      Usage usage = node.getUsage();
      if (usage == getTable().MORE_USAGES_SEPARATOR || usage == getTable().USAGES_OUTSIDE_SCOPE_SEPARATOR || usage == getTable().USAGES_FILTERED_OUT_SEPARATOR) return "";
      GroupNode group = (GroupNode)node.getParent();
      String groupText = group == null ? "" : group.getGroup().getPresentableGroupText();
      return groupText + usage.getPresentation().getPlainText();
    }

    @Override
    protected void selectElement(Object element, String selectedText) {
      List<UsageNode> data = ((MyModel)getTable().getModel()).getItems();
      int i = data.indexOf(element);
      if (i == -1) return;
      final int viewRow = getTable().convertRowIndexToView(i);
      getTable().getSelectionModel().setSelectionInterval(viewRow, viewRow);
      TableUtil.scrollSelectionToVisible(getTable());
    }

    private ShowUsagesTable getTable() {
      return (ShowUsagesTable)myComponent;
    }
  }

  static final class MyModel extends ListTableModel<UsageNode> implements ModelDiff.Model<UsageNode> {

    private MyModel(@NotNull List<UsageNode> data, int cols) {
      super(cols(cols), data, 0);
    }

    private static ColumnInfo<UsageNode, UsageNode> @NotNull [] cols(int cols) {
      ColumnInfo<UsageNode, UsageNode> o = new ColumnInfo<>("") {
        @Nullable
        @Override
        public UsageNode valueOf(UsageNode node) {
          return node;
        }
      };
      List<ColumnInfo<UsageNode, UsageNode>> list = Collections.nCopies(cols, o);
      return list.toArray(ColumnInfo.emptyArray());
    }

    @Override
    public void addToModel(int idx, UsageNode element) {
      if (idx < getRowCount()) {
        insertRow(idx, element);
      }
      else {
        addRow(element);
      }
    }

    @Override
    public void removeRangeFromModel(int start, int end) {
      for (int i = end; i >= start; i--) {
        removeRow(i);
      }
    }
  }
}
