// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @deprecated Unused, can be replaced with AtomicInteger. If you write your own {@link com.intellij.openapi.vfs.VirtualFileWithId},
 * ensure you comply with the contract from its documentation.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public final class DummyFileIdGenerator {
  private static final Logger LOG = Logger.getInstance(DummyFileIdGenerator.class);
  private static final AtomicInteger ourId = new AtomicInteger(Integer.MAX_VALUE / 2);

  private DummyFileIdGenerator() {
    LOG.warn("com.intellij.openapi.vfs.ex.dummy.DummyFileIdGenerator should not be used");
  }

  public static int next() {
    return ourId.getAndIncrement();
  }
}