// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.util.messages.Topic;

import java.util.EventListener;

/**
 * Message bus cannot be used because before / after events are not supported - order of events maybe changed by message bus.
 * <p>
 * Use extension point: {@code <extensionPoint name="virtualFileManagerListener" interface="com.example.Foo"/>}
 */
public interface VirtualFileManagerListener extends EventListener {
  @Topic.AppLevel
  Topic<VirtualFileManagerListener> TOPIC =
    new Topic<>(VirtualFileManagerListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

  default void beforeRefreshStart(boolean asynchronous) { }

  default void afterRefreshFinish(boolean asynchronous) { }
}
