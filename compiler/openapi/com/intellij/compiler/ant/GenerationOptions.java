/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package com.intellij.compiler.ant;

public abstract class GenerationOptions {
  public final boolean generateSingleFile;
  public final boolean enableFormCompiler;
  public final boolean backupPreviouslyGeneratedFiles;
  public final boolean forceTargetJdk;

  public GenerationOptions(boolean forceTargetJdk, boolean generateSingleFile, boolean enableFormCompiler, boolean backupPreviouslyGeneratedFiles) {
    this.forceTargetJdk = forceTargetJdk;
    this.generateSingleFile = generateSingleFile;
    this.enableFormCompiler = enableFormCompiler;
    this.backupPreviouslyGeneratedFiles = backupPreviouslyGeneratedFiles;
  }

  public abstract String subsitutePathWithMacros(String path);

  public abstract String getPropertyRefForUrl(String url);

  public abstract ModuleChunk[] getModuleChunks();
}