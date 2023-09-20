// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;

/**
 * Marker interface for {@link Macro}s which expansion should be performed after other macros' expansion.
 *
 * @see MacroManager#expandMacrosInString(String, boolean, DataContext)
 * @see MacroManager#expandMacrosInString(String, boolean, DataContext, String, boolean)
 * @see MacroManager#expandSilentMacros(String, boolean, DataContext)
 */
public interface SecondQueueExpandMacro {
}
