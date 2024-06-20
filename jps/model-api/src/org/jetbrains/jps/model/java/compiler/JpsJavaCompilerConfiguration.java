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
package org.jetbrains.jps.model.java.compiler;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface JpsJavaCompilerConfiguration extends JpsElement {
  boolean isAddNotNullAssertions();
  @ApiStatus.Internal
  void setAddNotNullAssertions(boolean addNotNullAssertions);

  List<String> getNotNullAnnotations();
  @ApiStatus.Internal
  void setNotNullAnnotations(List<String> notNullAnnotations);

  boolean isClearOutputDirectoryOnRebuild();
  @ApiStatus.Internal
  void setClearOutputDirectoryOnRebuild(boolean clearOutputDirectoryOnRebuild);

  @NotNull
  JpsCompilerExcludes getCompilerExcludes();

  @ApiStatus.Internal
  @NotNull
  JpsCompilerExcludes getValidationExcludes();

  @ApiStatus.Internal
  @NotNull
  JpsValidationConfiguration getValidationConfiguration();

  @ApiStatus.Internal
  void setValidationConfiguration(boolean validateOnBuild, @NotNull Set<String> disabledValidators);

  @NotNull
  ProcessorConfigProfile getDefaultAnnotationProcessingProfile();
  @ApiStatus.Internal
  ProcessorConfigProfile addAnnotationProcessingProfile();

  /**
   * @return a list of currently configured profiles excluding default one
   */
  @NotNull
  Collection<ProcessorConfigProfile> getAnnotationProcessingProfiles();

  /**
   * @return annotation profile with which the given module is associated
   */
  @NotNull
  ProcessorConfigProfile getAnnotationProcessingProfile(JpsModule module);

  @ApiStatus.Internal
  void addResourcePattern(String pattern);
  List<String> getResourcePatterns();
  boolean isResourceFile(@NotNull File file, @NotNull File srcRoot);

  @Nullable
  String getByteCodeTargetLevel(String moduleName);

  @ApiStatus.Internal
  void setProjectByteCodeTargetLevel(String level);
  @ApiStatus.Internal
  void setModuleByteCodeTargetLevel(String moduleName, String level);

  boolean useReleaseOption();
  @ApiStatus.Internal
  void setUseReleaseOption(boolean useReleaseOption);

  @NotNull
  String getJavaCompilerId();
  void setJavaCompilerId(@NotNull String compiler);

  @NotNull
  JpsJavaCompilerOptions getCompilerOptions(@NotNull String compilerId);
  @ApiStatus.Internal
  void setCompilerOptions(@NotNull String compilerId, @NotNull JpsJavaCompilerOptions options);

  @NotNull
  JpsJavaCompilerOptions getCurrentCompilerOptions();

}
