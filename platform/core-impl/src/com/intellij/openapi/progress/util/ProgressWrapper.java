/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.progress.WrappedProgressIndicator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProgressWrapper extends AbstractProgressIndicatorBase implements WrappedProgressIndicator, StandardProgressIndicator {
  private final ProgressIndicator myOriginal;
  private final boolean myCheckCanceledForMe;
  private final int nested;

  protected ProgressWrapper(@NotNull ProgressIndicator original) {
    this(original, false);
  }

  protected ProgressWrapper(@NotNull ProgressIndicator original, boolean checkCanceledForMe) {
    if (!(original instanceof StandardProgressIndicator)) {
      throw new IllegalArgumentException("Original indicator " + original + " must be StandardProcessIndicator but got: " + original.getClass());
    }
    myOriginal = original;
    myCheckCanceledForMe = checkCanceledForMe;
    nested = 1 + (original instanceof ProgressWrapper ? ((ProgressWrapper)original).nested : -1);
    //if (nested > 50) {
    //  LOG.error("Too many wrapped indicators");
    //}
    ProgressManager.assertNotCircular(original);
  }

  @Override
  public final void cancel() {
    super.cancel();
  }


  @Override
  public final boolean isCanceled() {
    ProgressWrapper current = this;
    while (true) {
      if (current.myCheckCanceledForMe && current.isCanceledRaw()) return true;
      ProgressIndicator original = current.getOriginalProgressIndicator();
      if (original instanceof ProgressWrapper) {
        current = (ProgressWrapper)original;
      }
      else {
        return original.isCanceled();
      }
    }
  }

  @Nullable
  @Override
  protected Throwable getCancellationTrace() {
    if (myOriginal instanceof AbstractProgressIndicatorBase) {
      return ((AbstractProgressIndicatorBase)myOriginal).getCancellationTrace();
    }
    return super.getCancellationTrace();
  }

  private boolean isCanceledRaw() { return super.isCanceled(); }
  private void checkCanceledRaw() { super.checkCanceled(); }

  @Override
  public final void checkCanceled() {
    ProgressWrapper current = this;
    while (true) {
      current.checkCanceledRaw();
      ProgressIndicator original = current.getOriginalProgressIndicator();
      if (original instanceof ProgressWrapper) {
        current = (ProgressWrapper)original;
      }
      else {
        original.checkCanceled();
        break;
      }
    }
  }

  @NotNull
  @Override
  public ModalityState getModalityState() {
    return myOriginal.getModalityState();
  }

  @Override
  @NotNull
  public ProgressIndicator getOriginalProgressIndicator() {
    return myOriginal;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static ProgressWrapper wrap(@Nullable ProgressIndicator indicator) {
    return indicator == null || indicator instanceof ProgressWrapper ? (ProgressWrapper)indicator : new ProgressWrapper(indicator);
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static ProgressIndicator unwrap(ProgressIndicator indicator) {
    return indicator instanceof ProgressWrapper ?
           ((ProgressWrapper)indicator).getOriginalProgressIndicator() : indicator;
  }
}
