// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;

public interface ThreadNameManager {
  String getThreadName(@NotNull ActivityImpl event);
}
