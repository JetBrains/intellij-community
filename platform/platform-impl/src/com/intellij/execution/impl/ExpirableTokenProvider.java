// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.openapi.util.Expirable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class generates tokens that have an expiration duration.
 * Such tokens are useful in scenarios where results from certain tasks or jobs are temporal or can cause duplications.
 * To invalidate all created tokens at once, use the {@link #invalidateAll()} method.
 */
public class ExpirableTokenProvider {
  private final AtomicInteger modificationStamp = new AtomicInteger();

  public @NotNull Expirable createExpirable() {
    return new MyExpirable(modificationStamp);
  }

  public void invalidateAll() {
    modificationStamp.incrementAndGet();
  }


  private class MyExpirable implements Expirable {
    private final @NotNull AtomicInteger myModificationStamp;
    private final int myCreationStamp = modificationStamp.get();
    private MyExpirable(@NotNull AtomicInteger modificationStamp) {
      myModificationStamp = modificationStamp;
    }


    @Override
    public boolean isExpired() {
      return myCreationStamp != myModificationStamp.get();
    }
  }
}