package com.intellij.database.datagrid;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

public class BaseObjectNormalizerProvider implements ObjectNormalizerProvider {
  public static final ObjectNormalizerProvider INSTANCE = new BaseObjectNormalizerProvider();

  private BaseObjectNormalizerProvider() {
  }

  @Override
  public boolean isApplicable(GridDataRequest.@NotNull Context context) {
    return true;
  }

  @Override
  public @NotNull Function<GridDataRequest.@NotNull Context, @NotNull ObjectNormalizer> createCache() {
    ObjectNormalizer normalizer = new BaseObjectNormalizer();
    return context -> normalizer;
  }
}
