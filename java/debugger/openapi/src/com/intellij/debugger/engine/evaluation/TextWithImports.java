// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TextWithImports {
  @NlsSafe String getText();

  void setText(String newText);

  @NotNull
  String getImports();

  CodeFragmentKind getKind();

  boolean isEmpty();

  String toExternalForm();

  @Nullable
  FileType getFileType();
}
