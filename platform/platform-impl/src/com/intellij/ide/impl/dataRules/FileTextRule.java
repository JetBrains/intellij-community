// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author mike
 */
public class FileTextRule implements GetDataRule {
  @Override
  public Object getData(DataProvider dataProvider) {
    final VirtualFile virtualFile = (VirtualFile)dataProvider.getData(CommonDataKeys.VIRTUAL_FILE.getName());
    if (virtualFile == null) {
      return null;
    }

    final FileType fileType = virtualFile.getFileType();
    if (fileType.isBinary() || fileType.isReadOnly()) {
      return null;
    }

    final Project project = (Project)dataProvider.getData(CommonDataKeys.PROJECT.getName());
    if (project == null) {
      return null;
    }

    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) {
      return null;
    }

    return document.getText();
  }
}
