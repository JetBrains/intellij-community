/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.ant;

import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.compiler.make.CompoundBuildInstruction;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public abstract class ExplodedAndJarTargetParameters {
  private ModuleChunk myChunk;
  private Module myContainingModule;
  private String myConfigurationName;
  private GenerationOptions myGenerationOptions;
  private String myExplodedPathProperty;
  private String myJarPathProperty;
  private BuildConfiguration myBuildConfiguration;

  protected ExplodedAndJarTargetParameters(final ModuleChunk chunk,
                                           final Module containingModule,
                                           final String configurationName,
                                           final GenerationOptions generationOptions,
                                           final BuildConfiguration buildConfiguration,
                                           final String explodedPathProperty,
                                           final String jarPathProperty) {
    myBuildConfiguration = buildConfiguration;
    myChunk = chunk;
    myContainingModule = containingModule;
    myConfigurationName = configurationName;
    myGenerationOptions = generationOptions;
    myExplodedPathProperty = explodedPathProperty;
    myJarPathProperty = jarPathProperty;
  }

  public ModuleChunk getChunk() {
    return myChunk;
  }

  public GenerationOptions getGenerationOptions() {
    return myGenerationOptions;
  }

  public String getExplodedPathProperty() {
    return myExplodedPathProperty;
  }

  public String getJarPathProperty() {
    return myJarPathProperty;
  }

  public Module getContainingModule() {
    return myContainingModule;
  }

  public String getConfigurationName() {
    return myConfigurationName;
  }

  public abstract String getConfigurationName(CompoundBuildInstruction instruction);

  @NonNls
  public abstract String getBuildExplodedTargetName(String configurationName);

  @NonNls
  public abstract String getBuildJarTargetName(String configurationName);

  @NonNls
  public abstract String getExplodedPathProperty(String configurationName);

  @NonNls
  public abstract String getJarPathProperty(String configurationName);

  public BuildConfiguration getBuildConfiguration() {
    return myBuildConfiguration;
  }
}
