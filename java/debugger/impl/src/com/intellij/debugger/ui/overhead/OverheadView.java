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
package com.intellij.debugger.ui.overhead;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.function.Function;

/**
 * @author egor
 */
public class OverheadView extends BorderLayoutPanel {
  @NotNull private final DebugProcessImpl myProcess;

  static final EnabledColumnInfo ENABLED_COLUMN = new EnabledColumnInfo();
  static final NameColumnInfo NAME_COLUMN = new NameColumnInfo();

  final JBTable myTable;
  final ListTableModel<BreakpointOverheadItem> myModel;

  public OverheadView(@NotNull DebugProcessImpl process) {
    myProcess = process;

    myModel = new ListTableModel<>(new ColumnInfo[]{
      ENABLED_COLUMN,
      NAME_COLUMN,
      new TimingColumnInfo("hits", s -> OverheadTimings.getHits(myProcess, s.myBreakpoint)),
      new TimingColumnInfo("time", s -> OverheadTimings.getTime(myProcess, s.myBreakpoint))},
                                   StreamEx.of(OverheadTimings.getProducers(process)).select(Breakpoint.class).map(BreakpointOverheadItem::new).toList(),
                                   3, SortOrder.DESCENDING);
    myModel.setSortable(true);
    myTable = new JBTable(myModel);
    addToCenter(ScrollPaneFactory.createScrollPane(myTable));
    TableUtil.setupCheckboxColumn(myTable.getColumnModel().getColumn(0));
    OverheadTimings.addListener(o -> {
      int idx = 0;
      for (BreakpointOverheadItem item : myModel.getItems()) {
        if (item.myBreakpoint == o) {
          myModel.fireTableRowsUpdated(idx, idx);
          return;
        }
        idx++;
      }
      myModel
        .setItems(StreamEx.of(OverheadTimings.getProducers(process)).select(Breakpoint.class).map(BreakpointOverheadItem::new).toList());
      myModel.fireTableDataChanged();
    }, process);

    new DumbAwareAction("Toggle") {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(myTable.getSelectedRowCount() == 1);
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        getSelected().forEach(c -> c.setEnabled(!c.isEnabled()));
        myTable.repaint();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myTable);
  }

  private StreamEx<OverheadItem> getSelected() {
    return IntStreamEx.of(myTable.getSelectedRows()).map(myTable::convertRowIndexToModel).mapToObj(myModel::getItem);
  }

  private static class EnabledColumnInfo extends ColumnInfo<OverheadItem, Boolean> {
    public EnabledColumnInfo() {
      super("");
    }

    @Override
    public Class<?> getColumnClass() {
      return Boolean.class;
    }

    @Nullable
    @Override
    public Boolean valueOf(OverheadItem item) {
      return item.isEnabled();
    }

    @Override
    public boolean isCellEditable(OverheadItem item) {
      return true;
    }

    @Override
    public void setValue(OverheadItem item, Boolean value) {
      item.setEnabled(value);
    }
  }

  private static class NameColumnInfo extends ColumnInfo<BreakpointOverheadItem, String> {
    public NameColumnInfo() {
      super("name");
    }

    @Nullable
    @Override
    public String valueOf(BreakpointOverheadItem aspects) {
      return aspects.getName();
    }

    @Nullable
    @Override
    public Comparator<BreakpointOverheadItem> getComparator() {
      return Comparator.comparing(i -> valueOf(i));
    }
  }

  private static class TimingColumnInfo extends ColumnInfo<BreakpointOverheadItem, Long> {
    private final Function<BreakpointOverheadItem, Long> myGetter;

    public TimingColumnInfo(@NotNull String name, Function<BreakpointOverheadItem, Long> getter) {
      super(name);
      myGetter = getter;
    }

    @Override
    public Class<?> getColumnClass() {
      return Long.class;
    }

    @Nullable
    @Override
    public Long valueOf(BreakpointOverheadItem aspects) {
      return myGetter.apply(aspects);
    }

    @Nullable
    @Override
    public Comparator<BreakpointOverheadItem> getComparator() {
      return Comparator.comparingLong(i -> valueOf(i));
    }
  }

  interface OverheadItem {
    boolean isEnabled();

    void setEnabled(boolean enabled);

    String getName();
  }

  static class BreakpointOverheadItem implements OverheadItem {
    private final Breakpoint myBreakpoint;

    public BreakpointOverheadItem(Breakpoint breakpoint) {
      myBreakpoint = breakpoint;
    }

    @Override
    public String getName() {
      return myBreakpoint.getDisplayName();
    }

    @Override
    public boolean isEnabled() {
      return myBreakpoint.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
      myBreakpoint.setEnabled(enabled);
    }
  }
}
