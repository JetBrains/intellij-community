// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import org.jetbrains.annotations.NotNull;

public class LightEditDocumentSaveVetoer extends FileDocumentSynchronizationVetoer {
  @Override
  public boolean maySaveDocument(@NotNull Document document, boolean isSaveExplicit) {
    if (LightEditUtil.getProjectIfCreated() == null) {
      return super.maySaveDocument(document, isSaveExplicit);
    }
    return isSaveExplicit || LightEditService.getInstance().getEditorManager().isImplicitSaveAllowed(document);
  }
}
