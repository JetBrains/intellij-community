/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import javax.tools.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The latest version of ecj batch compiler can be found here:
 * http://download.eclipse.org/eclipse/downloads/
 *
 * @author nik
 */
public class EclipseCompilerTool extends JavaCompilingTool {

  private static final String JAR_FILE_NAME_PREFIX = "ecj-";
  private static final String JAR_FILE_NAME_SUFFIX = ".jar";
  private String myVersion;
  @NotNull
  @Override
  public String getId() {
    return JavaCompilers.ECLIPSE_ID;
  }

  @Nullable
  @Override
  public String getAlternativeId() {
    return JavaCompilers.ECLIPSE_EMBEDDED_ID;
  }

  @Override
  public boolean isCompilerTreeAPISupported() {
    return false;
  }

  @NotNull
  @Override
  public String getDescription() {
    String version = myVersion;
    if (version == null) {
      version = "";
      //final File file = findEcjJarFile();
      final JavaCompiler compiler = findCompiler();
      final String path = compiler != null? PathManager.getJarPathForClass(compiler.getClass()) : null;
      final File file = path != null? new File(path) : null;
      if (file != null) {
        final String name = file.getName();
        if (name.startsWith(JAR_FILE_NAME_PREFIX) && name.endsWith(JAR_FILE_NAME_SUFFIX)) {
          version = " " + name.substring(JAR_FILE_NAME_PREFIX.length(), name.length() - JAR_FILE_NAME_SUFFIX.length());
        }
      }
      myVersion = version;
    }
    return "Eclipse compiler" + version;
  }

  @NotNull
  @Override
  public JavaCompiler createCompiler() throws CannotCreateJavaCompilerException {
    final JavaCompiler javaCompiler = findCompiler();
    if (javaCompiler == null) {
      throw new CannotCreateJavaCompilerException("Eclipse Batch Compiler was not found in classpath");
    }
    return javaCompiler;
  }

  @Nullable
  private static JavaCompiler findCompiler() {
    for (JavaCompiler javaCompiler : ServiceLoader.load(JavaCompiler.class)) {
      if ("EclipseCompiler".equals(StringUtil.getShortName(javaCompiler.getClass()))) {
        return javaCompiler;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<File> getAdditionalClasspath() {
    return ContainerUtil.createMaybeSingletonList(findEcjJarFile());
  }

  @Nullable
  public static File findEcjJarFile() {
    File[] libs = {new File(PathManager.getHomePath(), "lib"), new File(PathManager.getHomePath(), "community/lib")};
    for (File lib : libs) {
      File[] children = lib.listFiles((dir, name) -> name.startsWith(JAR_FILE_NAME_PREFIX) && name.endsWith(JAR_FILE_NAME_SUFFIX));
      if (children != null && children.length > 0) {
        return children[0];
      }
    }
    return null;
  }

  @Override
  public List<String> getDefaultCompilerOptions() {
    return Collections.singletonList("-noExit");
  }
}
