// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.id;

import org.jetbrains.annotations.ApiStatus.OverrideOnly;

/**
 * @author traff
 * <p>
 * Marker interface that defines lexer that performes lexing to get Id index map
 */
@OverrideOnly
public interface LexingIdIndexer extends IdIndexer {
}
