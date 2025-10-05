// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.overhead;

import com.intellij.CommonBundle;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.Navigatable;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;

public class OverheadView extends BorderLayoutPanel implements Disposable, UiDataProvider {
  private final @NotNull DebugProcessImpl myProcess;

  static final EnabledColumnInfo ENABLED_COLUMN = new EnabledColumnInfo();
  static final NameColumnInfo NAME_COLUMN = new NameColumnInfo();

  private final TableView<OverheadProducer> myTable;
  private final ListTableModel<OverheadProducer> myModel;

  private final MergingUpdateQueue myUpdateQueue;
  private Runnable myBouncer;

  private static final SimpleTextAttributes STRIKEOUT_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null);

  public OverheadView(@NotNull DebugProcessImpl process) {
    myProcess = process;

    myModel = new ListTableModel<>(new ColumnInfo[]{
      ENABLED_COLUMN,
      NAME_COLUMN,
      new TimingColumnInfo(JavaDebuggerBundle.message("column.name.hits"), s -> OverheadTimings.getHits(myProcess, s)),
      new TimingColumnInfo(JavaDebuggerBundle.message("column.name.time.ms"), s -> OverheadTimings.getTime(myProcess, s))},
                                   new ArrayList<>(OverheadTimings.getProducers(process)),
                                   3, SortOrder.DESCENDING);
    myModel.setSortable(true);
    myTable = new TableView<>(myModel);
    addToCenter(ScrollPaneFactory.createScrollPane(myTable, true));
    TableUtil.setupCheckboxColumn(myTable.getColumnModel().getColumn(0));

    myUpdateQueue = new MergingUpdateQueue("OverheadView", 500, true, null, this);

    OverheadTimings.addListener(new OverheadTimings.OverheadTimingsListener() {
                                  @Override
                                  public void timingAdded(OverheadProducer o) {
                                    myUpdateQueue.queue(new Update(o) {
                                      @Override
                                      public void run() {
                                        int idx = myModel.indexOf(o);
                                        if (idx != -1) {
                                          myModel.fireTableRowsUpdated(idx, idx);
                                          return;
                                        }
                                        myModel.setItems(new ArrayList<>(OverheadTimings.getProducers(process)));
                                      }
                                    });
                                  }

                                  @Override
                                  public void excessiveOverheadDetected() {
                                    if (myBouncer != null) {
                                      DebuggerUIUtil.invokeLater(myBouncer);
                                    }
                                  }
                                }
      , process);

    new DumbAwareAction(CommonBundle.message("action.text.toggle")) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(myTable.getSelectedRowCount() == 1);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(final @NotNull AnActionEvent e) {
        myTable.getSelection().forEach(c -> c.setEnabled(!c.isEnabled()));
        myTable.repaint();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myTable);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        ReadAction.nonBlocking(
            () -> getFirstItem(mapNotNull(getSelectedBreakpoints(), XBreakpoint::getNavigatable)))
          .expireWith(OverheadView.this)
          .finishOnUiThread(ModalityState.nonModal(), navigatable -> {
            if (navigatable != null) {
              navigatable.navigate(true);
            }
          })
          .submit(AppExecutorUtil.getAppExecutorService());
        return true;
      }
    }.installOn(myTable);
  }


  private List<XBreakpoint> getSelectedBreakpoints() {
    return StreamEx.of(myTable.getSelection())
      .select(Breakpoint.class)
      .map(Breakpoint::getXBreakpoint).nonNull()
      .toList();
  }


  public JComponent getDefaultFocusedComponent() {
    return myTable;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    var selection = getSelectedBreakpoints();
    sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY, () -> {
      List<Navigatable> navigatables = mapNotNull(selection, XBreakpoint::getNavigatable);
      return navigatables.isEmpty() ? null : navigatables.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
    });
  }

  private static class EnabledColumnInfo extends ColumnInfo<OverheadProducer, Boolean> {
    EnabledColumnInfo() {
      super("");
    }

    @Override
    public Class<?> getColumnClass() {
      return Boolean.class;
    }

    @Override
    public @Nullable Boolean valueOf(OverheadProducer item) {
      return item.isEnabled();
    }

    @Override
    public boolean isCellEditable(OverheadProducer item) {
      return true;
    }

    @Override
    public void setValue(OverheadProducer item, Boolean value) {
      item.setEnabled(value);
    }
  }

  private static class NameColumnInfo extends ColumnInfo<OverheadProducer, OverheadProducer> {
    NameColumnInfo() {
      super(CommonBundle.message("title.name"));
    }

    @Override
    public @Nullable OverheadProducer valueOf(OverheadProducer aspects) {
      return aspects;
    }

    @Override
    public Class<?> getColumnClass() {
      return OverheadProducer.class;
    }

    @Override
    public @Nullable TableCellRenderer getRenderer(OverheadProducer producer) {
      return new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
          if (value instanceof OverheadProducer overheadProducer) {
            if (overheadProducer.isObsolete()) {
              overrideAttributes(overheadProducer, STRIKEOUT_ATTRIBUTES);
            }
            else if (!overheadProducer.isEnabled()) {
              overrideAttributes(overheadProducer, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            else {
              overheadProducer.customizeRenderer(this);
            }
          }
          setTransparentIconBackground(true);
        }

        private void overrideAttributes(OverheadProducer overhead, SimpleTextAttributes attributes) {
          SimpleColoredComponent component = new SimpleColoredComponent();
          overhead.customizeRenderer(component);
          component.iterator().forEachRemaining(f -> append(f, attributes));
          setIcon(component.getIcon());
        }
      };
    }
  }

  private static class TimingColumnInfo extends ColumnInfo<OverheadProducer, OverheadProducer> {
    private final Function<? super OverheadProducer, Long> myGetter;

    TimingColumnInfo(@NotNull @NlsContexts.ColumnName String name, Function<? super OverheadProducer, Long> getter) {
      super(name);
      myGetter = getter;
    }

    @Override
    public @Nullable OverheadProducer valueOf(OverheadProducer aspects) {
      return aspects;
    }

    @Override
    public Class<?> getColumnClass() {
      return OverheadProducer.class;
    }

    @Override
    public @Nullable TableCellRenderer getRenderer(OverheadProducer producer) {
      return new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(@NotNull JTable table,
                                             @Nullable Object value,
                                             boolean selected,
                                             boolean hasFocus,
                                             int row,
                                             int column) {
          if (value instanceof OverheadProducer overheadProducer) {
            Long val = myGetter.apply(overheadProducer);
            append(val != null ? String.valueOf((long)val) : "",
                   overheadProducer.isEnabled() ? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      };
    }

    @Override
    public @Nullable Comparator<OverheadProducer> getComparator() {
      return Comparator.comparing(c -> {
        Long value = myGetter.apply(c);
        return value != null ? value : Long.MAX_VALUE;
      });
    }
  }

  @Override
  public void dispose() {
  }

  public void setBouncer(Runnable bouncer) {
    myBouncer = bouncer;
  }
}
