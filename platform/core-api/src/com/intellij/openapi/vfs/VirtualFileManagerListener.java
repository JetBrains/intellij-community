// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import java.util.EventListener;

/**
 * Message bus cannot be used because before / after events are not supported - order of events maybe changed by message bus.
 *
 * Use extension point: {@code <extensionPoint name="virtualFileManagerListener" interface="com.example.Foo"/>}
 */
public interface VirtualFileManagerListener extends EventListener {
  default void beforeRefreshStart(boolean asynchronous) {
  }

  default void afterRefreshFinish(boolean asynchronous) {
  }
}