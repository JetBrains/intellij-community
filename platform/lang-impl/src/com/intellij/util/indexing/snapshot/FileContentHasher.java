// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot;

import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface FileContentHasher {
  @NotNull
  String getId();

  int getEnumeratedHash(@NotNull FileContent content) throws IOException;
}
