// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LightEditDocumentSaveVetoer extends FileDocumentSynchronizationVetoer {
  @Override
  public boolean maySaveDocument(@NotNull Document document, boolean isSaveExplicit) {
    if (LightEditUtil.getProjectIfCreated() == null) {
      return super.maySaveDocument(document, isSaveExplicit);
    }
    return isSaveExplicit || LightEditService.getInstance().getEditorManager().isImplicitSaveAllowed(document);
  }
}
