package com.intellij.database.datagrid;

import com.intellij.database.extractors.BaseObjectFormatter;
import com.intellij.database.extractors.ObjectFormatter;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public interface ObjectNormalizerProvider {
  ExtensionPointName<ObjectNormalizerProvider> EP = ExtensionPointName.create("com.intellij.database.datagrid.objectNormalizerProvider");

  boolean isApplicable(GridDataRequest.@NotNull Context context);

  @NotNull
  Function<GridDataRequest.@NotNull Context, @NotNull ObjectNormalizer> createCache();

  default ObjectFormatter getCoupledFormatter() { return new BaseObjectFormatter(); }

  static @NotNull ObjectNormalizerProvider get(GridDataRequest.@NotNull Context context) {
    ObjectNormalizerProvider provider = ContainerUtil.find(EP.getExtensionList(), p -> p.isApplicable(context));
    return provider != null ? provider : BaseObjectNormalizerProvider.INSTANCE;
  }

  static Function<GridDataRequest.@NotNull Context, @NotNull ObjectNormalizer> getCache() {
    Ref<Function<GridDataRequest.@NotNull Context, @NotNull ObjectNormalizer>> cache = new Ref<>(null);
    return context -> {
      if (cache.isNull()) {
        cache.set(get(context).createCache());
      }
      return cache.get().fun(context);
    };
  }
}
