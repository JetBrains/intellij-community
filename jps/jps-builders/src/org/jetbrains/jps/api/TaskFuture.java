// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
public interface TaskFuture<T> extends Future<T> {
  void waitFor();

  boolean waitFor(long timeout, TimeUnit unit);
}
