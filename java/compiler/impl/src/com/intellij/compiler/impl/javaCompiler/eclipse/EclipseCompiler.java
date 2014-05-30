/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.Set;

public class EclipseCompiler implements BackendCompiler {
  private final Project myProject;
  private static final String COMPILER_CLASS_NAME = "org.eclipse.jdt.core.compiler.batch.BatchCompiler";
  @NonNls private static final String PATH_TO_COMPILER_JAR = findJarPah();

  private static String findJarPah() {
    try {
      final Class<?> aClass = Class.forName(COMPILER_CLASS_NAME);
      final String path = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
      if (path != null) {
        return path;
      }
    }
    catch (ClassNotFoundException ignored) {
    }

    File dir = new File(PathManager.getLibPath());
    File[] jars = dir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.startsWith("ecj-") && name.endsWith(".jar");
      }
    });
    return jars.length == 0 ? dir + "/ecj-*.jar" : jars[0].getPath();
  }

  public EclipseCompiler(Project project) {
    myProject = project;
  }

  public static boolean isInitialized() {
    File file = new File(PATH_TO_COMPILER_JAR);
    return file.exists();
  }

  @NotNull
  public String getId() { // used for externalization
    return JavaCompilers.ECLIPSE_ID;
  }

  @NotNull
  public String getPresentableName() {
    return CompilerBundle.message("compiler.eclipse.name");
  }

  @NotNull
  public Configurable createConfigurable() {
    return new EclipseCompilerConfigurable(EclipseCompilerConfiguration.getOptions(myProject, EclipseCompilerConfiguration.class));
  }

  @NotNull
  @Override
  public Set<FileType> getCompilableFileTypes() {
    return Collections.<FileType>singleton(StdFileTypes.JAVA);
  }
}
