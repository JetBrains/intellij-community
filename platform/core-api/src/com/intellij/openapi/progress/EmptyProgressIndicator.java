// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * See {@link EmptyProgressIndicatorBase} notice.
 * </p>
 */
public class EmptyProgressIndicator extends EmptyProgressIndicatorBase implements StandardProgressIndicator {
  private volatile @Nullable Throwable myCancellationRequester;

  private static final Throwable PLACEHOLDER = new Throwable(
    "Dummy throwable that indicates cancellation of EmptyProgressIndicator.\nSet `ide.rich.cancellation.traces` to `true` to get real origin of cancellation.");

  @Obsolete
  @Contract(pure = true)
  public EmptyProgressIndicator() { }

  @Obsolete
  public EmptyProgressIndicator(@NotNull ModalityState modalityState) {
    super(modalityState);
  }

  @Override
  public void start() {
    super.start();
    myCancellationRequester = null;
  }

  @Override
  public final void cancel() {
    myCancellationRequester =
      Registry.is("ide.rich.cancellation.traces", false) ? new Throwable("Origin of cancellation of " + this) : PLACEHOLDER;
    ProgressManager.canceled(this);
  }

  final @Nullable Throwable getCancellationCause() {
    return myCancellationRequester;
  }

  @Override
  public final boolean isCanceled() {
    return myCancellationRequester != null;
  }

  /**
   * @deprecated instead of using this function somewhere higher in the stacktrace,
   * make sure the indicator is installed by whoever calls the function which calls {@code notNullize},
   * or, in other words, make sure {@link ProgressManager#getGlobalProgressIndicator()} returns non-null value.
   * This function is dangerous because it makes the code effectively non-cancellable suppressing any assertions.
   */
  @Deprecated
  public static @NotNull ProgressIndicator notNullize(@Nullable ProgressIndicator indicator) {
    return indicator != null ? indicator : new EmptyProgressIndicator();
  }
}
