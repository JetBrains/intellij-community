// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import java.util.EventListener;

public interface VirtualFileManagerListener extends EventListener {
  default void beforeRefreshStart(boolean asynchronous) {
  }

  default void afterRefreshFinish(boolean asynchronous) {
  }
}