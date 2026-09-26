// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import org.jetbrains.annotations.NotNull;


final class DefaultTypedHandler implements TypedActionHandler {

  @Override
  public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) {
      return;
    }
    String str = String.valueOf(charTyped);
    CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.in.editor.command.name"));
    Document doc = editor.getDocument();
    doc.startGuardedBlockChecking();
    try {
      EditorModificationUtil.typeInStringAtCaretHonorMultipleCarets(editor, str, true);
    } catch (ReadOnlyFragmentModificationException e) {
      EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
    } finally {
      doc.stopGuardedBlockChecking();
    }
  }
}
