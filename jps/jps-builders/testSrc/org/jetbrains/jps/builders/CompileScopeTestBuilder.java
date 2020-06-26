// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.SmartHashSet;
import gnu.trove.THashSet;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.CompileScopeImpl;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.TargetTypeRegistry;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

public final class CompileScopeTestBuilder {
  private final boolean myForceBuild;
  private final Set<BuildTargetType<?>> myTargetTypes = new HashSet<>();
  private final Set<BuildTarget<?>> myTargets = new HashSet<>();
  private final LinkedHashMap<BuildTarget<?>, Set<File>> myFiles = new LinkedHashMap<>();

  public static CompileScopeTestBuilder rebuild() {
    return new CompileScopeTestBuilder(true);
  }

  public static CompileScopeTestBuilder make() {
    return new CompileScopeTestBuilder(false);
  }

  public static CompileScopeTestBuilder recompile() {
    return new CompileScopeTestBuilder(true);
  }

  private CompileScopeTestBuilder(boolean forceBuild) {
    myForceBuild = forceBuild;
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

  public CompileScopeTestBuilder targetTypes(BuildTargetType<?>... targets) {
    myTargetTypes.addAll(Arrays.asList(targets));
    return this;
  }

  public CompileScopeTestBuilder file(BuildTarget<?> target, String path) {
    Set<File> files = myFiles.get(target);
    if (files == null) {
      files = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
      myFiles.put(target, files);
    }
    files.add(new File(path));
    return this;
  }

  public CompileScope build() {
    final Collection<BuildTargetType<?>> typesToForceBuild;
    if (myForceBuild) {
      typesToForceBuild = new SmartHashSet<>();
      typesToForceBuild.addAll(myTargetTypes);
      for (BuildTarget<?> target : myTargets) {
        typesToForceBuild.add(target.getTargetType());
      }
    }
    else {
      typesToForceBuild = Collections.emptyList();
    }
    return new CompileScopeImpl(myTargetTypes, typesToForceBuild, myTargets, myFiles);
  }

  /**
   * Add all targets in the project to the scope. May lead to unpredictable results if some plugins add targets your test doesn't expect.
   *
   * @deprecated use {@link #allModules()} instead or directly add required target types via {@link #targetTypes}
   */
  public CompileScopeTestBuilder all() {
    myTargetTypes.addAll(TargetTypeRegistry.getInstance().getTargetTypes());
    return this;
  }

  public CompileScopeTestBuilder artifacts(JpsArtifact... artifacts) {
    for (JpsArtifact artifact : artifacts) {
      myTargets.add(new ArtifactBuildTarget(artifact));
    }
    return this;
  }
}
