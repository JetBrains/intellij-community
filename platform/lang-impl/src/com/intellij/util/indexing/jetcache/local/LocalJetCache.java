// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache.local;

import com.intellij.util.indexing.jetcache.JetCacheLocalStorage;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class LocalJetCache extends JetCacheLocalStorage {
  public LocalJetCache(@NotNull File file) {
    super(file);
  }
}
