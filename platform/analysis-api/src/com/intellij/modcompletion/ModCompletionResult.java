// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.function.Consumer;

/**
 * Consumer for {@link ModCompletionItem}s created by completion providers. 
 */
@NotNullByDefault
public interface ModCompletionResult extends Consumer<ModCompletionItem> {
  
}
