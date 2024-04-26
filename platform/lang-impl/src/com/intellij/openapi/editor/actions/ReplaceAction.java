// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.actionSystem.EditorAction;

public final class ReplaceAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  public ReplaceAction() {
    super(new IncrementalFindAction.Handler(true));
  }
}
