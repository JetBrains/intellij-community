// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class WaitNotifyNotInSynchronizedContextInspectionMerger extends InspectionElementsMerger {
  @Override
  public @NotNull String getMergedToolName() {
    return "WaitNotifyNotInSynchronizedContext";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "WaitNotInSynchronizedContext",
      "NotifyNotInSynchronizedContext"
    };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[]{
      "WaitWhileNotSynced",
      "NotifyNotInSynchronizedContext"
    };
  }
}
