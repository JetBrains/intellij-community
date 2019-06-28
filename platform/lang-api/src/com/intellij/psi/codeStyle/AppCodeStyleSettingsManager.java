// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(name = "CodeStyleSettingsManager", storages = @Storage("code.style.schemes"))
public final class AppCodeStyleSettingsManager extends CodeStyleSettingsManager {
}
