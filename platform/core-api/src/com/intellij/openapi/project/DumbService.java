/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.*;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Debugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A service managing IDEA's 'dumb' mode: when indices are updated in background and the functionality is very much limited.
 * Only the explicitly allowed functionality is available. Usually it's allowed by implementing {@link DumbAware} interface.
 *
 * @author peter
 */
public abstract class DumbService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.DumbService");

  /**
   * @see Project#getMessageBus()
   */
  public static final Topic<DumbModeListener> DUMB_MODE = new Topic<>("dumb mode", DumbModeListener.class);

  /**
   * The tracker is advanced each time we enter/exit from dumb mode.
   */
  public abstract ModificationTracker getModificationTracker();

  /**
   * @return whether IntelliJ IDEA is in dumb mode, which means that right now indices are updated in background.
   * IDEA offers only limited functionality at such times, e.g. plain text file editing and version control operations.
   */
  public abstract boolean isDumb();

  public static boolean isDumb(@NotNull Project project) {
    return getInstance(project).isDumb();
  }

  /**
   * Executes the runnable as soon as possible on AWT Event Dispatch when:
   * <ul>
   *   <li>project is initialized</li>
   *   <li>and there's no dumb mode in progress</li>
   * </ul>
   * This may also happen immediately if these conditions are already met.<p/>
   * Note that it's not guaranteed that the dumb mode won't start again during this runnable execution, it should manage that situation explicitly.
   * @param runnable runnable to run
   */
  public abstract void runWhenSmart(@Debugger.Capture @NotNull Runnable runnable);

  /**
   * Pause the current thread until dumb mode ends and then continue execution.
   * NOTE: there are no guarantees that a new dumb mode won't begin before the next statement.
   * Hence: use with care. Consider using {@link #runWhenSmart(Runnable)}, {@link #runReadActionInSmartMode(Runnable)} or {@link #repeatUntilPassesInSmartMode(Runnable)} instead
   */
  public abstract void waitForSmartMode();

  /**
   * Pause the current thread until dumb mode ends, and then run the read action. Index is guaranteed to be available inside that read action,
   * unless this method is already called with read access allowed.
   */
  public <T> T runReadActionInSmartMode(@NotNull final Computable<T> r) {
    final Ref<T> result = new Ref<>();
    runReadActionInSmartMode(() -> result.set(r.compute()));
    return result.get();
  }

  @Nullable
  public <T> T tryRunReadActionInSmartMode(@NotNull Computable<T> task, @Nullable String notification) {
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
   * Pause the current thread until dumb mode ends, and then run the read action. Index is guaranteed to be available inside that read action,
   * unless this method is already called with read access allowed.
   */
  public void runReadActionInSmartMode(@NotNull final Runnable r) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      r.run();
      return;
    }

    while (true) {
      waitForSmartMode();
      boolean success = ReadAction.compute(() -> {
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
   * try again until the runnable is able to complete successfully.
   * It makes sense to use this method when you have a long-running activity consisting of many small read actions, and you don't want to
   * use a single long read action in order to keep the IDE responsive.
   * 
   * @see #runReadActionInSmartMode(Runnable) 
   */
  public void repeatUntilPassesInSmartMode(@NotNull final Runnable r) {
    while (true) {
      waitForSmartMode();
      try {
        r.run();
        return;
      }
      catch (IndexNotReadyException e) {
        LOG.info(e);
      }
    }
  }

  /**
   * Invoke the runnable later on EventDispatchThread AND when IDEA isn't in dumb mode
   * @param runnable runnable
   */
  public abstract void smartInvokeLater(@NotNull Runnable runnable);

  public abstract void smartInvokeLater(@NotNull Runnable runnable, @NotNull ModalityState modalityState);

  private static final NotNullLazyKey<DumbService, Project> INSTANCE_KEY = ServiceManager.createLazyKey(DumbService.class);

  public static DumbService getInstance(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  /**
   * @return all the elements of the given array if there's no dumb mode currently, or the dumb-aware ones if {@link #isDumb()} is true.
   * @see #isDumbAware(Object) 
   */
  @NotNull
  public <T> List<T> filterByDumbAwareness(@NotNull T[] array) {
    return filterByDumbAwareness(Arrays.asList(array));
  }

  /**
   * @return all the elements of the given collection if there's no dumb mode currently, or the dumb-aware ones if {@link #isDumb()} is true. 
   * @see #isDumbAware(Object)
   */
  @NotNull
  public <T> List<T> filterByDumbAwareness(@NotNull Collection<T> collection) {
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
   * Queues a task to be executed in "dumb mode", where access to indices is forbidden. Tasks are executed sequentially
   * in background unless {@link #completeJustSubmittedTasks()} is called in the same dispatch thread activity.<p/>
   *
   * Tasks can specify custom "equality" policy via their constructor. Calling this method has no effect if an "equal" task is already enqueued (but not yet running).
   */
  public abstract void queueTask(@NotNull DumbModeTask task);

  /**
   * Cancels the given task. If it's in the queue, it won't be executed. If it's already running, its {@link com.intellij.openapi.progress.ProgressIndicator} is canceled, so the next {@link ProgressManager#checkCanceled()} call
   * will throw {@link com.intellij.openapi.progress.ProcessCanceledException}.
   */
  public abstract void cancelTask(@NotNull DumbModeTask task);

  /**
   * Runs the "just submitted" tasks under a modal dialog. "Just submitted" means that tasks were queued for execution
   * earlier within the same Swing event dispatch thread event processing, and there were no other tasks already running at that moment. Otherwise this method does nothing.<p/>
   *
   * This functionality can be useful in refactorings (invoked in "smart mode"), when after VFS or root changes
   * (which could start "dumb mode") some reference resolve is required (which again requires "smart mode").
   */
  public abstract void completeJustSubmittedTasks();

  public abstract JComponent wrapGently(@NotNull JComponent dumbUnawareContent, @NotNull Disposable parentDisposable);

  public void makeDumbAware(@NotNull final JComponent component, @NotNull Disposable disposable) {
    component.setEnabled(!isDumb());
    getProject().getMessageBus().connect(disposable).subscribe(DUMB_MODE, new DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        component.setEnabled(false);
      }

      @Override
      public void exitDumbMode() {
        component.setEnabled(true);
      }
    });
  }

  public abstract void showDumbModeNotification(@NotNull String message);

  public abstract Project getProject();

  public static boolean isDumbAware(Object o) {
    if (o instanceof PossiblyDumbAware) {
      return ((PossiblyDumbAware)o).isDumbAware();
    }
    return o instanceof DumbAware;
  }

  /**
   * Enables or disables alternative resolve strategies for the current thread.<p/> 
   * 
   * Normally reference resolution uses index, and hence is not available in dumb mode. In some cases, alternative ways
   * of performing resolve are available, although much slower. It's impractical to always use these ways because it'll
   * lead to overloaded CPU (especially given there's also indexing in progress). But for some explicit user actions
   * (e.g. explicit Goto Declaration) turning these slower methods is beneficial.<p/>
   *
   * NOTE: even with alternative resolution enabled, methods like resolve(), findClass() etc may still throw
   * {@link IndexNotReadyException}. So alternative resolve is not a panacea, it might help provide navigation in some cases
   * but not in all.<p/>
   * 
   * A typical usage would involve try-finally, where the alternative resolution is first enabled, then an action is performed,
   * and then alternative resolution is turned off in the finally block.
   */
  public abstract void setAlternativeResolveEnabled(boolean enabled);

  /**
   * Invokes the given runnable with alternative resolve set to true.
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
   * 
   * @see #setAlternativeResolveEnabled(boolean) 
   */
  public abstract boolean isAlternativeResolveEnabled();

  /**
   * Obsolete, does nothing, just executes the passed runnable.
   * @see #completeJustSubmittedTasks()
   */
  @SuppressWarnings({"deprecation", "unused"})
  @Deprecated
  public static void allowStartingDumbModeInside(@NotNull DumbModePermission permission, @NotNull Runnable runnable) {
    runnable.run();
  }

  /**
   * @see #DUMB_MODE
   */
  public interface DumbModeListener {

    /**
     * The event arrives on EDT
     */
    default void enteredDumbMode() {}

    /**
     * The event arrives on EDT
     */
    default void exitDumbMode() {}

  }

}
