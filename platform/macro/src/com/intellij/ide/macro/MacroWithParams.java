// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

/**
 * Marker interface for {@link Macro} supporting parameters.
 * Macro with a parameter is referenced as: {@code $MyMacro(my param)$}.
 * Please note that multiple parameters are not supported by {@link MacroManager}.
 */
public interface MacroWithParams {
}
