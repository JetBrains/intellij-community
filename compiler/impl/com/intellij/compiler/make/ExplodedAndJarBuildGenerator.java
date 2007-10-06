/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.compiler.make;

import com.intellij.compiler.ant.Tag;
import com.intellij.compiler.ant.ExplodedAndJarTargetParameters;
import com.intellij.compiler.ant.taskdefs.ZipFileSet;
import com.intellij.openapi.compiler.make.BuildInstruction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class ExplodedAndJarBuildGenerator {
  public static final ExtensionPointName<ExplodedAndJarBuildGenerator> EP_NAME = ExtensionPointName.create("com.intellij.explodedAndJarBuildGenerator");

  @Nullable
  public Tag[] generateTagsForExplodedTarget(@NotNull BuildInstruction instruction, @NotNull ExplodedAndJarTargetParameters parameters)
    throws Exception {
    return null;
  }

  @Nullable
  public ZipFileSet[] generateTagsForJarTarget(@NotNull BuildInstruction instruction, @NotNull ExplodedAndJarTargetParameters parameters,
                                         final Ref<Boolean> tempDirUsed) throws Exception {
    return null;
  }

  @Nullable
  public Tag[] generateJarBuildPrepareTags(@NotNull BuildInstruction instruction, @NotNull ExplodedAndJarTargetParameters parameters) throws Exception {
    return null;
  }

}
