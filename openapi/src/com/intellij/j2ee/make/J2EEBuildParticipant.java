/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.make;

import com.intellij.openapi.compiler.CompileContext;

import java.io.File;

public interface J2EEBuildParticipant {
  void registerBuildInstructions(BuildRecipe buildRecipe, CompileContext context);

  void afterJarCreated(File jarFile, CompileContext context) throws Exception;
  void afterExplodedCreated(File outputDir, CompileContext context) throws Exception;
  void buildFinished(CompileContext context) throws Exception;
}
