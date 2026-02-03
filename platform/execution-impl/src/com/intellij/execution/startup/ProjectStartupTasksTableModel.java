// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.startup;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.util.Processor;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.*;

@ApiStatus.Internal
public final class ProjectStartupTasksTableModel extends AbstractTableModel implements EditableModel {
  public static final int NAME_COLUMN = 0;
  public static final int IS_SHARED_COLUMN = 1;

  private final Set<RunnerAndConfigurationSettings> mySharedConfigurations;
  private final List<RunnerAndConfigurationSettings> myAllConfigurations;

  public ProjectStartupTasksTableModel() {
    mySharedConfigurations = new HashSet<>();
    myAllConfigurations = new ArrayList<>();
  }

  public void setData(final Collection<? extends RunnerAndConfigurationSettings> shared,
                      final Collection<? extends RunnerAndConfigurationSettings> local) {
    mySharedConfigurations.clear();
    myAllConfigurations.clear();

    mySharedConfigurations.addAll(shared);
    myAllConfigurations.addAll(shared);
    myAllConfigurations.addAll(local);
    myAllConfigurations.sort(new RunnerAndConfigurationSettingsComparator());
  }

  @Override
  public void addRow() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void exchangeRows(int oldIndex, int newIndex) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean canExchangeRows(int oldIndex, int newIndex) {
    return false;
  }

  @Override
  public void removeRow(int idx) {
    final RunnerAndConfigurationSettings settings = myAllConfigurations.remove(idx);
    if (settings != null) {
      mySharedConfigurations.remove(settings);
      fireTableDataChanged();
    }
  }

  @Override
  public int getRowCount() {
    return myAllConfigurations.size();
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public @NotNull Class<?> getColumnClass(int columnIndex) {
    if (IS_SHARED_COLUMN == columnIndex) {
      return Boolean.class;
    }
    return super.getColumnClass(columnIndex);
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    if (NAME_COLUMN == columnIndex) {
      return myAllConfigurations.get(rowIndex).getName();
    } else if (IS_SHARED_COLUMN == columnIndex) {
      return mySharedConfigurations.contains(myAllConfigurations.get(rowIndex));
    }
    return null;
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (IS_SHARED_COLUMN == columnIndex) {
      if (Boolean.TRUE.equals(aValue)) {
        mySharedConfigurations.add(myAllConfigurations.get(rowIndex));
      } else {
        mySharedConfigurations.remove(myAllConfigurations.get(rowIndex));
      }
      fireTableRowsUpdated(rowIndex, rowIndex + 1);
    }
  }

  @Override
  public @NotNull String getColumnName(int column) {
    if (NAME_COLUMN == column) return ExecutionBundle.message("project.startup.task.table.name.column");
    if (IS_SHARED_COLUMN == column) return ExecutionBundle.message("project.startup.task.table.is.shared.column");
    return "";
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    if (NAME_COLUMN == columnIndex) return false;
    return myAllConfigurations.get(rowIndex).isShared();
  }

  public void addConfiguration(final @NotNull RunnerAndConfigurationSettings configuration) {
    if (myAllConfigurations.contains(configuration)) return;
    myAllConfigurations.add(configuration);
    myAllConfigurations.sort(RunnerAndConfigurationSettingsComparator.getInstance());
    if (configuration.isShared()) {
      mySharedConfigurations.add(configuration);
    }
    fireTableDataChanged();
  }

  public Set<RunnerAndConfigurationSettings> getSharedConfigurations() {
    return mySharedConfigurations;
  }

  public List<RunnerAndConfigurationSettings> getAllConfigurations() {
    return myAllConfigurations;
  }

  public void reValidateConfigurations(final Processor<? super RunnerAndConfigurationSettings> existenceChecker) {
    final Iterator<RunnerAndConfigurationSettings> iterator = myAllConfigurations.iterator();
    while (iterator.hasNext()) {
      final RunnerAndConfigurationSettings settings = iterator.next();
      if (!existenceChecker.process(settings)) {
        iterator.remove();
        mySharedConfigurations.remove(settings);
      }
    }
  }

  public static final class RunnerAndConfigurationSettingsComparator implements Comparator<RunnerAndConfigurationSettings> {
    private static final RunnerAndConfigurationSettingsComparator ourInstance = new RunnerAndConfigurationSettingsComparator();

    public static RunnerAndConfigurationSettingsComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(RunnerAndConfigurationSettings o1, RunnerAndConfigurationSettings o2) {
      return o1.getName().compareToIgnoreCase(o2.getName());
    }
  }
}
