/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.make;

import com.intellij.openapi.module.Module;

import java.io.File;
import java.io.FileFilter;

public interface BuildRecipe {
  void addInstruction(BuildInstruction instruction);

  boolean visitInstructions(BuildInstructionVisitor visitor, boolean reverseOrder);
  boolean visitInstructionsWithExceptions(BuildInstructionVisitor visitor, boolean reverseOrder) throws Exception;

  void addAll(BuildRecipe buildRecipe);
  void addFileCopyInstruction(File file,
                              boolean isDirectory, Module module,
                              String outputRelativePath,
                              FileFilter fileFilter);
}