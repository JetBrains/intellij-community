// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

/**
 * Elements are compared one-by-one.
 * If range [a, b) is equal to [a', b'), than element(a + i) is equal to element(a' + i) for all i in [0, b-a)
 *
 * Matched fragments are guaranteed to have same length.
 */
public interface FairDiffIterable extends DiffIterable {
}
