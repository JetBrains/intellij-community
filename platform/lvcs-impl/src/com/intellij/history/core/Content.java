// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class Content {
  public void write(DataOutput out) throws IOException {
  }

  public abstract byte[] getBytes();

  public byte[] getBytesIfAvailable() {
    return isAvailable() ? getBytes() : null;
  }

  public String getString(Entry e, IdeaGateway gw) {
    return gw.stringFromBytes(getBytes(), e.getPath());
  }

  public abstract boolean isAvailable();

  public abstract void release();

  @Override
  public String toString() {
    return new String(getBytes(), StandardCharsets.UTF_8);
  }

  @Override
  public boolean equals(Object o) {
    return o != null && getClass().equals(o.getClass());
  }
}
