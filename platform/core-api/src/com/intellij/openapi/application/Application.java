// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.*;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Provides access to the core application-wide functionality and methods for working with the IDE thread model.
 * <p>
 * The thread model defines three types of locks which provide access to the PSI and other IDE data structures:
 * <ul>
 *   <li><b>Read lock</b> provides read access to the data. Can be acquired from any thread concurrently with other Read locks
 *   and Write Intent lock.</li>
 *   <li><b>Write Intent lock</b> provides read access to the data and the ability to acquire Write lock. Can be acquired from
 *   any thread concurrently with Read locks, but cannot be acquired if another Write Intent lock is held on another thread.</li>
 *   <li><b>Write lock</b> provides read and write access to the data. Can only be acquired from under Write Intent lock.
 *   Cannot be acquired if a Read lock is held on another thread.</li>
 * </ul>
 * <p>
 * The compatibility matrix for these locks is reflected below.
 * <table>
 *   <tr><th style="width: 20px;"></th><th style="width: 20px;">R</th><th style="width: 20px;">IW</th><th style="width: 20px;">W</th></tr>
 *   <tr><th>R</th><td>+</td><td>+</td><td>-</td></tr>
 *   <tr><th>IW</th><td>+</td><td>-</td><td>-</td></tr>
 *   <tr><th>W</th><td>-</td><td>-</td><td>-</td></tr>
 * </table>
 * <p>
 * Acquiring locks manually is not recommended.
 * The recommended way to acquire read and write locks is to run so-called "read actions" and "write actions"
 * via {@link #runReadAction} and {@link #runWriteAction}, respectively.
 * <p>
 * The recommended way to acquire Write Intent lock is to schedule execution on so-called "write thread" (i.e. thread with Write Intent lock)
 * via {@link #invokeLaterOnWriteThread} or {@link AppUIExecutor#onWriteThread} asynchronous API.
 * <p>
 * Multiple read actions can run at the same time without locking each other.
 * <p>
 * If there are read actions running at this moment {@code runWriteAction} is blocked until they are completed.
 * <p>
 * See also <a href="https://plugins.jetbrains.com/docs/intellij/threading-model.html">Threading Model</a>.
 */
public interface Application extends ComponentManager {

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * This method is obsolete because there will be no such thing as <i>write thread</i>,
   * and any thread will be able to hold the write lock.
   * For more info follow <a href="https://youtrack.jetbrains.com/issue/IJPL-53">IJPL-53</a>.
   * </p>
   */
  @ApiStatus.Obsolete
  default void invokeLaterOnWriteThread(@NotNull Runnable action) {
    invokeLater(action, getDefaultModalityState());
  }

  /**
   * See <b>obsolescence notice</b> on {@link #invokeLaterOnWriteThread(Runnable)}.
   * <hr>
   *
   * <p>
   * Causes {@code runnable} to be executed asynchronously under Write Intent lock on some thread,
   * when IDE is in the specified modality state (or a state with less modal dialogs open).
   *
   * @param action the runnable to execute.
   * @param modal  the state in which action will be executed
   */
  @ApiStatus.Obsolete
  default void invokeLaterOnWriteThread(@NotNull Runnable action, @NotNull ModalityState modal) {
    invokeLater(action, modal, getDisposed());
  }

  /**
   * See <b>obsolescence notice</b> on {@link #invokeLaterOnWriteThread(Runnable)}.
   * <hr>
   *
   * <p>
   * Causes {@code runnable} to be executed asynchronously under Write Intent lock on some thread,
   * when IDE is in the specified modality state (or a state with less modal dialogs open)
   * - unless the expiration condition is fulfilled.
   *
   * @param action  the runnable to execute.
   * @param modal   the state in which action will be executed
   * @param expired condition to check before execution.
   */
  @ApiStatus.Obsolete
  default void invokeLaterOnWriteThread(@NotNull Runnable action, @NotNull ModalityState modal, @NotNull Condition<?> expired) {
    invokeLater(action, modal, expired);
  }

  /**
   * Runs the specified computation in a read action. Can be called from any thread.
   * The action is executed immediately if no write action is currently running or the write action
   * is running on the current thread.
   * Otherwise, the action is blocked until the currently running write action completes.
   * <p>
   * See also {@link ReadAction#run} for a more lambda-friendly version.
   *
   * @param action the action to run.
   * @see CoroutinesKt#readAction
   * @see CoroutinesKt#readActionBlocking
   */
  @RequiresBlockingContext
  void runReadAction(@NotNull Runnable action);

  /**
   * Runs the specified computation in a read action. Can be called from any thread.
   * The computation is executed immediately if no write action is currently running or the write action
   * is running on the current thread.
   * Otherwise, the action is blocked until the currently running write action completes.
   * <p>
   * See also {@link ReadAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @see CoroutinesKt#readAction
   * @see CoroutinesKt#readActionBlocking
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  @RequiresBlockingContext
  <T> T runReadAction(@NotNull Computable<T> computation);

  /**
   * Runs the specified computation in a read action. Can be called from any thread.
   * The computation is executed immediately if no write action is currently running or the write action
   * is running on the current thread.
   * Otherwise, the action is blocked until the currently running write action completes.
   * <p>
   * See also {@link ReadAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   * @see CoroutinesKt#readAction
   * @see CoroutinesKt#readActionBlocking
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  @RequiresBlockingContext
  <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E;

  /**
   * Runs the specified write action. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no read actions are currently running, or blocked until all read actions complete.
   * <p>
   * See also {@link WriteAction#run} for a more lambda-friendly version.
   *
   * @param action the action to run
   * @see CoroutinesKt#writeAction
   */
  @RequiresBlockingContext
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
   * @see CoroutinesKt#writeAction
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  @RequiresBlockingContext
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
   * @see CoroutinesKt#writeAction
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  @RequiresBlockingContext
  <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E;

  /**
   * Returns {@code true} if there is currently executing write action of the specified class.
   *
   * @param actionClass the class of the write action to return.
   * @return {@code true} if the action is running, or {@code false} if no action of the specified class is currently executing.
   */
  boolean hasWriteAction(@NotNull Class<?> actionClass);

  /**
   * Runs the specified computation in a write intent. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   * <p>
   * See also {@link WriteIntentReadAction#compute} for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   */
  @ApiStatus.Experimental
  default <T, E extends Throwable> T runWriteIntentReadAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    assertWriteIntentLockAcquired();
    return computation.compute();
  }

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * This function is obsolete because the threading assertions should not depend on presence of the {@code Application}.
   * Annotate the function with {@link RequiresReadLock} (in Java),
   * or use {@link ThreadingAssertions#assertReadAccess()},
   * or use {@link ThreadingAssertions#softAssertReadAccess} instead.
   * </p>
   * <hr>
   *
   * Asserts that read access is allowed.
   */
  @ApiStatus.Obsolete
  void assertReadAccessAllowed();

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * This function is obsolete because the threading assertions should not depend on presence of the {@code Application}.
   * Annotate the function with {@link RequiresWriteLock} (in Java) or use {@link ThreadingAssertions#assertWriteAccess()} instead.
   * </p>
   * <hr>
   *
   * Asserts that write access is allowed.
   */
  @ApiStatus.Obsolete
  void assertWriteAccessAllowed();

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * This function is obsolete because the threading assertions should not depend on presence of the {@code Application}.
   * Annotate the function with {@link RequiresReadLockAbsence} (in Java) or use {@link ThreadingAssertions#assertNoReadAccess()} instead.
   * </p>
   * <hr>
   *
   * Asserts that read access is not allowed.
   */
  @ApiStatus.Experimental
  @ApiStatus.Obsolete
  void assertReadAccessNotAllowed();

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * This function is obsolete because the threading assertions should not depend on presence of the {@code Application}.
   * Annotate the function with {@link RequiresEdt} (in Java) or use {@link ThreadingAssertions#assertEventDispatchThread()} instead.
   * </p>
   * <hr>
   *
   * Asserts that the method is being called from the event dispatch thread.
   */
  @ApiStatus.Obsolete
  void assertIsDispatchThread();

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * This function is obsolete because the threading assertions should not depend on presence of the {@code Application}.
   * Annotate the function with {@link RequiresBackgroundThread} (in Java) or use {@link ThreadingAssertions#assertBackgroundThread()} instead.
   * </p>
   * <hr>
   *
   * Asserts that the method is being called from any thread outside EDT.
   */
  @ApiStatus.Experimental
  @ApiStatus.Obsolete
  void assertIsNonDispatchThread();

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * This function is obsolete because the threading assertions should not depend on presence of the {@code Application}.
   * Use {@link ThreadingAssertions#assertWriteIntentReadAccess()} instead.
   * </p>
   * <hr>
   *
   * Asserts that the method is being called from under the write-intent lock.
   */
  @ApiStatus.Experimental
  @ApiStatus.Obsolete
  void assertWriteIntentLockAcquired();

  /**
   * Adds an {@link ApplicationListener}.
   *
   * @param listener the listener to add
   * @param parent   the parent disposable, whose disposal will trigger this listener's removal
   */
  void addApplicationListener(@NotNull ApplicationListener listener, @NotNull Disposable parent);

  /**
   * Saves all open documents, settings of all open projects, and application settings.
   *
   * @see #saveSettings()
   * @deprecated Use {@link com.intellij.ide.SaveAndSyncHandler#scheduleSave)}
   */
  @Deprecated
  @ApiStatus.Internal
  @RequiresEdt
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

  default void exit(boolean force, boolean exitConfirmed, boolean restart, int exitCode) {
    exit();
  }

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
   * @return {@code true} if the current thread is the Swing dispatch thread with IW lock, {@code false} otherwise.
   * @see #isWriteIntentLockAcquired()
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
  boolean isWriteIntentLockAcquired();

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
  @RequiresBlockingContext
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
   * @see CoroutinesKt#getEDT
   */
  @RequiresBlockingContext
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
   * @see CoroutinesKt#getEDT
   */
  @RequiresBlockingContext
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
   * @see CoroutinesKt#getEDT
   */
  @RequiresBlockingContext
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
   * @see CoroutinesKt#getEDT
   */
  @RequiresBlockingContext
  void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) throws ProcessCanceledException;

  /**
   * Same as {@link #invokeAndWait(Runnable, ModalityState)}, using {@link ModalityState#defaultModalityState()}.
   * @see CoroutinesKt#getEDT
   */
  @RequiresBlockingContext
  void invokeAndWait(@NotNull Runnable runnable) throws ProcessCanceledException;

  /**
   * Please use {@link ModalityState#current()} instead.
   *
   * @return the current modality state.
   * @deprecated for attention
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @RequiresEdt
  @ApiStatus.Internal
  @NotNull ModalityState getCurrentModalityState();

  /**
   * Please use {@link ModalityState#stateForComponent(Component)} instead.
   *
   * @return the modality state for the dialog to which the specified component belongs.
   */
  @RequiresEdt
  @NotNull ModalityState getModalityStateForComponent(@NotNull Component c);

  /**
   * Please use {@link ModalityState#defaultModalityState()} instead.
   *
   * @return the modality state for the current thread.
   */
  @RequiresBlockingContext
  @NotNull ModalityState getDefaultModalityState();

  /**
   * Please use {@link ModalityState#nonModal()} instead.
   *
   * @return the modality state for no modal dialogs.
   * @deprecated for attention
   */
  @Deprecated
  @ApiStatus.Internal
  @NotNull ModalityState getNoneModalityState();

  /**
   * Please use {@link ModalityState#any()} instead, and only if you absolutely must, after carefully reading its documentation.
   *
   * @return modality state which is always applicable
   * @deprecated for attention
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @ApiStatus.Internal
  @NotNull ModalityState getAnyModalityState();

  /**
   * Returns the IDE start timestamp (UNIX time, in milliseconds).
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
   * <p>
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
   * @return {@code true} if IDE is running in command line mode, {@code false} otherwise
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
  @RequiresBlockingContext
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
  @RequiresBlockingContext
  @NotNull <T> Future<T> executeOnPooledThread(@NotNull Callable<T> action);

  /**
   * Checks if IDE is capable of restarting itself on the current platform and with the current execution mode.
   *
   * @return {@code true} if IDE can restart itself, {@code false} otherwise.
   */
  boolean isRestartCapable();

  /**
   * Exits and restarts IDE. If the current platform is not restart-capable, only exits.
   */
  void restart();

  /**
   * Checks if the application is active.
   *
   * @return {@code true} if one of application windows is focused, {@code false} otherwise
   */
  boolean isActive();

  /**
   * Checks if IDE is running in
   * <a href="https://plugins.jetbrains.com/docs/intellij/enabling-internal.html">Internal Mode</a>
   * to enable additional features for plugin development.
   */
  boolean isInternal();

  boolean isEAP();

  @ApiStatus.Internal
  default boolean isExitInProgress() {
    return false;
  }

  @ApiStatus.Internal
  boolean isSaveAllowed();

  //<editor-fold desc="Deprecated stuff">

  /** @deprecated Use {@link #addApplicationListener(ApplicationListener, Disposable)} instead */
  @Deprecated
  void addApplicationListener(@NotNull ApplicationListener listener);

  /** @deprecated call {@code Disposer.dispose(disposable);} on disposable passed to {@link #addApplicationListener(ApplicationListener, Disposable)} */
  @Deprecated
  void removeApplicationListener(@NotNull ApplicationListener listener);

  /** @deprecated use {@link #isDisposed()} instead */
  @Deprecated
  default boolean isDisposeInProgress() {
    return isDisposed();
  }

  /** @deprecated use {@link #runReadAction(Runnable)} instead */
  @Deprecated
  @NotNull AccessToken acquireReadActionLock();

  /** @deprecated use {@link #runWriteAction}, {@link WriteAction#run(ThrowableRunnable)}, or {@link WriteAction#compute} instead */
  @Deprecated
  @NotNull AccessToken acquireWriteActionLock(@NotNull Class<?> marker);

  /** @deprecated bad name, use {@link #isWriteIntentLockAcquired()} instead */
  @Deprecated
  @ApiStatus.Experimental
  @Contract(pure = true)
  default boolean isWriteThread() {
    return isWriteIntentLockAcquired();
  }

  /** @deprecated bad name, use {@link #assertWriteIntentLockAcquired()} instead */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  @ApiStatus.Experimental
  default void assertIsWriteThread() {
    assertWriteIntentLockAcquired();
  }
  //</editor-fold>

  @ApiStatus.Experimental
  @ApiStatus.Internal
  default CoroutineContext getLockStateAsCoroutineContext() {
    return EmptyCoroutineContext.INSTANCE;
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  default boolean hasLockStateInContext(CoroutineContext context) {
    return false;
  }
}
