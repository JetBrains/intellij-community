package com.intellij.database.datagrid;

import com.intellij.database.extractors.FormatterCreator;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

public class BaseFormatterCreatorProvider implements FormatterCreatorProvider {
  public static final BaseFormatterCreatorProvider INSTANCE = new BaseFormatterCreatorProvider();

  private BaseFormatterCreatorProvider() {
  }

  @Override
  public boolean isApplicable(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return true;
  }

  @Override
  public boolean isApplicable(@NotNull FormatterCreatorProvider.FormatterCreatorTarget importTarget) {
    return true;
  }

  @Override
  public @NotNull Function<@NotNull CoreGrid<GridRow, GridColumn>, @NotNull FormatterCreator> createCache() {
    FormatterCreator creator = createInner();
    return grid -> creator;
  }

  private static FormatterCreator createInner() {
    return new FormatterCreator();
  }

  @Override
  public @NotNull FormatterCreator create(@NotNull FormatterCreatorProvider.FormatterCreatorTarget importTarget) {
    return createInner();
  }
}
