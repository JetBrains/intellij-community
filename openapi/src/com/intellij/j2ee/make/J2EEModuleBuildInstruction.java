/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.make;


import com.intellij.openapi.compiler.CompileContext;

public interface J2EEModuleBuildInstruction extends BuildInstruction {
  ModuleBuildProperties getBuildProperties();

  BuildRecipe getChildInstructions(CompileContext context);
}