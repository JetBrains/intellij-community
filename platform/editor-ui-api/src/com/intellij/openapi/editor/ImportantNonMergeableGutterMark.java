// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.GutterMark;
import org.jetbrains.annotations.ApiStatus;

/**
 * This interface is used in some {@link GutterMarkPreprocessor} implementors (the ones that merge the gutter marks) to
 * mark a gutter mark as a non-participant in the merge logic.
 */
@ApiStatus.Internal
public interface ImportantNonMergeableGutterMark extends GutterMark {
}
