// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events;

import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface MessageEventResult extends EventResult {
  MessageEvent.Kind getKind();

  default @Nullable @BuildEventsNls.Description String getDetails() { return null; }
}
