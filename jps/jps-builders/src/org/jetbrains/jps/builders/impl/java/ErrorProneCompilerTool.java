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
package org.jetbrains.jps.builders.impl.java;

import com.intellij.openapi.application.PathManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import javax.tools.JavaCompiler;
import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

/**
 * @author lambdamix
 */
public class ErrorProneCompilerTool extends JavaCompilingTool {
  @NotNull
  @Override
  public String getId() {
    return JavaCompilers.PWA_ID;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Javac with Project Wide Analysis";
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public JavaCompiler createCompiler() throws CannotCreateJavaCompilerException {
    try {
      Class<JavaCompiler> clazz = (Class<JavaCompiler>)Class.forName("com.google.errorprone.ErrorProneJavaCompiler");
      return clazz.newInstance();
    }
    catch (ClassNotFoundException e) {
      throw new CannotCreateJavaCompilerException(e.getMessage());
    }
    catch (InstantiationException e) {
      throw new CannotCreateJavaCompilerException(e.getMessage());
    }
    catch (IllegalAccessException e) {
      throw new CannotCreateJavaCompilerException(e.getMessage());
    }
  }

  @NotNull
  @Override
  public List<File> getAdditionalClasspath() {
    return ContainerUtil.createMaybeSingletonList(findErrorProneJarFile());
  }

  public static File findErrorProneJarFile() {
    File[] libs = {new File(PathManager.getHomePath(), "lib"), new File(PathManager.getHomePath(), "community/lib")};
    for (File lib : libs) {
      File[] children = lib.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.startsWith("error_prone_core-") && name.endsWith(".jar");
        }
      });
      if (children != null && children.length > 0) {
        return children[0];
      }
    }
    return null;
  }
}
