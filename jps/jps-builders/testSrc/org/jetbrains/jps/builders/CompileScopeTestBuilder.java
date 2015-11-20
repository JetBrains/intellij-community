/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders;

import com.intellij.openapi.util.io.FileUtil;
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

/**
 * @author nik
 */
public class CompileScopeTestBuilder {
  private final boolean myForceBuild;
  private final Set<BuildTargetType<?>> myTargetTypes = new HashSet<BuildTargetType<?>>();
  private final Set<BuildTarget<?>> myTargets = new HashSet<BuildTarget<?>>();
  private LinkedHashMap<BuildTarget<?>, Set<File>> myFiles = new LinkedHashMap<BuildTarget<?>, Set<File>>();

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
      files = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
      myFiles.put(target, files);
    }
    files.add(new File(path));
    return this;
  }

  public CompileScope build() {
    Collection<BuildTargetType<?>> typesToForceBuild = myForceBuild ? myTargetTypes : Collections.<BuildTargetType<?>>emptyList();
    return new CompileScopeImpl(myTargetTypes, typesToForceBuild, myTargets, myFiles);
  }

  public CompileScopeTestBuilder all() {
    myTargetTypes.addAll(TargetTypeRegistry.getInstance().getTargetTypes());
    return this;
  }

  public CompileScopeTestBuilder artifacts(JpsArtifact[] artifacts) {
    for (JpsArtifact artifact : artifacts) {
      myTargets.add(new ArtifactBuildTarget(artifact));
    }
    return this;
  }
}
