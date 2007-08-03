/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.ant;

import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.compiler.make.CompoundBuildInstruction;
import com.intellij.openapi.module.Module;

/**
 * @author nik
 */
public class ExplodedAndJarTargetParameters {
  private ModuleChunk myChunk;
  private Module myContainingModule;
  private GenerationOptions myGenerationOptions;
  private String myExplodedPathParameter;
  private String myJarPathParameter;
  private final String myBuildExplodedTargetName;
  private final String myBuildJarTargetName;
  private final String myExplodedPathProperty;
  private final String myJarPathProperty;
  private BuildConfiguration myBuildConfiguration;
  private CompoundBuildInstructionNaming myCompoundBuildInstructionNaming;

  public ExplodedAndJarTargetParameters(final ModuleChunk chunk,
                                           final Module containingModule,
                                           final GenerationOptions generationOptions,
                                           final BuildConfiguration buildConfiguration,
                                           final String explodedPathParameter,
                                           final String jarPathParameter,
                                           final String buildExplodedTargetName,
                                           final String buildJarTargetName,
                                           final String explodedPathProperty,
                                           final String jarPathProperty, final CompoundBuildInstructionNaming compoundBuildInstructionNaming) {
    myCompoundBuildInstructionNaming = compoundBuildInstructionNaming;
    myBuildConfiguration = buildConfiguration;
    myChunk = chunk;
    myContainingModule = containingModule;
    myGenerationOptions = generationOptions;
    myExplodedPathParameter = explodedPathParameter;
    myJarPathParameter = jarPathParameter;
    myBuildExplodedTargetName = buildExplodedTargetName;
    myBuildJarTargetName = buildJarTargetName;
    myExplodedPathProperty = explodedPathProperty;
    myJarPathProperty = jarPathProperty;
  }

  public ModuleChunk getChunk() {
    return myChunk;
  }

  public GenerationOptions getGenerationOptions() {
    return myGenerationOptions;
  }

  public String getExplodedPathParameter() {
    return myExplodedPathParameter;
  }

  public String getJarPathParameter() {
    return myJarPathParameter;
  }

  public Module getContainingModule() {
    return myContainingModule;
  }

  public String getBuildExplodedTargetName() {
    return myBuildExplodedTargetName;
  }

  public String getBuildJarTargetName() {
    return myBuildJarTargetName;
  }

  public String getExplodedPathProperty() {
    return myExplodedPathProperty;
  }

  public String getJarPathProperty() {
    return myJarPathProperty;
  }

  public CompoundBuildInstructionNaming getCompoundBuildInstructionNaming() {
    return myCompoundBuildInstructionNaming;
  }

  public BuildConfiguration getBuildConfiguration() {
    return myBuildConfiguration;
  }
}
