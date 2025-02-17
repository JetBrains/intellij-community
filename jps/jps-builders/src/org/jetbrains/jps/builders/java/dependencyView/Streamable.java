// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.ApiStatus;

import java.io.PrintStream;

@ApiStatus.Internal
public interface Streamable {
  void toStream(DependencyContext context, PrintStream stream);
}
