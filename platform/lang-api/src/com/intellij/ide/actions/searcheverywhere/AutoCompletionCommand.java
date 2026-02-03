// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.util.NlsSafe;

import javax.swing.text.JTextComponent;

public interface AutoCompletionCommand {

  void completeQuery(JTextComponent textComponent);

  @NlsSafe String getPresentationString();
}
