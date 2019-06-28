/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An object accompanying a computation, usually in a background thread. It allows to display process status to the user
 * ({@link #setText}, {@link #setText2}, {@link #setFraction}, {@link #setIndeterminate}) and
 * interrupt if the computation is canceled ({@link #checkCanceled()}).<p></p>
 *
 * There are many implementations which may implement only parts of the functionality in this interface. Some indicators are invisible,
 * not showing any status to the user, but still tracking cancellation.<p></p>
 *
 * To run a task with a visible progress indicator, modal or displayed in the status bar, please use {@link ProgressManager#run} methods.<p></p>
 *
 * To associate a thread with a progress, use {@link ProgressManager#runProcess} methods. This is mostly
 * used with invisible progress indicators for cancellation handling. A common case is to take a read action that's interrupted by a write action
 * about to occur in the UI thread, to avoid UI freezes (see {@link com.intellij.openapi.application.ReadAction#nonBlocking(Runnable)}).
 * Another common case is wrapping main thread's to parallelize the associated computation by forking additional threads.<p></p>
 *
 * Current thread's progress indicator, visible or not, can be retrieved with {@link ProgressManager#getGlobalProgressIndicator()}.<p></p>
 *
 * Here are some commonly used implementations:
 * <ul>
 *   <li>{@link EmptyProgressIndicator}: invisible (ignores text/fraction-related methods), used only for cancellation tracking.
 *   Remembers its creation modality state.</li>
 *   <li>{@link com.intellij.openapi.progress.util.ProgressIndicatorBase}: invisible, but can be made visible by subclassing:
 *   remembers text/fraction inside and allows to retrieve them and possibly show in the UI. Non-modal by default.</li>
 *   <li>{@link com.intellij.openapi.progress.util.ProgressWindow}: visible progress, either modal or background. Usually not created directly,
 *   instantiated internally inside {@link ProgressManager#run}.</li>
 *   <li>{@link com.intellij.openapi.progress.util.ProgressWrapper}: wraps an existing progress indicator, usually to fork another thread
 *   with the same cancellation policy. Use {@link com.intellij.concurrency.SensitiveProgressWrapper} to allow
 *   that separate thread's indicator to be canceled independently from the main thread.</li>
 * </ul>
 *
 */
public interface ProgressIndicator {
  /**
   * Marks the process as started. Invoked by {@link ProgressManager} internals, shouldn't be called from client code
   * unless you know what you're doing.
   */
  void start();

  /**
   * Marks the process as finished. Invoked by {@link ProgressManager} internals, shouldn't be called from client code
   * unless you know what you're doing.
   */
  void stop();

  /**
   * @return whether the computation associated with this progress indicator is currently running: started, not yet finished,
   * but possibly already canceled.
   */
  boolean isRunning();

  /**
   * Cancels the current process. Is usually invoked not by the process itself, but by some external activity:
   * e.g. a handler for a "Cancel" button pressed by the user (for visible processes), or by some other event handler
   * which recognizes that the process makes no sense.
   */
  void cancel();

  /**
   * @return whether the process has been canceled. Usually {@link #checkCanceled()} is called instead.
   * @see #cancel()
   */
  boolean isCanceled();

  /**
   * Sets text above the progress bar
   * @param text Text to set
   * @see #setText2(String)
   */
  void setText(String text);

  /**
   * @return text above the progress bar, set by {@link #setText(String)}
   */
  String getText();

  /**
   * Sets text under the progress bar
   * @param text Text to set
   * @see #setText(String)
   */
  void setText2(String text);

  /**
   * @return text under the progress bar, set by {@link #setText2(String)}
   */
  String getText2();

  /**
   * @return current fraction, set by {@link #setFraction(double)}
   */
  double getFraction();

  /**
   * Sets the fraction: a number between 0.0 and 1.0 reflecting the ratio of work that has already be done (0.0 for nothing, 1.0 for all).
   * Only works for determinate indicator.
   * The fraction should provide the user with rough estimation of the time left. If that's impossible, consider making the progress indeterminate.
   * @see #setIndeterminate(boolean)
   */
  void setFraction(double fraction);

  void pushState();

  void popState();

  /**
   * @deprecated use {@link ProgressManager#executeNonCancelableSection(Runnable)} instead
   */
  @Deprecated
  default void startNonCancelableSection() {}

  /**
   * @deprecated use {@link ProgressManager#executeNonCancelableSection(Runnable)} instead
   */
  @Deprecated
  default void finishNonCancelableSection() {}

  /**
   * @return whether this process blocks user activities while active, probably displaying a modal dialog.
   * @see #setModalityProgress(ProgressIndicator)
   */
  boolean isModal();

  /**
   * @return the modality state returned by {@link ModalityState#defaultModalityState()} on threads associated with this progress.
   * By default, depending on implementation, it's {@link ModalityState#NON_MODAL} or current modality at the moment of progress indicator creation.
   * It can be later modified by {@link #setModalityProgress(ProgressIndicator)}, but it mostly makes sense for processes showing modal dialogs.
   */
  @NotNull
  ModalityState getModalityState();

  /**
   * In many implementations, grants to this progress indicator its own modality state. Don't call unless you know what you're doing.
   * Passing a non-null value can make this indicator modal (as in {@link #isModal()}, which, if not accompanied by showing a modal dialog,
   * might affect the whole IDE adversely: e.g. actions won't be executed, editor typing won't work, and all that with no visible indication.
   */
  void setModalityProgress(@Nullable ProgressIndicator modalityProgress);

  /**
   * @return whether the progress is indeterminate and displays no fractions
   */
  boolean isIndeterminate();

  /**
   * Marks the progress indeterminate (for processes that can't estimate the amount of work to be done) or determinate (for processes
   * that can display the fraction of the work done using {@link #setFraction(double)}).
   */
  void setIndeterminate(boolean indeterminate);

  /**
   * Usually invoked in the thread associated with this indicator, used to check if the computation performed by this thread
   * has been canceled, and, if yes, stop it immediately (by throwing an exception). Threads should call this frequently
   * to allow for prompt cancellation. Failure to do this can cause UI freezes.<p></p>
   * You might also want to use {@link ProgressManager#checkCanceled()} where you don't need to know current indicator and pass it around.
   * @throws ProcessCanceledException if this progress has been canceled, i.e. {@link #isCanceled()} returns true.
   */
  void checkCanceled() throws ProcessCanceledException;

  boolean isPopupWasShown();
  boolean isShowing();
}
