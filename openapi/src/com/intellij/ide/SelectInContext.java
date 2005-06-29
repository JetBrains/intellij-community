/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kir
 */
public interface SelectInContext {

  String DATA_CONTEXT_ID = "SelectInContext";

  @NotNull
  Project getProject();

  @NotNull
  VirtualFile getVirtualFile();

  @Nullable
  Object getSelectorInFile();

  @Nullable
  FileEditorProvider getFileEditorProvider();
}
