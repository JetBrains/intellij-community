// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.ide.util.gotoByName.ModelDiff;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
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
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Contract;
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
import java.util.function.Supplier;

public final class ShowUsagesTable extends JBTable implements UiDataProvider {
  final Usage MORE_USAGES_SEPARATOR = new UsageAdapter();
  final Usage USAGES_OUTSIDE_SCOPE_SEPARATOR = new UsageAdapter();
  final Usage USAGES_FILTERED_OUT_SEPARATOR = new UsageAdapter();

  static final int MAX_COLUMN_WIDTH = 500;
  static final int MIN_COLUMN_WIDTH = 200;

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
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(LangDataKeys.POSITION_ADJUSTER_POPUP, PopupUtil.getPopupContainerFor(this));
    sink.set(UsageView.USAGE_VIEW_KEY, myUsageView);
    List<Object> selection = Arrays.stream(getSelectedRows())
      .mapToObj(o -> getValueAt(o, 0))
      .toList();
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
      Object single = ContainerUtil.getOnlyItem(selection);
      return single == null ? null : getPsiElementForHint(single);
    });
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
  Runnable prepareTable(@NotNull Runnable appendMoreUsageRunnable, @NotNull Runnable showInMaximalScopeRunnable,
                        @NotNull ShowUsagesActionHandler actionHandler) {
    SpeedSearchBase<JTable> speedSearch = MySpeedSearch.installOn(this);
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
      //todo List<Usage>
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
          usages.add(usage instanceof UsageInfo2UsageAdapter ? ((UsageInfo2UsageAdapter)usage).getUsageInfo() : usage);
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
          if (usage instanceof UsageInfo usageInfo) {
            PsiElement selectedElement = usageInfo.getElement();
            if (selectedElement != null) {
              String recentSearchText = speedSearch.getComparator().getRecentSearchText();
              int numberOfLettersTyped = recentSearchText != null ? recentSearchText.length() : 0;
              Project project = selectedElement.getProject();
              ReadAction.nonBlocking(() -> actionHandler.buildFinishEventData(usageInfo)).submit(AppExecutorUtil.getAppExecutorService())
                .onSuccess(finishEventData ->
                             UsageViewStatisticsCollector.logItemChosenInPopupFeatures(project, myUsageView, selectedElement,
                                                                                       finishEventData));
              UsageViewStatisticsCollector.logItemChosen(project, myUsageView, CodeNavigateSource.ShowUsagesPopup, getSelectedRow(),
                                                         getRowCount(),
                                                         numberOfLettersTyped,
                                                         selectedElement.getLanguage(), false);
            }
            UsageViewUtil.navigateTo(usageInfo, true);
          }
          else if (usage instanceof Navigatable) {
            ((Navigatable)usage).navigate(true);
          }
        }
      }
    };
  }

  public boolean isFullLineNode(UsageNode node) {
    if (node instanceof ShowUsagesAction.StringNode) return true;

    Usage usage = node.getUsage();
    return usage == USAGES_OUTSIDE_SCOPE_SEPARATOR
           || usage == MORE_USAGES_SEPARATOR
           || usage == USAGES_FILTERED_OUT_SEPARATOR;
  }

  private static @Nullable PsiElement getPsiElementForHint(Object selectedValue) {
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
  MyModel setTableModel(final @NotNull List<UsageNode> data) {
    ThreadingAssertions.assertEventDispatchThread();
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

  private static final class MySpeedSearch extends SpeedSearchBase<JTable> {
    private MySpeedSearch(@NotNull ShowUsagesTable table) {
      super(table, null);
    }

    @Contract("_ -> new")
    static @NotNull MySpeedSearch installOn(@NotNull ShowUsagesTable table) {
      MySpeedSearch search = new MySpeedSearch(table);
      search.setupListeners();
      return search;
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
      if (!(element instanceof UsageNode node)) return element.toString();
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

    private final CellSizesCache cellSizesCache;

    private MyModel(@NotNull List<UsageNode> data, int cols) {
      super(cols(cols), data, 0);
      cellSizesCache = new CellSizesCache(data.size(), cols);
    }

    private static ColumnInfo<UsageNode, UsageNode> @NotNull [] cols(int cols) {
      ColumnInfo<UsageNode, UsageNode> o = new ColumnInfo<>("") {
        @Override
        public @Nullable UsageNode valueOf(UsageNode node) {
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
        cellSizesCache.addLine(idx);
      }
      else {
        addRow(element);
        cellSizesCache.addLine();
      }
    }

    @Override
    public void removeRangeFromModel(int start, int end) {
      for (int i = end; i >= start; i--) {
        removeRow(i);
        cellSizesCache.removeLine(i);
      }
    }

    public int getOrCalcCellWidth(int row, int col, Supplier<Integer> supplier) {
      return cellSizesCache.getOrCalculate(row, col, supplier);
    }
  }

  static class CellSizesCache {

    private final List<Integer[]> table;
    private final int colsNumber;

    private CellSizesCache(int rows, int cols) {
      colsNumber = cols;
      table = new ArrayList<>(rows);
      for (int i = 0; i < rows; i++) {
        table.add(new Integer[cols]);
      }
    }

    void addLine() {
      table.add(new Integer[colsNumber]);
    }

    void addLine(int row) {
      table.add(row, new Integer[colsNumber]);
    }

    void removeLine(int row) {
      table.remove(row);
    }

    int getOrCalculate(int row, int col, Supplier<Integer> supplier) {
      Integer cached = table.get(row)[col];
      if (cached != null) return cached;

      Integer newVal = supplier.get();
      table.get(row)[col] = newVal;
      return newVal;
    }
  }
}
