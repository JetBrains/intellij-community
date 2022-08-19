// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompositeDataProvider implements DataProvider {

  private final List<? extends DataProvider> myProviders;

  private CompositeDataProvider(@NotNull List<? extends DataProvider> providers) { myProviders = providers; }

  public static @NotNull DataProvider compose(@NotNull DataProvider provider1, @Nullable DataProvider provider2) {
    if (provider2 == null) {
      return provider1;
    }
    List<? extends DataProvider> p1 = provider1 instanceof CompositeDataProvider ? ((CompositeDataProvider)provider1).myProviders :
                                      Collections.singletonList(provider1);
    List<? extends DataProvider> p2 = provider2 instanceof CompositeDataProvider ? ((CompositeDataProvider)provider2).myProviders :
                                      Collections.singletonList(provider2);
    return new CompositeDataProvider(new ArrayList<>(ContainerUtil.concat(p1, p2)));
  }

  public static @NotNull DataProvider compose(@NotNull Iterable<? extends DataProvider> providers) {
    List<DataProvider> list = null;
    for (DataProvider provider : providers) {
      if (list == null) list = new SmartList<>();
      if (provider instanceof CompositeDataProvider) list.addAll(((CompositeDataProvider)provider).myProviders);
      else list.add(provider);
    }
    return list == null ? dataId -> null :
           list.size() == 1 ? list.get(0) :
           new CompositeDataProvider(new ArrayList<>(list));
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    for (DataProvider dataProvider : myProviders) {
      if (dataProvider != null) {
        final Object data = dataProvider.getData(dataId);
        if (data != null) return data;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public @NotNull Iterable<? extends DataProvider> getDataProviders() {
    return myProviders;
  }
}
