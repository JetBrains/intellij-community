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

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface ProcessorConfigProfile extends AnnotationProcessingConfiguration {
  String DEFAULT_PRODUCTION_DIR_NAME = "generated";
  String DEFAULT_TESTS_DIR_NAME = "generated_tests";

  void initFrom(ProcessorConfigProfile other);

  @NlsSafe String getName();

  void setName(String name);

  void setEnabled(boolean enabled);

  void setProcessorPath(@Nullable String processorPath);

  void setUseProcessorModulePath(boolean isModulePath);

  void setObtainProcessorsFromClasspath(boolean value);

  void setGeneratedSourcesDirectoryName(@Nullable String generatedSourcesDirectoryName, boolean forTests);

  void setProcOnly(boolean value);

  @NotNull
  Set<String> getModuleNames();

  boolean addModuleName(String name);

  boolean addModuleNames(Collection<String> names);

  boolean removeModuleName(String name);

  boolean removeModuleNames(Collection<String> names);

  void clearModuleNames();

  void clearProcessors();

  boolean addProcessor(String processor);

  boolean removeProcessor(String processor);

  String setOption(String key, String value);

  @Nullable
  String getOption(String key);

  void clearProcessorOptions();

  void setOutputRelativeToContentRoot(boolean outputRelativeToContentRoot);
}
