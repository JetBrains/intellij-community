// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.DocumentListener;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for unregistering a document listener when its parent disposable is disposed.
 * <p>
 * It stores only the listener collection and the listener to avoid document leak when the listener is leaked
 */
final class DocumentListenerDisposable implements Disposable {
  private final @NotNull LockFreeCOWSortedArray<? super DocumentListener> myListeners;
  private final @NotNull DocumentListener myListener;

  DocumentListenerDisposable(
    @NotNull LockFreeCOWSortedArray<? super DocumentListener> listeners,
    @NotNull DocumentListener listener
  ) {
    myListeners = listeners;
    myListener = listener;
  }

  @Override
  public void dispose() {
    myListeners.remove(myListener);
  }
}
