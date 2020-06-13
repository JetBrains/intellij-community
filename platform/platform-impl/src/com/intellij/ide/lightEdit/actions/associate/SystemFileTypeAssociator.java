// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SystemFileTypeAssociator {
  void associateFileTypes(@NotNull List<FileType> fileTypes) throws FileAssociationException;
}
