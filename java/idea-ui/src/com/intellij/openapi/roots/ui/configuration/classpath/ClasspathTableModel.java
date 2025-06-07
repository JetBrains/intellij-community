// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class ClasspathTableModel extends ListTableModel<ClasspathTableItem<?>> implements ItemRemovable {
  private static final ColumnInfo<ClasspathTableItem<?>, Boolean> EXPORT_COLUMN_INFO = new ColumnInfo<>(getExportColumnName()) {
    @Override
    public Boolean valueOf(ClasspathTableItem<?> item) {
      return item.isExported();
    }

    @Override
    public void setValue(ClasspathTableItem<?> item, Boolean value) {
      item.setExported(value);
    }

    @Override
    public boolean isCellEditable(ClasspathTableItem<?> item) {
      return item.isExportable();
    }

    @Override
    public Class<Boolean> getColumnClass() {
      return Boolean.class;
    }
  };
  private static final Comparator<DependencyScope> DEPENDENCY_SCOPE_COMPARATOR =
    (o1, o2) -> o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
  private static final Comparator<ClasspathTableItem<?>> CLASSPATH_ITEM_SCOPE_COMPARATOR =
    (o1, o2) -> Comparing.compare(o1.getScope(), o2.getScope(), DEPENDENCY_SCOPE_COMPARATOR);
  private static final ColumnInfo<ClasspathTableItem<?>, DependencyScope> SCOPE_COLUMN_INFO = new ColumnInfo<>(getScopeColumnName()) {
    @Override
    public @Nullable DependencyScope valueOf(ClasspathTableItem<?> item) {
      return item.getScope();
    }

    @Override
    public void setValue(ClasspathTableItem<?> item, DependencyScope value) {
      item.setScope(value);
    }

    @Override
    public boolean isCellEditable(ClasspathTableItem<?> item) {
      return item.isExportable();
    }

    @Override
    public Class<DependencyScope> getColumnClass() {
      return DependencyScope.class;
    }

    @Override
    public Comparator<ClasspathTableItem<?>> getComparator() {
      return CLASSPATH_ITEM_SCOPE_COMPARATOR;
    }
  };
  public static final int EXPORT_COLUMN = 0;
  public static final int ITEM_COLUMN = 1;
  public static final int SCOPE_COLUMN = 2;
  private final ModuleConfigurationState myState;
  private final StructureConfigurableContext myContext;

  ClasspathTableModel(final ModuleConfigurationState state, StructureConfigurableContext context) {
    super(EXPORT_COLUMN_INFO, new ClasspathTableItemClasspathColumnInfo(context), SCOPE_COLUMN_INFO);
    myState = state;
    myContext = context;
    init();
  }

  @Override
  public RowSorter.SortKey getDefaultSortKey() {
    return new RowSorter.SortKey(1, SortOrder.UNSORTED);
  }

  public void init() {
    final OrderEntry[] orderEntries = myState.getModifiableRootModel().getOrderEntries();
    boolean hasJdkOrderEntry = false;
    List<ClasspathTableItem<?>> items = new ArrayList<>();
    for (final OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof JdkOrderEntry) {
        hasJdkOrderEntry = true;
      }
      items.add(ClasspathTableItem.createItem(orderEntry, myContext));
    }
    if (!hasJdkOrderEntry) {
      items.add(0, new InvalidJdkItem());
    }
    setItems(items);
  }

  @Override
  public void exchangeRows(int idx1, int idx2) {
    super.exchangeRows(idx1, idx2);
    List<OrderEntry> entries = getEntries();
    myState.getModifiableRootModel().rearrangeOrderEntries(entries.toArray(OrderEntry.EMPTY_ARRAY));
  }

  public void clear() {
    setItems(new ArrayList<>());
  }

  private List<OrderEntry> getEntries() {
    final int count = getRowCount();
    final List<OrderEntry> entries = new ArrayList<>(count);
    for (int row = 0; row < count; row++) {
      final OrderEntry entry = getItem(row).getEntry();
      if (entry != null) {
        entries.add(entry);
      }
    }
    return entries;
  }

  private static class ClasspathTableItemClasspathColumnInfo extends ColumnInfo<ClasspathTableItem<?>, ClasspathTableItem<?>> {
    private final Comparator<ClasspathTableItem<?>> myItemComparator;

    ClasspathTableItemClasspathColumnInfo(final StructureConfigurableContext context) {
      super("");
      myItemComparator = (o1, o2) -> {
        String text1 = ClasspathPanelImpl.getCellAppearance(o1, context, false).getText();
        String text2 = ClasspathPanelImpl.getCellAppearance(o2, context, false).getText();
        return text1.compareToIgnoreCase(text2);
      };
    }

    @Override
    public @Nullable Comparator<ClasspathTableItem<?>> getComparator() {
      return myItemComparator;
    }

    @Override
    public @Nullable ClasspathTableItem<?> valueOf(ClasspathTableItem<?> item) {
      return item;
    }

    @Override
    public Class<?> getColumnClass() {
      return ClasspathTableItem.class;
    }
  }

  private static @NlsContexts.ColumnName String getScopeColumnName() {
    return JavaUiBundle.message("modules.order.export.scope.column");
  }

  static @NlsContexts.ColumnName String getExportColumnName() {
    return JavaUiBundle.message("modules.order.export.export.column");
  }
}
