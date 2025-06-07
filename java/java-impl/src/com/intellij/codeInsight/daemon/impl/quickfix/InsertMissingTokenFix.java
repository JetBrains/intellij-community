// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.modcommand.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class InsertMissingTokenFix implements ModCommandAction {
  private final @NotNull String myToken;
  private final boolean myMoveAfter;

  public InsertMissingTokenFix(@NotNull String token) {
    this(token, false);
  }

  public InsertMissingTokenFix(@NotNull String token, boolean moveAfter) {
    myToken = token;
    myMoveAfter = moveAfter;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return Presentation.of(getFamilyName())
      .withPriority(PriorityAction.Priority.LOW)
      .withFixAllOption(this, action -> action instanceof InsertMissingTokenFix tokenFix && tokenFix.myToken.equals(myToken));
  }

  @Override
  public @NotNull String getFamilyName() {
    return CommonQuickFixBundle.message("fix.insert.x", myToken);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    int offset = context.offset();
    Document document = context.file().getFileDocument();
    if (document instanceof DocumentWindow window) {
      offset = window.injectedToHost(offset);
      document = window.getDelegate();
    }
    String oldText = document.getText();
    String newText = oldText.substring(0, offset) + myToken + oldText.substring(offset);
    VirtualFile file = Objects.requireNonNull(FileDocumentManager.getInstance().getFile(document));
    ModCommand fix = new ModUpdateFileText(file, oldText, newText,
                                           List.of(new ModUpdateFileText.Fragment(offset, 0, myToken.length())));
    if (myMoveAfter) {
      fix = fix.andThen(new ModNavigate(file, -1, -1, offset + myToken.length()));
    }
    return fix;
  }
}
