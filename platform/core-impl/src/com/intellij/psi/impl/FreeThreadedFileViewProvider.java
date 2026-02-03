// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

/**
 * Marker interface for view providers which allow clearCaches() to be called from any thread, without holding write action.
 */
public interface FreeThreadedFileViewProvider {
}
