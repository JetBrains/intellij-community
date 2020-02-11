// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class NameEnumerator extends PersistentStringEnumerator {
  public NameEnumerator(@NotNull File file) throws IOException {
    super(file.toPath());
  }

  @NotNull
  public String getName(int idx) {
    try {
      return Objects.requireNonNull(valueOf(idx));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

