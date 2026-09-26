// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;


public interface DebuggerContextListener extends EventListener {
  void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event);
}
