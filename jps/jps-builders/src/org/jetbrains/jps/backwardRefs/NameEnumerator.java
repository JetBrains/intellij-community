// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public final class NameEnumerator extends PersistentStringEnumerator {
  public NameEnumerator(@NotNull File file) throws IOException {
    super(file.toPath());
  }
}

