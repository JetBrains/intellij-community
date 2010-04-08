/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.JarClasspathHelper;
import com.intellij.util.PathsList;

import java.io.File;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class JarProgramPatcher extends JavaProgramPatcher {

  @Override
  public void patchJavaParameters(Executor executor, RunProfile configuration, JavaParameters javaParameters) {

    if (!(configuration instanceof RunConfiguration) || !Registry.is("jar.build")) return;

    String path = JarClasspathHelper.getJarsPath(((RunConfiguration)configuration).getProject());
    if (path == null) return;

    PathsList classPath = javaParameters.getClassPath();
    List<String> pathList = classPath.getPathList();
    for (String s: pathList) {
      String name = new File(s).getName();
      VirtualFile jarFile = JarClasspathHelper.getJarFile(path, name);
      if (jarFile != null) {
        classPath.remove(s);
        classPath.add(jarFile);
      }
    }
  }

}
