// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.text.JTextComponent;

/// @deprecated The functionality is redundant.
@Deprecated
@ApiStatus.Internal
public interface AutoCompletionCommand {

  void completeQuery(JTextComponent textComponent);

  @NlsSafe String getPresentationString();
}
