// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileTypes.FileTypeExtension;

@Service
final class FileTypeBraceMatcher extends FileTypeExtension<BraceMatcher> {
  static FileTypeBraceMatcher getInstance() {
    return ApplicationManager.getApplication().getService(FileTypeBraceMatcher.class);
  }

  FileTypeBraceMatcher() {
    super(BraceMatcher.EP_NAME.getName());
  }
}
