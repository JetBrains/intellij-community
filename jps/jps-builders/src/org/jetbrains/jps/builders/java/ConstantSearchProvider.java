// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.CompileContext;

/**
 * Implement this interface to provide additional constant affection resolver that will be notified when a constant is changed.
 * Implementations are registered as Java services, by creating a file
 * META-INF/services/org.jetbrains.jps.builders.java.ConstantSearchProvider
 * containing the qualified name of your implementation class.
 */
public interface ConstantSearchProvider {
  @NotNull
  Callbacks.ConstantAffectionResolver getConstantSearch(@NotNull CompileContext context);
}
