// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nullable;

public interface LookupManagerListener {
  void activeLookupChanged(@Nullable Lookup oldLookup, @Nullable Lookup newLookup);

  @Topic.ProjectLevel
  Topic<LookupManagerListener> TOPIC = Topic.create("lookup manager listener", LookupManagerListener.class);
}
