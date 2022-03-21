// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.ml;

import org.jetbrains.annotations.ApiStatus;

/**
 * A marker interface for the {@link com.intellij.codeInsight.lookup.LookupElement} to ignore this element
 * from sorting by Machine Learning-assisted completion.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface IgnoreMLLookupElement {
}
