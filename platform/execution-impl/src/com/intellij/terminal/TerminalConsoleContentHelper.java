// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

class TerminalConsoleContentHelper implements Disposable {

  private static final int FLUSH_TIMEOUT = 200;

  private final Collection<ObservableConsoleView.ChangeListener> myChangeListeners = new CopyOnWriteArraySet<>();
  private final BlockingQueue<Pair<String, ConsoleViewContentType>> myTextChunks = new LinkedBlockingQueue<>();
  private final Alarm myAlarm;
  private final AtomicBoolean myRequested = new AtomicBoolean(false);
  private volatile boolean myDisposed = false;

  TerminalConsoleContentHelper(@NotNull TerminalExecutionConsole console) {
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD /* won't be disposed to call addRequest safely */);
    Disposer.register(console, this);
  }

  public void addChangeListener(@NotNull ObservableConsoleView.ChangeListener listener, @NotNull Disposable parent) {
    myChangeListeners.add(listener);
    Disposer.register(parent, () -> myChangeListeners.remove(listener));
  }

  public void onContentTypePrinted(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    myTextChunks.add(Pair.create(text, contentType));
    if (myRequested.compareAndSet(false, true) && !myDisposed) {
      myAlarm.addRequest(this::flush, FLUSH_TIMEOUT);
    }
  }

  private void flush() {
    if (myDisposed) return;
    myRequested.set(false);
    List<Pair<String, ConsoleViewContentType>> textChunks = new ArrayList<>(myTextChunks.size());
    myTextChunks.drainTo(textChunks);
    if (!textChunks.isEmpty()) {
      fireTextAdded(textChunks);
    }
  }

  private void fireTextAdded(@NotNull List<? extends Pair<String, ConsoleViewContentType>> textContentTypes) {
    for (ObservableConsoleView.ChangeListener listener : myChangeListeners) {
      for (Pair<String, ConsoleViewContentType> pair : textContentTypes) {
        listener.textAdded(pair.first, pair.second);
      }
    }
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myAlarm.cancelAllRequests();
  }
}
