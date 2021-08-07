// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import com.intellij.openapi.util.NlsContexts.ProgressDetails;
import com.intellij.openapi.util.NlsContexts.ProgressText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an entity used to send progress updates to.
 * The client code is not supposed to read the progress updates, thus there are no getters.
 * It's up to implementors of this interface to decide what to do with these updates.
 */
@ApiStatus.Experimental
public interface ProgressSink {

  /**
   * Updates current progress text.
   */
  void text(@ProgressText @NotNull String text);

  /**
   * Updates current progress details.
   */
  void details(@ProgressDetails @NotNull String details);

  /**
   * Updates current progress fraction.
   *
   * @param fraction a number between 0.0 and 1.0 reflecting the ratio of work that has already be done (0.0 for nothing, 1.0 for all)
   */
  void fraction(double fraction);
}
