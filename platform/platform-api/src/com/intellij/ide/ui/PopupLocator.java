// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.ui.popup.JBPopup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;


@ApiStatus.Experimental
@FunctionalInterface
public interface PopupLocator {

  @Nullable Point getPositionFor(@NotNull JBPopup popup);
}
