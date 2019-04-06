// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.ui.popup.JBPopup;
import org.jetbrains.annotations.NotNull;

public interface QuickSearchComponent {

  void registerHint(@NotNull JBPopup h);

  void unregisterHint();
}
