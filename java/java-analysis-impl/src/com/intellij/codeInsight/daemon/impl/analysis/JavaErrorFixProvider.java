// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Extension point to provide Java compilation error fixes
 * <p>
 * Experimental: may be moved to another package later
 */
@ApiStatus.Experimental
public interface JavaErrorFixProvider {
  ExtensionPointName<JavaErrorFixProvider> EP_NAME = ExtensionPointName.create("com.intellij.java.errorFixProvider");
  
  /**
   * @param error error to attach fixes to
   * @param sink fix consumer. Call sink.accept(fix) for every fix that should be attached to a given error.
   */
  void registerFixes(@NotNull JavaCompilationError<?, ?> error, @NotNull Consumer<? super @NotNull CommonIntentionAction> sink);

  /**
   * A registrator that produces heavy fixes, which are desired to be registered asynchronously. 
   * 
   * @param error error to attach fixes to
   * @return a consumer that accepts a sink to register fixes later asynchronously, 
   * or null if it was quickly determined that no asynchronous fixes need to be registered.
   */
  default @Nullable Consumer<@NotNull Consumer<? super @NotNull CommonIntentionAction>> registerLazyFixes(@NotNull JavaCompilationError<?, ?> error) {
    return null;
  }
}
