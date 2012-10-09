package org.jetbrains.jps.builders;

import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.CompileScopeImpl;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class CompileScopeTestBuilder {
  private BuildType myBuildType;
  private Set<BuildTargetType<?>> myTargetTypes = new HashSet<BuildTargetType<?>>();
  private Set<BuildTarget<?>> myTargets = new HashSet<BuildTarget<?>>();

  public static CompileScopeTestBuilder rebuild() {
    return new CompileScopeTestBuilder(BuildType.PROJECT_REBUILD);
  }

  public static CompileScopeTestBuilder make() {
    return new CompileScopeTestBuilder(BuildType.MAKE);
  }

  public static CompileScopeTestBuilder recompile() {
    return new CompileScopeTestBuilder(BuildType.FORCED_COMPILATION);
  }

  private CompileScopeTestBuilder(BuildType buildType) {
    myBuildType = buildType;
  }

  public BuildType getBuildType() {
    return myBuildType;
  }

  public CompileScopeTestBuilder allModules() {
    myTargetTypes.addAll(JavaModuleBuildTargetType.ALL_TYPES);
    return this;
  }

  public CompileScopeTestBuilder module(JpsModule module) {
    myTargets.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
    myTargets.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.TEST));
    return this;
  }

  public CompileScopeTestBuilder allArtifacts() {
    myTargetTypes.add(ArtifactBuildTargetType.INSTANCE);
    return this;
  }

  public CompileScopeTestBuilder artifact(JpsArtifact artifact) {
    myTargets.add(new ArtifactBuildTarget(artifact));
    return this;
  }

  public CompileScope build() {
    return new CompileScopeImpl(myBuildType != BuildType.MAKE, myTargetTypes, myTargets, Collections.<BuildTarget<?>,Set<File>>emptyMap());
  }

  public CompileScopeTestBuilder all() {
    myTargetTypes.addAll(BuilderRegistry.getInstance().getTargetTypes());
    return this;
  }

  public CompileScopeTestBuilder artifacts(JpsArtifact[] artifacts) {
    for (JpsArtifact artifact : artifacts) {
      myTargets.add(new ArtifactBuildTarget(artifact));
    }
    return this;
  }
}
