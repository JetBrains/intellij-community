// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import org.jetbrains.annotations.ApiStatus;

/**
 * Same as {@link PsiTreeChangePreprocessor}, but runs in place, on the thread that fires the event.
 * <p>
 * The contract:
 * <ul>
 *   <li>the preprocessor must not use an EDT-only API, and must not assert the write-intent lock;</li>
 *   <li>two paired events can arrive on different threads, so the preprocessor must keep no thread-affine state:
 *   no {@code ThreadLocal}, and no unsynchronized mutable field;</li>
 *   <li>the write lock is held, as for {@link PsiTreeChangePreprocessor#EP}.</li>
 * </ul>
 */
@ApiStatus.Experimental
public interface PsiTreeChangePreprocessorBackgroundable extends PsiTreeChangePreprocessor {
  ProjectExtensionPointName<PsiTreeChangePreprocessorBackgroundable> EP =
    new ProjectExtensionPointName<>("com.intellij.psi.treeChangePreprocessorBackgroundable");
}