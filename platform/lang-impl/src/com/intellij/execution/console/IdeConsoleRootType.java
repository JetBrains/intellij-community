// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.console;

import org.jetbrains.annotations.*;

/**
 * @author gregsh
 *
 * @deprecated Use {@link com.intellij.ide.script.IdeConsoleRootType} instead.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
public class IdeConsoleRootType extends ConsoleRootType {
  protected IdeConsoleRootType(@NonNls @NotNull String consoleTypeId, @Nls @Nullable String displayName) {
    super(consoleTypeId, displayName);
  }
}
