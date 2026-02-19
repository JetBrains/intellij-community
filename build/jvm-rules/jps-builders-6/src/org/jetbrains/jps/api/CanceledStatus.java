// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.api;

public interface CanceledStatus {
  CanceledStatus NULL = new CanceledStatus() {
    @Override
    public boolean isCanceled() {
      return false;
    }
  };

  boolean isCanceled();
}
