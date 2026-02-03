// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.LowMemoryWatcher;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.function.UnaryOperator;

@Service
@ApiStatus.Internal
public final class StubStringInterner implements Disposable, UnaryOperator<@Nullable String> {
  private final LoadingCache<String, String> internCache = Caffeine.newBuilder()
    .maximumSize(8192)
    .executor(ExecutorsKt.asExecutor(Dispatchers.getDefault()))
    .build(key -> key);

  static StubStringInterner getInstance() {
    return ApplicationManager.getApplication().getService(StubStringInterner.class);
  }

  @VisibleForTesting
  public StubStringInterner() {
    LowMemoryWatcher.register(internCache::invalidateAll, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public @Nullable String apply(@Nullable String s) {
    return s == null ? null : internCache.get(s);
  }
}
