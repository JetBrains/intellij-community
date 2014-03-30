/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author mike
 */
public class FileTextRule implements GetDataRule {
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
