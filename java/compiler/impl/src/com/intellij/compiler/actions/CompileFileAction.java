// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.IdeActions;

public class CompileFileAction extends CompileAction {
  private static final String RECOMPILE_FILES_ID_MOD = IdeActions.ACTION_COMPILE + "File";

  public CompileFileAction() {
    super(true, RECOMPILE_FILES_ID_MOD);
  }
}
