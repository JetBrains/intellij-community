/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author kir
 */
public interface SelectInContext {

  String DATA_CONTEXT_ID = "SelectInContext";

  Project getProject();

  VirtualFile getVirtualFile();

  Object getSelectorInFile();

  StructureViewBuilder getStructureViewBuilder();
}
