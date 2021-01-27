// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.util.gist.GistManager;
import com.intellij.util.indexing.FileBasedIndex;

class RootCachesInvalidator extends CachesInvalidator {
  @Override
  public void invalidateCaches() {
    FileBasedIndex.getInstance().invalidateCaches();
    GistManager.getInstance().invalidateData();
  }
}
