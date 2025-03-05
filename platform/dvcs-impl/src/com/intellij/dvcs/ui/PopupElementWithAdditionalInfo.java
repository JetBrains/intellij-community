// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public interface PopupElementWithAdditionalInfo {
  default @Nls @Nullable String getInfoText() {return null;}
}
