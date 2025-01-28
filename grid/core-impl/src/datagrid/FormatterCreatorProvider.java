package com.intellij.database.datagrid;

import com.intellij.database.extractors.FormatterCreator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public interface FormatterCreatorProvider {
  ExtensionPointName<FormatterCreatorProvider> EP = ExtensionPointName.create("com.intellij.database.datagrid.formatterCreatorProvider");

  boolean isApplicable(@NotNull CoreGrid<GridRow, GridColumn> grid);

  boolean isApplicable(@NotNull FormatterCreatorTarget importTarget);

  @NotNull
  Function<@NotNull CoreGrid<GridRow, GridColumn>, @NotNull FormatterCreator> createCache();

  @NotNull
  FormatterCreator create(@NotNull FormatterCreatorTarget importTarget);

  static @NotNull FormatterCreator getCreator(@NotNull FormatterCreatorTarget importTarget) {
    FormatterCreatorProvider provider = ContainerUtil.find(EP.getExtensionList(), p -> p.isApplicable(importTarget));
    provider = provider != null ? provider : BaseFormatterCreatorProvider.INSTANCE;
    return provider.create(importTarget);
  }

  static @NotNull FormatterCreatorProvider get(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    FormatterCreatorProvider provider = ContainerUtil.find(EP.getExtensionList(), p -> p.isApplicable(grid));
    return provider != null ? provider : BaseFormatterCreatorProvider.INSTANCE;
  }

  static Function<@NotNull CoreGrid<GridRow, GridColumn>, @NotNull FormatterCreator> getCache() {
    Ref<Function<@NotNull CoreGrid<GridRow, GridColumn>, @NotNull FormatterCreator>> cache = new Ref<>(null);
    return grid -> {
      if (cache.isNull()) {
        cache.set(get(grid).createCache());
      }
      return cache.get().fun(grid);
    };
  }

  interface FormatterCreatorTarget {
  }
}
