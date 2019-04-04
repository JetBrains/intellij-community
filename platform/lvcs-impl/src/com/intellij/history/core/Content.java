/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.history.core;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;

import java.io.DataOutput;
import java.io.IOException;

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
    return new String(getBytes());
  }

  @Override
  public boolean equals(Object o) {
    return o != null && getClass().equals(o.getClass());
  }
}
