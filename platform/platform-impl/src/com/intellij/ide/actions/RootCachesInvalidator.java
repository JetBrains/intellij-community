// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.util.gist.GistManager;

final class RootCachesInvalidator extends CachesInvalidator {
  @Override
  public void invalidateCaches() {
    GistManager.getInstance().invalidateData();
  }
}
