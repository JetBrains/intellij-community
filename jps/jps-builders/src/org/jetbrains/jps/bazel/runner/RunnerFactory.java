// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.runner;

import org.jetbrains.jps.bazel.BuildContext;

public interface RunnerFactory<T extends Runner> {
  T create(BuildContext context);
}
