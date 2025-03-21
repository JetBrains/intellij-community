// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveFileFix implements ModCommandAction {
  private final VirtualFile myFile;
  private final VirtualFile myTarget;
  private final @IntentionName String myMessage;

  public MoveFileFix(@NotNull VirtualFile file, @NotNull VirtualFile target, @NotNull @Nls String message) {
    myFile = file;
    myTarget = target;
    myMessage = message;
  }

  @Override
  public @NotNull String getFamilyName() {
    return myMessage;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return myFile.isValid() && myTarget.isValid() ? Presentation.of(myMessage) : null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.moveFile(myFile, myTarget);
  }
}