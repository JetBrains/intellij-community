// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface VirtualFileAppearanceListener {
  Topic<VirtualFileAppearanceListener> TOPIC = new Topic<>(VirtualFileAppearanceListener.class);

  /**
   * Indicates that the presentable name or icon of the file have been updated.
   */
  void virtualFileAppearanceChanged(@NotNull VirtualFile virtualFile);

  static void fireVirtualFileAppearanceChanged(@NotNull VirtualFile virtualFile) {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).virtualFileAppearanceChanged(virtualFile);
  }
}