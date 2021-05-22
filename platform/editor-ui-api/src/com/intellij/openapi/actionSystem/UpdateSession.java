// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An interface to the current session of {@link ActionGroup} expansion
 * for postprocessing and recursive expansion.
 *
 * @see com.intellij.ide.actions.WeighingActionGroup
 * @see com.intellij.ide.actions.NonTrivialActionGroup
 */
@ApiStatus.Experimental
public interface UpdateSession {

  @NotNull Iterable<? extends AnAction> children(@NotNull ActionGroup actionGroup);

  @NotNull Presentation presentation(@NotNull AnAction action);
}
