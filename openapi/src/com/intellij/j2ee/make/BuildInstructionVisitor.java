/**
 * @author cdr
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.make;


public abstract class BuildInstructionVisitor {
  public boolean visitInstruction(BuildInstruction instruction) throws Exception {
    return true;
  }
  public boolean visitFileCopyInstruction(FileCopyInstruction instruction) throws Exception {
    return visitInstruction(instruction);
  }
  public boolean visitJarAndCopyBuildInstruction(JarAndCopyBuildInstruction instruction) throws Exception {
    return visitFileCopyInstruction(instruction);
  }
  public boolean visitJ2EEModuleBuildInstruction(J2EEModuleBuildInstruction instruction) throws Exception {
    return visitInstruction(instruction);
  }
}