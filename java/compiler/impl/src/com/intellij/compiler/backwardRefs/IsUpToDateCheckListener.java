// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.util.IntellijInternalApi;
import com.intellij.util.messages.Topic;

/**
 * Provides the result of {@link com.intellij.openapi.compiler.CompilerManager#isUpToDate(CompileScope)} once at project startup
 * if {@link CompilerReferenceServiceBase#isEnabled()} is true.
 *
 * @see CompilerReferenceIndexIsUpToDateStartupActivity
 */
@IntellijInternalApi
public interface IsUpToDateCheckListener {
  @Topic.ProjectLevel
  Topic<IsUpToDateCheckListener> TOPIC = new Topic<>(IsUpToDateCheckListener.class, Topic.BroadcastDirection.NONE, true);

  void isUpToDateCheckFinished(boolean isUpToDate);
}
