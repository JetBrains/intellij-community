/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Provides access to core application-wide functionality and methods for working with the IDEA
 * thread model. The thread model defines two main types of actions which can access the PSI and other
 * IDEA data structures: read actions (which do not modify the data) and write actions (which modify
 * some data).<p>
 * You can call methods requiring read access from the Swing event-dispatch thread without using
 * {@link #runReadAction} method. If you need to invoke such methods from another thread you have to use
 * {@link #runReadAction}. Multiple read actions can run at the same time without locking each other.
 * <p>
 * Write actions can be called only from the Swing thread using {@link #runWriteAction} method.
 * If there are read actions running at this moment <code>runWriteAction</code> is blocked until they are completed.
 */
public interface Application extends ComponentManager {
  /**
   * Runs the specified read action. Can be called from any thread. The action is executed immediately
   * if no write action is currently running, or blocked until the currently running write action completes.
   *
   * @param action the action to run.
   */
  void runReadAction(@NotNull Runnable action);

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   */
  <T> T runReadAction(@NotNull Computable<T> computation);

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @exception E re-frown from ThrowableComputable
   */
  <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E;

  /**
   * Runs the specified write action. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no read actions are currently running, or blocked until all read actions complete.
   *
   * @param action the action to run
   */
  void runWriteAction(@NotNull Runnable action);

  /**
   * Runs the specified computation in a write action. Must be called from the Swing dispatch thread.
   * The action is executed immediately if no read actions or write actions are currently running,
   * or blocked until all read actions and write actions complete.
   *
   * @param computation the computation to run
   * @return the result returned by the computation.
   */
  <T> T runWriteAction(@NotNull Computable<T> computation);

  /**
   * Runs the specified computation in a write action. Must be called from the Swing dispatch thread.
   * The action is executed immediately if no read actions or write actions are currently running,
   * or blocked until all read actions and write actions complete.
   *
   * @param computation the computation to run
   * @return the result returned by the computation.
   * @exception E re-frown from ThrowableComputable
   */
  <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E;

  /**
   * Returns true if there is currently executing write action of the specified class.
   *
   * @param actionClass the class of the write action to return.
   * @return true if the action is running, or false if no action of the specified class is currently executing.
   */
  boolean hasWriteAction(@Nullable Class<?> actionClass);

  /**
   * Asserts whether the read access is allowed.
   */
  void assertReadAccessAllowed();

  /**
   * Asserts whether the write access is allowed.
   */
  void assertWriteAccessAllowed();

  /**
   * Asserts whether the method is being called from the event dispatch thread.
   */
  void assertIsDispatchThread();

  /**
   * Adds an {@link ApplicationListener}.
   *
   * @param listener the listener to add
   */
  void addApplicationListener(@NotNull ApplicationListener listener);

  /**
   * Adds an {@link ApplicationListener}.
   *
   * @param listener the listener to add
   * @param parent the parent disposable which dispose will trigger this listener removal
   */
  void addApplicationListener(@NotNull ApplicationListener listener, @NotNull Disposable parent);

  /**
   * Removes an {@link ApplicationListener}.
   *
   * @param listener the listener to remove
   */
  void removeApplicationListener(@NotNull ApplicationListener listener);

  /**
   * Saves all open documents and projects.
   */
  void saveAll();

  /**
   * Saves all application settings.
   */
  void saveSettings();

  /**
   * Exits the application, showing the exit confirmation prompt if it is enabled.
   */
  void exit();

  /**
   * Checks if the write access is currently allowed.
   *
   * @return true if the write access is currently allowed, false otherwise.
   * @see #assertWriteAccessAllowed()
   * @see #runWriteAction(Runnable)
   */
  boolean isWriteAccessAllowed();

  /**
   * Checks if the read access is currently allowed.
   *
   * @return true if the read access is currently allowed, false otherwise.
   * @see #assertReadAccessAllowed()
   * @see #runReadAction(Runnable)
   */
  boolean isReadAccessAllowed();

  /**
   * Checks if the current thread is the Swing dispatch thread.
   *
   * @return true if the current thread is the Swing dispatch thread, false otherwise.
   */
  boolean isDispatchThread();

  /**
   * @return a facade, which lets to call all those invokeLater() with a ActionCallback handle returned.
   */
  @NotNull
  ModalityInvokator getInvokator();

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread. This will happen after all
   * pending AWT events have been processed.
   *
   * @param runnable the runnable to execute.
   */
  void invokeLater(@NotNull Runnable runnable);

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread - unless the expiration condition is fulfilled.
   * This will happen after all pending AWT events have been processed.
   *
   * @param runnable the runnable to execute.
   * @param expired  condition to check before execution.
   */
  void invokeLater(@NotNull Runnable runnable, @NotNull Condition expired);

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread, when IDEA is in the specified modality
   * state.
   *
   * @param runnable the runnable to execute.
   * @param state the state in which the runnable will be executed.
   */
  void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state);

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread, when IDEA is in the specified modality
   * state - unless the expiration condition is fulfilled.
   * This will happen after all pending AWT events have been processed.
   *
   * @param runnable the runnable to execute.
   * @param state the state in which the runnable will be executed.
   * @param expired  condition to check before execution.
   */
  void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition expired);

  /**
   * <p>Causes {@code runnable.run()} to be executed synchronously on the
   * AWT event dispatching thread, when IDEA is in the specified modality
   * state. This call blocks until all pending AWT events have been processed and (then)
   * {@code runnable.run()} returns.</p>
   *
   * <p>If current thread is an event dispatch thread then {@code runnable.run()}
   * is executed immediately.</p>
   *
   * @param runnable the runnable to execute.
   * @param modalityState the state in which the runnable will be executed.
   */
  void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState);

  /**
   * Returns the current modality state for the Swing dispatch thread.
   *
   * @return the current modality state.
   */
  @NotNull
  ModalityState getCurrentModalityState();

  /**
   * Returns the modality state for the dialog to which the specified component belongs.
   *
   * @param c the component for which the modality state is requested.
   * @return the modality state.
   */
  @NotNull
  ModalityState getModalityStateForComponent(@NotNull Component c);

  /**
   * Returns the current modality state for the current thread (which may be different
   * from the Swing dispatch thread).
   *
   * @return the modality state for the current thread.
   */
  @NotNull
  ModalityState getDefaultModalityState();

  /**
   * Returns the modality state representing the state when no modal dialogs
   * are active.
   *
   * @return the modality state for no modal dialogs.
   */
  @NotNull
  ModalityState getNoneModalityState();

  /**
   * Returns modality state which is active anytime
   * @return modality state
   */
  ModalityState getAnyModalityState();

  /**
   * Returns the time of IDEA start, in milliseconds since midnight, January 1, 1970 UTC.
   *
   * @return the IDEA start time.
   */
  long getStartTime();

  /**
   * Returns the time in milliseconds during which IDEA received no input events.
   *
   * @return the idle time of IDEA.
   */
  long getIdleTime();

  /**
   * Checks if IDEA is currently running unit tests. No UI should be shown when unit
   * tests are being executed.
   *
   * @return true if IDEA is running unit tests, false otherwise
   */
  boolean isUnitTestMode();

  /**
   * Checks if IDEA is running as a command line applet or in unit test mode.
   * No UI should be shown when IDEA is running in this mode.
   *
   * @return true if IDEA is running in UI-less mode, false otherwise
   */
  boolean isHeadlessEnvironment();

  /**
   * Checks if IDEA is running as a command line applet or in unit test mode.
   * UI can be shown (e.g. diff frame)
   *
   * @return true if IDEA is running in command line  mode, false otherwise
   */
  boolean isCommandLine();

  @Override
  boolean isDisposed();

  /**
   * Requests pooled thread to execute the action
   * @param action to be executed
   * @return future result
   */
  @NotNull
  Future<?> executeOnPooledThread(@NotNull Runnable action);

  /**
   * Requests pooled thread to execute the action
   * @param action to be executed
   * @return future result
   */
  @NotNull
  <T> Future<T> executeOnPooledThread(@NotNull Callable<T> action);

  /**
   * @return true if application is currently disposing (but not yet disposed completely)
   */
  boolean isDisposeInProgress();

  /**
   * Checks if IDEA is capable of restarting itself on the current platform and with the current execution mode.
   *
   * @return true if IDEA can restart itself, false otherwise.
   * @since 8.1
   */
  boolean isRestartCapable();

  /**
   * Exits and restarts IDEA. If the current platform is not restart capable, only exits.
   *
   * @since 8.1
   */
  void restart();

  /**
   * Checks if the application is active
   * @return true if one of application windows is focused, false -- otherwise
   * @since 9.0
   */
  boolean isActive();

  /**
   * Returns lock used for read operations, should be closed in finally block
   */
  @NotNull
  AccessToken acquireReadActionLock();

  /**
   * Returns lock used for write operations, should be closed in finally block
   */
  @NotNull
  AccessToken acquireWriteActionLock(@Nullable Class marker);

  boolean isInternal();

  boolean isEAP();
}
