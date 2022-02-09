// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.impl.java;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import javax.tools.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The latest version of ecj batch compiler can be found here:
 * http://download.eclipse.org/eclipse/downloads/
 */
public final class EclipseCompilerTool extends JavaCompilingTool {
  private static final String JAR_FILE_NAME_PREFIX = "ecj-";
  private static final String JAR_FILE_NAME_SUFFIX = ".jar";
  private String myVersion;
  @Override
  public @NotNull String getId() {
    return JavaCompilers.ECLIPSE_ID;
  }

  @Override
  public @NotNull String getAlternativeId() {
    return JavaCompilers.ECLIPSE_EMBEDDED_ID;
  }

  @Override
  public @NotNull String getDescription() {
    String version = myVersion;
    if (version == null) {
      version = "";
      JavaCompiler compiler = findCompiler();
      Path file = compiler == null ? null : PathManager.getJarForClass(compiler.getClass());
      if (file != null) {
        String name = file.getFileName().toString();
        if (name.startsWith(JAR_FILE_NAME_PREFIX) && name.endsWith(JAR_FILE_NAME_SUFFIX)) {
          version = " " + name.substring(JAR_FILE_NAME_PREFIX.length(), name.length() - JAR_FILE_NAME_SUFFIX.length());
        }
      }
      myVersion = version;
    }
    return "Eclipse compiler" + version;
  }

  @Override
  public @NotNull JavaCompiler createCompiler() throws CannotCreateJavaCompilerException {
    final JavaCompiler javaCompiler = findCompiler();
    if (javaCompiler == null) {
      throw new CannotCreateJavaCompilerException("Eclipse Batch Compiler was not found in classpath");
    }
    return javaCompiler;
  }

  private static @Nullable JavaCompiler findCompiler() {
    for (JavaCompiler javaCompiler : ServiceLoader.load(JavaCompiler.class)) {
      if ("EclipseCompiler".equals(StringUtil.getShortName(javaCompiler.getClass()))) {
        return javaCompiler;
      }
    }
    return null;
  }

  @Override
  public @NotNull List<File> getAdditionalClasspath() {
    File element = findEcjJarFile();
    return element == null ? Collections.emptyList() : Collections.singletonList(element);
  }

  public static @Nullable File findEcjJarFile() {
    File result = new File(PathManager.getHomePath(), "plugins/java/lib/ecj/eclipse.jar");
    if (result.exists()) {
      return result;
    }

    for (String relativeDirectoryPath : new String[]{"plugins/java/lib", "lib", "community/lib"}) {
      File lib = new File(PathManager.getHomePath(), relativeDirectoryPath);
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
