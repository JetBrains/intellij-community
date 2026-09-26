// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;

public interface InvokerSupplier {
  /**
   * @return preferable invoker to be used to access the supplier
   */
  @NotNull
  Invoker getInvoker();
}
