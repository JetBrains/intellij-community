// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Provides access to core application-wide functionality and methods for working with the IDE's thread model.
 * <p>
 * The thread model defines three types of locks which provide access the PSI and other IDE data structures:
 * <ul>
 *   <li><b>Read lock</b> provides read access to the data. Can be obtained from any thread concurrently with other Read locks
 *   and Write Intent lock.</li>
 *   <li><b>Write Intent lock</b> provides read access to the data and the ability to acquire Write lock. Can be obtained from
 *   any thread concurrently with Read locks, but cannot be acquired if another Write Intent lock is held on another thread.</li>
 *   <li><b>Write lock</b> provides read and write access to the data. Can only be obtained from under Write Intent lock.
 *   Cannot be acquired if a Read lock is held on another thread.</li>
 * </ul>
 * <p>
 * The compatibility matrix for these locks is reflected below.
 * <table>
 *   <tr><th style="width: 20px;"></th><th style="width: 15px;">R</th><th style="width: 15px;">IW</th><th style="width: 15px;">W</th></tr>
 *   <tr><th>R</th><td>+</td><td>+</td><td>-</td></tr>
 *   <tr><th>IW</th><td>+</td><td>-</td><td>-</td></tr>
 *   <tr><th>W</th><td>-</td><td>-</td><td>-</td></tr>
 * </table>
 * <p>
 * Obtaining locks manually is not recommended. The recommended way to obtain read and write locks is to run so-called
 * "read actions" and write-actions via {@link #runReadAction} and {@link #runWriteAction}, respectively.
 * <p>
 * The recommended way to obtain Write Intent lock is to schedule execution on so-called "write thread" (i.e. thread with Write Intent lock)
 * via {@link #invokeLaterOnWriteThread} or {@link AppUIExecutor#onWriteThread} asynchronous API.
 * <p>
 * Multiple read actions can run at the same time without locking each other.
 * <p>
 * If there are read actions running at this moment {@code runWriteAction} is blocked until they are completed.
 * <p>
 * See also <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html">General Threading Rules</a>.
 */
public interface Application extends ComponentManager {
  /**
   * Causes {@code runnable} to be executed asynchronously under Write Intent lock on some thread,
   * with {@link ModalityState#defaultModalityState()} modality state.
   *
   * @param action the runnable to execute.
   */
  @ApiStatus.Experimental
  void invokeLaterOnWriteThread(@NotNull Runnable action);

  /**
   * Causes {@code runnable} to be executed asynchronously under Write Intent lock on some thread,
   * when IDE is in the specified modality state (or a state with less modal dialogs open).
   *
   * @param action the runnable to execute.
   * @param modal  the state in which action will be executed
   */
  @ApiStatus.Experimental
  void invokeLaterOnWriteThread(@NotNull Runnable action, @NotNull ModalityState modal);

  /**
   * Causes {@code runnable} to be executed asynchronously under Write Intent lock on some thread,
   * when IDE is in the specified modality state (or a state with less modal dialogs open)
   * - unless the expiration condition is fulfilled.
   *
   * @param action  the runnable to execute.
   * @param modal   the state in which action will be executed
   * @param expired condition to check before execution.
   */
  @ApiStatus.Experimental
  void invokeLaterOnWriteThread(@NotNull Runnable action, @NotNull ModalityState modal, @NotNull Condition<?> expired);

  /**
   * Runs the specified read action. Can be called from any thread. The action is executed immediately
   * if no write action is currently running, or blocked until the currently running write action completes.
   * <p>
   * See also {@link ReadAction#run} for a more lambda-friendly version.
   *
   * @param action the action to run.
   */
  void runReadAction(@NotNull Runnable action);

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   * <p>
   * See also {@link ReadAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  <T> T runReadAction(@NotNull Computable<T> computation);

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   * <p>
   * See also {@link ReadAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E;

  /**
   * Runs the specified write action. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no read actions are currently running, or blocked until all read actions complete.
   * <p>
   * See also {@link WriteAction#run} for a more lambda-friendly version.
   *
   * @param action the action to run
   */
  void runWriteAction(@NotNull Runnable action);

  /**
   * Runs the specified computation in a write-action. Must be called from the Swing dispatch thread.
   * The action is executed immediately if no read actions or write actions are currently running,
   * or blocked until all read actions and write actions complete.
   * <p>
   * See also {@link WriteAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to run
   * @return the result returned by the computation.
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  <T> T runWriteAction(@NotNull Computable<T> computation);

  /**
   * Runs the specified computation in a write-action. Must be called from the Swing dispatch thread.
   * The action is executed immediately if no read actions or write actions are currently running,
   * or blocked until all read actions and write actions complete.
   * <p>
   * See also {@link WriteAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to run
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E;

  /**
   * Returns {@code true} if there is currently executing write action of the specified class.
   *
   * @param actionClass the class of the write action to return.
   * @return {@code true} if the action is running, or {@code false} if no action of the specified class is currently executing.
   */
  boolean hasWriteAction(@NotNull Class<?> actionClass);

  /**
   * Asserts whether read access is allowed.
   */
  void assertReadAccessAllowed();

  /**
   * Asserts whether write access is allowed.
   */
  void assertWriteAccessAllowed();

  /**
   * Asserts whether read access is not allowed.
   */
  @ApiStatus.Experimental
  void assertReadAccessNotAllowed();

  /**
   * Asserts whether the method is being called from the event dispatch thread.
   */
  void assertIsDispatchThread();

  /**
   * Asserts whether the method is being called from any thread outside EDT.
   */
  @ApiStatus.Experimental
  void assertIsNonDispatchThread();

  /**
   * Asserts whether the method is being called from under the write-intent lock.
   */
  @ApiStatus.Experimental
  void assertIsWriteThread();

  /** @deprecated Use {@link #addApplicationListener(ApplicationListener, Disposable)} instead */
  @Deprecated
  void addApplicationListener(@NotNull ApplicationListener listener);

  /**
   * Adds an {@link ApplicationListener}.
   *
   * @param listener the listener to add
   * @param parent   the parent disposable which dispose will trigger this listener removal
   */
  void addApplicationListener(@NotNull ApplicationListener listener, @NotNull Disposable parent);

  /** @deprecated call {@code Disposer.dispose(disposable);} on disposable passed to {@link #addApplicationListener(ApplicationListener, Disposable)} */
  @Deprecated
  void removeApplicationListener(@NotNull ApplicationListener listener);

  /**
   * Saves all open documents, settings of all open projects, and application settings.
   *
   * @see #saveSettings()
   */
  void saveAll();

  /**
   * Saves application settings. Note that settings for non-roamable components aren't saved by default if they were saved less than
   * 5 minutes ago, see {@link com.intellij.openapi.components.Storage#useSaveThreshold() useSaveThreshold} for details.
   */
  void saveSettings();

  /**
   * Exits the application, showing the exit confirmation prompt if it is enabled.
   */
  void exit();

  default void exit(boolean force, boolean exitConfirmed, boolean restart) {
    exit();
  }

  /**
   * Checks if the write access is currently allowed.
   *
   * @return {@code true} if the write access is currently allowed, {@code false} otherwise.
   * @see #assertWriteAccessAllowed()
   * @see #runWriteAction(Runnable)
   */
  @Contract(pure = true)
  boolean isWriteAccessAllowed();

  /**
   * Checks if the read access is currently allowed.
   *
   * @return {@code true} if the read access is currently allowed, {@code false} otherwise.
   * @see #assertReadAccessAllowed()
   * @see #runReadAction(Runnable)
   */
  @Contract(pure = true)
  boolean isReadAccessAllowed();

  /**
   * Checks if the current thread is the event dispatch thread and has IW lock acquired.
   *
   * @see #isWriteThread()
   * @return {@code true} if the current thread is the Swing dispatch thread with IW lock, {@code false} otherwise.
   */
  @Contract(pure = true)
  boolean isDispatchThread();

  /**
   * Checks if the current thread has IW lock acquired, which grants read access and the ability to run write actions.
   *
   * @return {@code true} if the current thread has IW lock acquired, {@code false} otherwise.
   */
  @ApiStatus.Experimental
  @Contract(pure = true)
  boolean isWriteThread();

  /**
   * @return a facade, which lets to call all those `invokeLater()` with an `ActionCallback` handle returned.
   * @deprecated use corresponding {@link Application#invokeLater} methods
   */
  @Deprecated
  @NotNull ModalityInvokator getInvokator();

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread under Write Intent lock, with {@link ModalityState#defaultModalityState()} modality state.
   * This will happen after all pending AWT events have been processed.
   * <p>
   * Please use this method instead of {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or {@link com.intellij.util.ui.UIUtil} methods
   * for the reasons described in {@link ModalityState} documentation.
   *
   * @param runnable the runnable to execute.
   */
  void invokeLater(@NotNull Runnable runnable);

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread under Write Intent lock - unless the expiration condition is fulfilled.
   * This will happen after all pending AWT events have been processed and in {@link ModalityState#defaultModalityState()} modality state
   * (or a state with less modal dialogs open).
   * <p>
   * Please use this method instead of {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or {@link com.intellij.util.ui.UIUtil} methods
   * for the reasons described in {@link ModalityState} documentation.
   *
   * @param runnable the runnable to execute.
   * @param expired  condition to check before execution.
   */
  void invokeLater(@NotNull Runnable runnable, @NotNull Condition<?> expired);

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread under Write Intent lock, when IDE is in the specified modality
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
   * AWT event dispatching thread under Write Intent lock, when IDE is in the specified modality
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
  void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition<?> expired);

  /**
   * <p>Causes {@code runnable.run()} to be executed synchronously on the
   * AWT event dispatching thread under Write Intent lock, when the IDE is in the specified modality
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
  @NotNull ModalityState getCurrentModalityState();

  /**
   * Please use {@link ModalityState#stateForComponent(Component)} instead.
   *
   * @return the modality state for the dialog to which the specified component belongs.
   */
  @NotNull ModalityState getModalityStateForComponent(@NotNull Component c);

  /**
   * Please use {@link ModalityState#defaultModalityState()} instead.
   *
   * @return the modality state for the current thread.
   */
  @NotNull ModalityState getDefaultModalityState();

  /**
   * Please use {@link ModalityState#NON_MODAL} instead.
   *
   * @return the modality state for no modal dialogs.
   */
  @NotNull ModalityState getNoneModalityState();

  /**
   * Please use {@link ModalityState#any()} instead, and only if you absolutely must, after carefully reading its documentation.
   *
   * @return modality state which is always applicable
   */
  @NotNull ModalityState getAnyModalityState();

  /**
   * Returns the time of IDE start, in milliseconds since midnight, January 1, 1970 (UTC).
   */
  long getStartTime();

  /**
   * Returns the time in milliseconds during which IDE received no input events.
   */
  long getIdleTime();

  /**
   * Checks if IDE is currently running unit tests. No UI should be shown when unit
   * tests are being executed.
   * This method may also be used for additional debug checks or logging in test mode.
   * 
   * Please avoid doing things differently depending on the result of this method, because this leads to
   * testing something synthetic instead of what really happens in production.
   * So you'll be able to catch less of production bugs and might instead lose your time on debugging test-only issues.
   * It's more robust to write a bit more code in tests to accommodate for production behavior than vice versa.
   * For example:
   * <ul>
   *   <li>To wait for an {@code invokeLater} in tests, you can call {@code PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()}</li>
   *   <li>To wait for asynchronous non-blocking read operations, you can call {@code NonBlockingReadActionImpl.waitForAsyncTaskCompletion()}</li>
   *   <li>To wait for other asynchronous operations, you can track their {@link Future}/{@link org.jetbrains.concurrency.Promise Promise}/etc
   *       callbacks and call {@code PlatformTestUtil.waitFor*}</li>
   *   <li>To emulate user interaction with a simple yes/no dialog, use {@code TestDialogManager.setTestDialog(...)}</li>
   *   <li>For other UI interaction, try {@link com.intellij.ui.UiInterceptors UiInterceptors}</li>
   * </ul>
   *
   * @return {@code true} if IDE is running unit tests, {@code false} otherwise
   */
  boolean isUnitTestMode();

  /**
   * Checks if IDE is running as a command line applet or in unit test mode.
   * No UI should be shown when IDE is running in this mode.
   *
   * @return {@code true} if IDE is running in UI-less mode, {@code false} otherwise
   */
  boolean isHeadlessEnvironment();

  /**
   * Checks if IDE is running as a command line applet or in unit test mode.
   * UI can be shown (e.g. diff frame)
   *
   * @return {@code true} if IDE is running in command line  mode, {@code false} otherwise
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
  @NotNull Future<?> executeOnPooledThread(@NotNull Runnable action);

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
  @NotNull <T> Future<T> executeOnPooledThread(@NotNull Callable<T> action);

  /** @deprecated use {@link #isDisposed()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  default boolean isDisposeInProgress() {
    return isDisposed();
  }

  /**
   * Checks if IDE is capable of restarting itself on the current platform and with the current execution mode.
   *
   * @return {@code true} if IDE can restart itself, {@code false} otherwise.
   */
  boolean isRestartCapable();

  /**
   * Exits and restarts IDE. If the current platform is not restart capable, only exits.
   */
  void restart();

  /**
   * Checks if the application is active.
   *
   * @return {@code true} if one of application windows is focused, {@code false} otherwise
   */
  boolean isActive();

  /** @deprecated use {@link #runReadAction(Runnable)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  @NotNull AccessToken acquireReadActionLock();

  /** @deprecated use {@link #runWriteAction}, {@link WriteAction#run(ThrowableRunnable)}, or {@link WriteAction#compute} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  @NotNull AccessToken acquireWriteActionLock(@NotNull Class<?> marker);

  /**
   * Checks if IDE is running in
   * <a href="http://www.jetbrains.org/intellij/sdk/docs/reference_guide/internal_actions/enabling_internal.html">Internal Mode</a>
   * to enable additional features for plugin development.
   */
  boolean isInternal();

  boolean isEAP();
}
