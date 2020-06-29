// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.overhead;

import com.intellij.CommonBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.pom.Navigatable;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
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
import java.util.function.Function;

public class OverheadView extends BorderLayoutPanel implements Disposable, DataProvider {
  @NotNull private final DebugProcessImpl myProcess;

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
      new TimingColumnInfo("Hits", s -> OverheadTimings.getHits(myProcess, s)),
      new TimingColumnInfo("Time (ms)", s -> OverheadTimings.getTime(myProcess, s))},
                                   new ArrayList<>(OverheadTimings.getProducers(process)),
                                   3, SortOrder.DESCENDING);
    myModel.setSortable(true);
    myTable = new TableView<>(myModel);
    addToCenter(ScrollPaneFactory.createScrollPane(myTable));
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
      public void actionPerformed(@NotNull final AnActionEvent e) {
        myTable.getSelection().forEach(c -> c.setEnabled(!c.isEnabled()));
        myTable.repaint();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myTable);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        getSelectedNavigatables().findFirst().ifPresent(b -> b.navigate(true));
        return true;
      }
    }.installOn(myTable);
  }


  private StreamEx<Navigatable> getSelectedNavigatables() {
    return StreamEx.of(myTable.getSelection())
      .select(Breakpoint.class)
      .map(Breakpoint::getXBreakpoint).nonNull()
      .map(XBreakpoint::getNavigatable).nonNull();
  }


  public JComponent getDefaultFocusedComponent() {
    return myTable;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      Navigatable[] navigatables = getSelectedNavigatables().toArray(Navigatable.class);
      if (navigatables.length > 0) {
        return navigatables;
      }
    }
    return null;
  }

  private static class EnabledColumnInfo extends ColumnInfo<OverheadProducer, Boolean> {
    EnabledColumnInfo() {
      super("");
    }

    @Override
    public Class<?> getColumnClass() {
      return Boolean.class;
    }

    @Nullable
    @Override
    public Boolean valueOf(OverheadProducer item) {
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

    @Nullable
    @Override
    public OverheadProducer valueOf(OverheadProducer aspects) {
      return aspects;
    }

    @Override
    public Class<?> getColumnClass() {
      return OverheadProducer.class;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(OverheadProducer producer) {
      return new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
          if (value instanceof OverheadProducer) {
            OverheadProducer overheadProducer = (OverheadProducer)value;
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

    TimingColumnInfo(@NotNull String name, Function<? super OverheadProducer, Long> getter) {
      super(name);
      myGetter = getter;
    }

    @Nullable
    @Override
    public OverheadProducer valueOf(OverheadProducer aspects) {
      return aspects;
    }

    @Override
    public Class<?> getColumnClass() {
      return OverheadProducer.class;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(OverheadProducer producer) {
      return new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(@NotNull JTable table,
                                             @Nullable Object value,
                                             boolean selected,
                                             boolean hasFocus,
                                             int row,
                                             int column) {
          if (value instanceof OverheadProducer) {
            OverheadProducer overheadProducer = (OverheadProducer)value;
            Long val = myGetter.apply(overheadProducer);
            append(val != null ? String.valueOf(val) : "",
                   overheadProducer.isEnabled() ? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      };
    }

    @Nullable
    @Override
    public Comparator<OverheadProducer> getComparator() {
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
