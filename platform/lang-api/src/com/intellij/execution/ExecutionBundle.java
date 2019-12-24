// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author lesya
 */
public class ExecutionBundle extends DynamicBundle {
  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, @NotNull Object... params) {
    return ourInstance.getMessage(key, params);
  }

  public static final String PATH_TO_BUNDLE = "messages.ExecutionBundle";
  private static final AbstractBundle ourInstance = new ExecutionBundle();

  private ExecutionBundle() {
    super(PATH_TO_BUNDLE);
  }
}
