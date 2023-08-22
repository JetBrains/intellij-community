// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.ide.highlighter.ArchiveFileType;

public class FileTypes {
  protected FileTypes() { }

  public static final FileType ARCHIVE = ArchiveFileType.INSTANCE;
  public static final FileType UNKNOWN = UnknownFileType.INSTANCE;
  public static final LanguageFileType PLAIN_TEXT = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("PLAIN_TEXT");
}
