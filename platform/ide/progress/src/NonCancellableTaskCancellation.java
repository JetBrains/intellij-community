// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress;

import org.jetbrains.annotations.ApiStatus.Internal;

@Internal
public final class NonCancellableTaskCancellation implements TaskCancellation.NonCancellable {

  static final TaskCancellation.NonCancellable INSTANCE = new NonCancellableTaskCancellation();

  private NonCancellableTaskCancellation() { }
}
