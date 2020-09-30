// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.ide.IdeDeprecatedMessagesBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * @author lesya
 */
public final class ExecutionBundle extends DynamicBundle {
  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    if (ourInstance.containsKey(key)) {
      return ourInstance.getMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.message(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    if (ourInstance.containsKey(key)) {
      return ourInstance.getLazyMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.messagePointer(key, params);
  }

  public static final String PATH_TO_BUNDLE = "messages.ExecutionBundle";
  private static final AbstractBundle ourInstance = new ExecutionBundle();

  private ExecutionBundle() {
    super(PATH_TO_BUNDLE);
  }
}
