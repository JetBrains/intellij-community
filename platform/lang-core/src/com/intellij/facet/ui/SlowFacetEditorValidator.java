// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.ui;

/**
 * Marker interface for {@link FacetEditorValidator} which indicates that
 * validator's {@code check()} method is slow, and it should be invoked on background thread.
 */
public interface SlowFacetEditorValidator {
}
