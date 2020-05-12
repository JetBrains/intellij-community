// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A service managing the IDE's 'dumb' mode: when indexes are updated in the background, and the functionality is very much limited.
 * Only the explicitly allowed functionality is available. Usually, it's allowed by implementing {@link DumbAware} interface.<p></p>
 * <p>
 * "Dumb" mode starts and ends in a {@link com.intellij.openapi.application.WriteAction}, so if you're inside a {@link ReadAction}
 * on a background thread, it won't suddenly begin in the middle of your operation. But note that whenever you start
 * a top-level read action on a background thread, you should be prepared to anything being changed, including "dumb"
 * mode being suddenly on and off. To avoid executing a read action in "dumb" mode, please use {@link #runReadActionInSmartMode} or
 * {@link com.intellij.openapi.application.NonBlockingReadAction#inSmartMode}.
 * <p>
 * More information about dumb mode could be found here: {@link IndexNotReadyException}
 *
 * @author peter
 */
public abstract class DumbService {
  /**
   * @see Project#getMessageBus()
   */
  public static final Topic<DumbModeListener> DUMB_MODE = new Topic<>("dumb mode", DumbModeListener.class);

  /**
   * The tracker is advanced each time we enter/exit from dumb mode.
   */
  public abstract ModificationTracker getModificationTracker();

  /**
   * To avoid race conditions use it only in EDT thread or inside read-action. See documentation for this class {@link DumbService}
   *
   * @return whether the IDE is in dumb mode, which means that right now indexes are updated in the background.
   * The IDE offers only limited functionality at such times, e.g., plain text file editing and version control operations.
   */
  public abstract boolean isDumb();

  public static boolean isDumb(@NotNull Project project) {
    return getInstance(project).isDumb();
  }

  public static @NotNull <T> List<T> getDumbAwareExtensions(@NotNull Project project, @NotNull ExtensionPointName<T> extensionPoint) {
    List<T> list = extensionPoint.getExtensionList();
    if (list.isEmpty()) {
      return list;
    }

    DumbService dumbService = getInstance(project);
    return dumbService.filterByDumbAwareness(list);
  }

  public static @NotNull <T> List<T> getDumbAwareExtensions(@NotNull Project project, @NotNull ProjectExtensionPointName<T> extensionPoint) {
    DumbService dumbService = getInstance(project);
    return dumbService.filterByDumbAwareness(extensionPoint.getExtensions(project));
  }

  /**
   * Executes the runnable as soon as possible on AWT Event Dispatch when:
   * <ul>
   * <li>project is initialized</li>
   * <li>and there's no dumb mode in progress</li>
   * </ul>
   * This may also happen immediately if these conditions are already met.<p/>
   * Note that it's not guaranteed that the dumb mode won't start again during this runnable execution, it should manage that situation explicitly.
   */
  public abstract void runWhenSmart(@NotNull Runnable runnable);

  /**
   * Pause the current thread until dumb mode ends and then continue execution.
   * NOTE: there are no guarantees that a new dumb mode won't begin before the next statement.
   * Hence: use with care. Consider using {@link #runWhenSmart(Runnable)} or {@link #runReadActionInSmartMode(Runnable)} instead
   */
  public abstract void waitForSmartMode();

  /**
   * Pause the current thread until dumb mode ends, and then run the read action. Indexes are guaranteed to be available inside that read action,
   * unless this method is already called with read access allowed.
   *
   * @throws ProcessCanceledException if the project is closed during dumb mode
   */
  public <T> T runReadActionInSmartMode(final @NotNull Computable<T> r) {
    final Ref<T> result = new Ref<>();
    runReadActionInSmartMode(() -> result.set(r.compute()));
    return result.get();
  }

  public @Nullable <T> T tryRunReadActionInSmartMode(@NotNull Computable<T> task, @Nullable String notification) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      try {
        return task.compute();
      }
      catch (IndexNotReadyException e) {
        if (notification != null) {
          showDumbModeNotification(notification);
        }
        return null;
      }
    }
    else {
      return runReadActionInSmartMode(task);
    }
  }

  /**
   * Pause the current thread until dumb mode ends, and then run the read action. Indexes are guaranteed to be available inside that read action,
   * unless this method is already called with read access allowed.
   *
   * @throws ProcessCanceledException if the project is closed during dumb mode
   */
  public void runReadActionInSmartMode(@NotNull Runnable r) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      // we can't wait for smart mode to begin (it'd result in a deadlock),
      // so let's just pretend it's already smart and fail with IndexNotReadyException if not
      r.run();
      return;
    }

    while (true) {
      waitForSmartMode();
      boolean success = ReadAction.compute(() -> {
        if (getProject().isDisposed()) {
          throw new ProcessCanceledException();
        }
        if (isDumb()) {
          return false;
        }
        r.run();
        return true;
      });
      if (success) break;
    }
  }

  /**
   * Pause the current thread until dumb mode ends, and then attempt to execute the runnable. If it fails due to another dumb mode having started,
   * try again until the runnable can complete successfully.
   *
   * @deprecated This method provides no guarantees and should be avoided, please use {@link #runReadActionInSmartMode} instead.
   */
  @Deprecated
  public void repeatUntilPassesInSmartMode(final @NotNull Runnable r) {
    while (true) {
      waitForSmartMode();
      try {
        r.run();
        return;
      }
      catch (IndexNotReadyException ignored) {
      }
    }
  }

  /**
   * Invoke the runnable later on EventDispatchThread AND when IDE isn't in dumb mode.
   * The runnable won't be invoked if the project is disposed during dumb mode.
   */
  public abstract void smartInvokeLater(@NotNull Runnable runnable);

  /**
   * Invoke the runnable later on EventDispatchThread with the given modality state AND when IDE isn't in dumb mode.
   * The runnable won't be invoked if the project is disposed during dumb mode.
   */
  public abstract void smartInvokeLater(@NotNull Runnable runnable, @NotNull ModalityState modalityState);

  private static final NotNullLazyKey<DumbService, Project> INSTANCE_KEY = ServiceManager.createLazyKey(DumbService.class);

  public static DumbService getInstance(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  /**
   * @return all the elements of the given array if there's no dumb mode currently, or the dumb-aware ones if {@link #isDumb()} is true.
   * @see #isDumbAware(Object)
   */
  public @NotNull <T> List<T> filterByDumbAwareness(T @NotNull [] array) {
    return filterByDumbAwareness(Arrays.asList(array));
  }

  /**
   * @return all the elements of the given collection if there's no dumb mode currently, or the dumb-aware ones if {@link #isDumb()} is true.
   * @see #isDumbAware(Object)
   */
  @Contract(pure = true)
  public @NotNull <T> List<T> filterByDumbAwareness(@NotNull Collection<? extends T> collection) {
    if (isDumb()) {
      final ArrayList<T> result = new ArrayList<>(collection.size());
      for (T element : collection) {
        if (isDumbAware(element)) {
          result.add(element);
        }
      }
      return result;
    }

    if (collection instanceof List) {
      return (List<T>)collection;
    }

    return new ArrayList<>(collection);
  }

  /**
   * Queues a task to be executed in "dumb mode", where access to indexes is forbidden. Tasks are executed sequentially
   * in background unless {@link #completeJustSubmittedTasks()} is called in the same dispatch thread activity.<p/>
   * <p>
   * Tasks can specify custom "equality" policy via their constructor. Calling this method has no effect if an "equal" task is already enqueued (but not yet running).
   */
  public abstract void queueTask(@NotNull DumbModeTask task);

  /**
   * Cancels the given task. If it's in the queue, it won't be executed. If it's already running, its {@link com.intellij.openapi.progress.ProgressIndicator} is canceled, so the next {@link ProgressManager#checkCanceled()} call
   * will throw {@link ProcessCanceledException}.
   */
  public abstract void cancelTask(@NotNull DumbModeTask task);

  /**
   * Cancels all tasks and wait when their execution is finished. Should be called on write thread.
   */
  @ApiStatus.Internal
  public abstract void cancelAllTasksAndWait();

  /**
   * Runs the "just submitted" tasks under a modal dialog. "Just submitted" means that tasks were queued for execution
   * earlier within the same Swing event dispatch thread event processing, and there were no other tasks already running at that moment. Otherwise, this method does nothing.<p/>
   * <p>
   * This functionality can be useful in refactorings (invoked in "smart mode"), when after VFS or root changes
   * (which could start "dumb mode") some reference resolve is required (which again requires "smart mode").<p/>
   * <p>
   * Should be invoked on dispatch thread.
   * It's the caller's responsibility to invoke this method only when the model is in internally consistent state,
   * so that background threads with read actions don't see half-baked PSI/VFS/etc.
   */
  public abstract void completeJustSubmittedTasks();

  /**
   * Replaces given component temporarily with "Not available until indices are built" label during dumb mode.
   *
   * @return Wrapped component.
   */
  public abstract JComponent wrapGently(@NotNull JComponent dumbUnawareContent, @NotNull Disposable parentDisposable);

  /**
   * Adds a "Results might be incomplete while indexing." decorator to a given component during dumb mode.
   *
   * @param dumbAwareContent - a component to wrap
   * @param updateRunnable - an action to execute when dumb mode state changed or user explicitly request reload panel
   *
   * @return Wrapped component.
   */
  public abstract JComponent wrapWithSpoiler(@NotNull JComponent dumbAwareContent, @NotNull Runnable updateRunnable, @NotNull Disposable parentDisposable);

  /**
   * Disables given component temporarily during dumb mode.
   */
  public void makeDumbAware(final @NotNull JComponent componentToDisable, @NotNull Disposable parentDisposable) {
    componentToDisable.setEnabled(!isDumb());
    getProject().getMessageBus().connect(parentDisposable).subscribe(DUMB_MODE, new DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        componentToDisable.setEnabled(false);
      }

      @Override
      public void exitDumbMode() {
        componentToDisable.setEnabled(true);
      }
    });
  }

  /**
   * Show a notification when given action is not available during dumb mode.
   */
  public abstract void showDumbModeNotification(@NotNull @PopupContent String message);

  /**
   * Shows balloon about indexing blocking those actions until it is hidden (by key input, mouse event, etc.) or indexing stops.
   * @param balloonText
   * @param runWhenSmartAndBalloonStillShowing — will be executed in smart mode on EDT, balloon won't be dismissed by user's actions
   */
  public abstract void showDumbModeActionBalloon(@NotNull @PopupContent String balloonText,
                                                 @NotNull Runnable runWhenSmartAndBalloonStillShowing);

  public abstract Project getProject();

  @Contract(value = "null -> false", pure = true)
  public static boolean isDumbAware(Object o) {
    if (o instanceof PossiblyDumbAware) {
      return ((PossiblyDumbAware)o).isDumbAware();
    }
    //noinspection SSBasedInspection
    return o instanceof DumbAware;
  }

  /**
   * Enables or disables alternative resolve strategies for the current thread.<p/>
   * <p>
   * Normally reference resolution uses indexes, and hence is not available in dumb mode. In some cases, alternative ways
   * of performing resolve are available, although much slower. It's impractical to always use these ways because it'll
   * lead to overloaded CPU (especially given there's also indexing in progress). But for some explicit user actions
   * (e.g., explicit Goto Declaration) turning on these slower methods is beneficial.<p/>
   * <p>
   * NOTE: even with alternative resolution enabled, methods like resolve(), findClass() etc may still throw
   * {@link IndexNotReadyException}. So alternative resolve is not a panacea, it might help provide navigation in some cases
   * but not in all.<p/>
   * <p>
   * A typical usage would involve {@code try-finally}, where the alternative resolution is first enabled, then an action is performed,
   * and then alternative resolution is turned off in the {@code finally} block.
   * @deprecated Use {@link #runWithAlternativeResolveEnabled(ThrowableRunnable)} or {@link #computeWithAlternativeResolveEnabled(ThrowableComputable)} or {@link #withAlternativeResolveEnabled(Runnable)} instead
   */
  @Deprecated
  public abstract void setAlternativeResolveEnabled(boolean enabled);

  /**
   * Invokes the given runnable with alternative resolve set to true.
   *
   * @see #setAlternativeResolveEnabled(boolean)
   */
  public void withAlternativeResolveEnabled(@NotNull Runnable runnable) {
    setAlternativeResolveEnabled(true);
    try {
      runnable.run();
    }
    finally {
      setAlternativeResolveEnabled(false);
    }
  }

  /**
   * Invokes the given computable with alternative resolve set to true.
   *
   * @see #setAlternativeResolveEnabled(boolean)
   */
  public <T, E extends Throwable> T computeWithAlternativeResolveEnabled(@NotNull ThrowableComputable<T, E> runnable) throws E {
    setAlternativeResolveEnabled(true);
    try {
      return runnable.compute();
    }
    finally {
      setAlternativeResolveEnabled(false);
    }
  }

  /**
   * Invokes the given runnable with alternative resolve set to true.
   *
   * @see #setAlternativeResolveEnabled(boolean)
   */
  public <E extends Throwable> void runWithAlternativeResolveEnabled(@NotNull ThrowableRunnable<E> runnable) throws E {
    setAlternativeResolveEnabled(true);
    try {
      runnable.run();
    }
    finally {
      setAlternativeResolveEnabled(false);
    }
  }

  /**
   * @return whether alternative resolution is enabled for the current thread.
   * @see #setAlternativeResolveEnabled(boolean)
   */
  public abstract boolean isAlternativeResolveEnabled();

  /**
   * @see #completeJustSubmittedTasks()
   * @deprecated Obsolete, does nothing, just executes the passed runnable.
   */
  @Deprecated
  public static void allowStartingDumbModeInside(@NotNull DumbModePermission permission, @NotNull Runnable runnable) {
    runnable.run();
  }

  /**
   * Runs a heavy activity and suspends indexing (if any) for this time. The user still can manually pause and resume the indexing. In that case, indexing won't be resumed automatically after the activity finishes.
   *
   * @param activityName the text (a noun phrase) to display as a reason for the indexing being paused
   */
  public abstract void suspendIndexingAndRun(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String activityName,
                                             @NotNull Runnable activity);

  /**
   * Checks whether {@link #isDumb()} is true for the current project and if it's currently suspended by user or a {@link #suspendIndexingAndRun} call.
   * This should be called inside read action. The momentary system state is returned: there are no guarantees that the result won't change
   * in the next line of the calling code.
   */
  public abstract boolean isSuspendedDumbMode();

  /**
   * @see #DUMB_MODE
   */
  public interface DumbModeListener {
    /**
     * The event arrives on EDT.
     */
    default void enteredDumbMode() {}

    /**
     * The event arrives on EDT.
     */
    default void exitDumbMode() {}
  }

  @ApiStatus.Internal
  public abstract void unsafeRunWhenSmart(@NotNull Runnable runnable);
}
