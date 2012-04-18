/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Map;

public abstract class CompilerConfiguration {
  // need this flag for profiling purposes. In production code is always set to 'true'
  public static final boolean MAKE_ENABLED = true;

  public static CompilerConfiguration getInstance(Project project) {
    return project.getComponent(CompilerConfiguration.class);
  }

  public abstract boolean isExcludedFromCompilation(VirtualFile virtualFile);

  public abstract boolean isResourceFile(VirtualFile virtualFile);

  public abstract boolean isResourceFile(String path);

  public abstract void addResourceFilePattern(String namePattern) throws MalformedPatternException;

  public abstract boolean isAddNotNullAssertions();

  public abstract void setAddNotNullAssertions(boolean enabled);

  public abstract boolean isAnnotationProcessorsEnabled();

  public abstract void setAnnotationProcessorsEnabled(boolean enableAnnotationProcessors);

  public abstract boolean isObtainProcessorsFromClasspath();

  public abstract void setObtainProcessorsFromClasspath(boolean obtainProcessorsFromClasspath);

  public abstract String getProcessorPath();

  public abstract void setProcessorsPath(String processorsPath);

  public abstract Map<String, String> getAnnotationProcessorsMap();

  public abstract void setAnnotationProcessorsMap(Map<String, String> map);

  public abstract void setAnotationProcessedModules(Map<Module, String> modules);

  public abstract Map<Module, String> getAnotationProcessedModules();

  public abstract boolean isAnnotationProcessingEnabled(Module module);

  public abstract String getGeneratedSourceDirName(Module module);

}