// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Provides access to core application-wide functionality and methods for working with the IDE's
 * thread model.
 * <p>
 * The thread model defines two main types of actions which can access the PSI and other
 * IDE data structures: read actions (which do not modify the data) and write actions (which modify
 * some data).
 * <p>
 * You can call methods requiring read access from the Swing event-dispatch thread without using
 * {@link #runReadAction} method. If you need to invoke such methods from another thread you have to use
 * {@link #runReadAction}. Multiple read actions can run at the same time without locking each other.
 * <p>
 * Write actions can be called only from the Swing thread using {@link #runWriteAction} method.
 * If there are read actions running at this moment {@code runWriteAction} is blocked until they are completed.
 * <p>
 * See also <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html">General Threading Rules</a>.
 */
public interface Application extends ComponentManager {

  /**
   * Runs the specified read action. Can be called from any thread. The action is executed immediately
   * if no write action is currently running, or blocked until the currently running write action completes.<p></p>
   * <p>
   * See also {@link ReadAction#run} for a more lambda-friendly version.
   *
   * @param action the action to run.
   */
  void runReadAction(@NotNull Runnable action);

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.<p></p>
   * <p>
   * See also {@link ReadAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   */
  <T> T runReadAction(@NotNull Computable<T> computation);

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.<p></p>
   * <p>
   * See also {@link ReadAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   */
  <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E;

  /**
   * Runs the specified write action. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no read actions are currently running, or blocked until all read actions complete.<p></p>
   * <p>
   * See also {@link WriteAction#run} for a more lambda-friendly version.
   *
   * @param action the action to run
   */
  void runWriteAction(@NotNull Runnable action);

  /**
   * Runs the specified computation in a write action. Must be called from the Swing dispatch thread.
   * The action is executed immediately if no read actions or write actions are currently running,
   * or blocked until all read actions and write actions complete.<p></p>
   * <p>
   * See also {@link WriteAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to run
   * @return the result returned by the computation.
   */
  <T> T runWriteAction(@NotNull Computable<T> computation);

  /**
   * Runs the specified computation in a write action. Must be called from the Swing dispatch thread.
   * The action is executed immediately if no read actions or write actions are currently running,
   * or blocked until all read actions and write actions complete.<p></p>
   * <p>
   * See also {@link WriteAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to run
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   */
  <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E;

  /**
   * Returns true if there is currently executing write action of the specified class.
   *
   * @param actionClass the class of the write action to return.
   * @return true if the action is running, or false if no action of the specified class is currently executing.
   */
  boolean hasWriteAction(@NotNull Class<?> actionClass);

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
   * @deprecated Use {@link #addApplicationListener(ApplicationListener, Disposable)} instead
   * Adds an {@link ApplicationListener}.
   *
   * @param listener the listener to add
   */
  @Deprecated
  void addApplicationListener(@NotNull ApplicationListener listener);

  /**
   * Adds an {@link ApplicationListener}.
   *
   * @param listener the listener to add
   * @param parent   the parent disposable which dispose will trigger this listener removal
   */
  void addApplicationListener(@NotNull ApplicationListener listener, @NotNull Disposable parent);

  /**
   * @deprecated Instead, just call {@code Disposer.dispose(disposable);} on disposable you've passed to {@link #addApplicationListener(ApplicationListener, Disposable)}
   * Removes an {@link ApplicationListener}.
   *
   * @param listener the listener to remove
   */
  @Deprecated
  void removeApplicationListener(@NotNull ApplicationListener listener);

  /**
   * Saves all open documents and projects.
   */
  void saveAll();

  /**
   * Saves application settings.
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
  @Contract(pure = true)
  boolean isWriteAccessAllowed();

  /**
   * Checks if the read access is currently allowed.
   *
   * @return true if the read access is currently allowed, false otherwise.
   * @see #assertReadAccessAllowed()
   * @see #runReadAction(Runnable)
   */
  @Contract(pure = true)
  boolean isReadAccessAllowed();

  /**
   * Checks if the current thread is the Swing dispatch thread.
   *
   * @return true if the current thread is the Swing dispatch thread, false otherwise.
   */
  @Contract(pure = true)
  boolean isDispatchThread();

  /**
   * @return a facade, which lets to call all those invokeLater() with a ActionCallback handle returned.
   */
  @NotNull
  ModalityInvokator getInvokator();

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread, with {@link ModalityState#defaultModalityState()} modality state. This will happen after all
   * pending AWT events have been processed.<p/>
   * <p>
   * Please use this method instead of {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or {@link com.intellij.util.ui.UIUtil} methods
   * for the reasons described in {@link ModalityState} documentation.
   *
   * @param runnable the runnable to execute.
   */
  void invokeLater(@NotNull Runnable runnable);

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread - unless the expiration condition is fulfilled.
   * This will happen after all pending AWT events have been processed and in {@link ModalityState#defaultModalityState()} modality state
   * (or a state with less modal dialogs open).<p/>
   * <p>
   * Please use this method instead of {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or {@link com.intellij.util.ui.UIUtil} methods
   * for the reasons described in {@link ModalityState} documentation.
   *
   * @param runnable the runnable to execute.
   * @param expired  condition to check before execution.
   */
  void invokeLater(@NotNull Runnable runnable, @NotNull Condition expired);

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread, when IDEA is in the specified modality
   * state (or a state with less modal dialogs open).
   * <p>
   * Please use this method instead of {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or {@link com.intellij.util.ui.UIUtil} methods
   * for the reasons described in {@link ModalityState} documentation.
   *
   * @param runnable the runnable to execute.
   * @param state    the state in which the runnable will be executed.
   */
  void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state);

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread, when IDEA is in the specified modality
   * state(or a state with less modal dialogs open) - unless the expiration condition is fulfilled.
   * This will happen after all pending AWT events have been processed.
   * <p>
   * Please use this method instead of {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or {@link com.intellij.util.ui.UIUtil} methods
   * for the reasons described in {@link ModalityState} documentation.
   *
   * @param runnable the runnable to execute.
   * @param state    the state in which the runnable will be executed.
   * @param expired  condition to check before execution.
   */
  void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition expired);

  /**
   * <p>Causes {@code runnable.run()} to be executed synchronously on the
   * AWT event dispatching thread, when the IDE is in the specified modality
   * state (or a state with less modal dialogs open). This call blocks until all pending AWT events have been processed and (then)
   * {@code runnable.run()} returns.</p>
   *
   * <p>If current thread is an event dispatch thread then {@code runnable.run()}
   * is executed immediately regardless of the modality state.</p>
   * <p>
   * Please use this method instead of {@link javax.swing.SwingUtilities#invokeAndWait(Runnable)} or {@link com.intellij.util.ui.UIUtil} methods
   * for the reasons described in {@link ModalityState} documentation.
   *
   * @param runnable      the runnable to execute.
   * @param modalityState the state in which the runnable will be executed.
   * @throws ProcessCanceledException when the current thread is interrupted
   */
  void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) throws ProcessCanceledException;

  /**
   * Same as {@link #invokeAndWait(Runnable, ModalityState)}, using {@link ModalityState#defaultModalityState()}.
   */
  void invokeAndWait(@NotNull Runnable runnable) throws ProcessCanceledException;

  /**
   * Please use {@link ModalityState#current()} instead.
   *
   * @return the current modality state.
   */
  @NotNull
  ModalityState getCurrentModalityState();

  /**
   * Please use {@link ModalityState#stateForComponent(Component)} instead.
   *
   * @return the modality state for the dialog to which the specified component belongs.
   */
  @NotNull
  ModalityState getModalityStateForComponent(@NotNull Component c);

  /**
   * Please use {@link ModalityState#defaultModalityState()} instead.
   *
   * @return the modality state for the current thread.
   */
  @NotNull
  ModalityState getDefaultModalityState();

  /**
   * Please use {@link ModalityState#NON_MODAL} instead.
   *
   * @return the modality state for no modal dialogs.
   */
  @NotNull
  ModalityState getNoneModalityState();

  /**
   * Please use {@link ModalityState#any()} instead, and only if you absolutely must, after carefully reading its documentation.
   *
   * @return modality state which is always applicable
   */
  @NotNull
  ModalityState getAnyModalityState();

  /**
   * Returns the time of IDE start, in milliseconds since midnight, January 1, 1970 UTC.
   *
   * @return the IDE's start time.
   */
  long getStartTime();

  /**
   * Returns the time in milliseconds during which IDE received no input events.
   *
   * @return the idle time of IDE.
   */
  long getIdleTime();

  /**
   * Checks if IDE is currently running unit tests. No UI should be shown when unit
   * tests are being executed.
   *
   * @return true if IDEA is running unit tests, false otherwise
   */
  boolean isUnitTestMode();

  /**
   * Checks if IDE is running as a command line applet or in unit test mode.
   * No UI should be shown when IDE is running in this mode.
   *
   * @return true if IDE is running in UI-less mode, false otherwise
   */
  boolean isHeadlessEnvironment();

  /**
   * Checks if IDE is running as a command line applet or in unit test mode.
   * UI can be shown (e.g. diff frame)
   *
   * @return true if IDE is running in command line  mode, false otherwise
   */
  boolean isCommandLine();

  /**
   * Requests pooled thread to execute the action.
   * <p>
   * This pool is an
   * <ul>
   * <li>Unbounded.</li>
   * <li>Application-wide, always active, non-shutdownable singleton.</li>
   * </ul>
   * You can use this pool for long-running and/or IO-bound tasks.
   *
   * @param action to be executed
   * @return future result
   */
  @NotNull
  Future<?> executeOnPooledThread(@NotNull Runnable action);

  /**
   * Requests pooled thread to execute the action.
   * <p>
   * This pool is an
   * <ul>
   * <li>Unbounded.</li>
   * <li>Application-wide, always active, non-shutdownable singleton.</li>
   * </ul>
   * You can use this pool for long-running and/or IO-bound tasks.
   *
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
   * Checks if IDE is capable of restarting itself on the current platform and with the current execution mode.
   *
   * @return true if IDE can restart itself, false otherwise.
   */
  boolean isRestartCapable();

  /**
   * Exits and restarts IDE. If the current platform is not restart capable, only exits.
   */
  void restart();

  /**
   * Checks if the application is active.
   *
   * @return true if one of application windows is focused, false -- otherwise
   */
  boolean isActive();

  /**
   * @deprecated Use {@link #runReadAction(Runnable)} instead
   */
  @NotNull
  @Deprecated
  AccessToken acquireReadActionLock();

  /**
   * @deprecated use {@link #runWriteAction}, {@link WriteAction#run(ThrowableRunnable)} or {@link WriteAction#compute(ThrowableComputable)} instead
   */
  @NotNull
  @Deprecated
  AccessToken acquireWriteActionLock(@NotNull Class marker);

  boolean isInternal();

  boolean isEAP();
}
