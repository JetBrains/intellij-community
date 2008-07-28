/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.compiler;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * A set of utility methods for working with paths
 */
public class CompilerPaths {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.compiler.CompilerPaths");
  private static volatile String ourSystemPath;
  /**
   * Returns a directory
   * @param project
   * @param compiler
   * @return a directory where compiler may generate files. All generated files are not deleted when the application exits
   */
  public static File getGeneratedDataDirectory(Project project, Compiler compiler) {
    //noinspection HardCodedStringLiteral
    File dir = new File(getGeneratedDataDirectory(project), compiler.getDescription().replaceAll("\\s+", "_"));
    if (!project.isDefault()) {
      dir.mkdirs();
    }
    return dir;
  }

  /**
   * @param project
   * @return a root directory where generated files for various compilers are stored
   */
  public static File getGeneratedDataDirectory(Project project) {
    //noinspection HardCodedStringLiteral
    File dir = new File(getCompilerSystemDirectory(project), ".generated");
    if (!project.isDefault()) {
      dir.mkdirs();
    }
    return dir;
  }

  /**
   * @param project
   * @return a root directory where compiler caches for the given project are stored
   */
  public static File getCacheStoreDirectory(final Project project) {
    //noinspection HardCodedStringLiteral
    final File cacheStoreDirectory = new File(getCompilerSystemDirectory(project), ".caches");
    if (!project.isDefault()) {
      cacheStoreDirectory.mkdirs();
    }
    return cacheStoreDirectory;
  }

  /**
   * @param project
   * @return a directory under IDEA "system" directory where all files related to compiler subsystem are stored (such as compiler caches or generated files)
   */
  public static File getCompilerSystemDirectory(Project project) {
    //noinspection HardCodedStringLiteral
    final File compilerProjectSystemDir = new File(getCompilerSystemDirectory(), project.getName() + "." + project.getLocationHash());
    if (!project.isDefault()) {
      compilerProjectSystemDir.mkdirs();
    }
    return compilerProjectSystemDir;
  }

  public static File getCompilerSystemDirectory() {
    //noinspection HardCodedStringLiteral
    final String systemPath = ourSystemPath != null? ourSystemPath : (ourSystemPath = PathUtil.getCanonicalPath(PathManager.getSystemPath()));
    final File compilerSystemDir = new File(systemPath, "compiler");
    compilerSystemDir.mkdirs();
    return compilerSystemDir;
  }

  /**
   * @param module
   * @param forTestClasses true if directory for test sources, false - for sources.
   * @return a directory to which the sources (or test sources depending on the second partameter) should be compiled.
   * Null is returned if output directory is not specified or is not valid
   */
  @Nullable
  public static VirtualFile getModuleOutputDirectory(final Module module, boolean forTestClasses) {
    final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    VirtualFile outPath;
    if (forTestClasses) {
      final VirtualFile path = compilerModuleExtension.getCompilerOutputPathForTests();
      if (path != null) {
        outPath = path;
      }
      else {
        outPath = compilerModuleExtension.getCompilerOutputPath();
      }
    }
    else {
      outPath = compilerModuleExtension.getCompilerOutputPath();
    }
    if (outPath == null) {
      return null;
    }
    if (!outPath.isValid()) {
      LOG.info("Requested output path for module " + module.getName() + " is not valid");
      return null;
    }
    return outPath;
  }

  /**
   * The same as {@link #getModuleOutputDirectory} but returns String.
   * The method still returns a non-null value if the output path is specified in Settings but does not exist on disk.
   */
  @Nullable
  public static String getModuleOutputPath(final Module module, boolean forTestClasses) {
    final String outPathUrl;
    final Application application = ApplicationManager.getApplication();
    if (forTestClasses) {
      if (application.isDispatchThread()) {
        final String url = CompilerModuleExtension.getInstance(module).getCompilerOutputUrlForTests();
        outPathUrl = (url != null) ? url : CompilerModuleExtension.getInstance(module).getCompilerOutputUrl();
      }
      else {
        outPathUrl = application.runReadAction(new Computable<String>() {
          public String compute() {
            final String url = CompilerModuleExtension.getInstance(module).getCompilerOutputUrlForTests();
            return (url != null) ? url : CompilerModuleExtension.getInstance(module).getCompilerOutputUrl();
          }
        });
      }
    }
    else { // for ordinary classes
      if (application.isDispatchThread()) {
        outPathUrl = CompilerModuleExtension.getInstance(module).getCompilerOutputUrl();
      }
      else {
        outPathUrl = application.runReadAction(new Computable<String>() {
          public String compute() {
            return CompilerModuleExtension.getInstance(module).getCompilerOutputUrl();
          }
        });
      }
    }
    if (outPathUrl != null) {
      return VirtualFileManager.extractPath(outPathUrl);
    }
    return null;
  }

  @NonNls
  public static String getGenerationOutputPath(IntermediateOutputCompiler compiler, Module module, final boolean forTestSources) {
    final String generatedCompilerDirectoryPath = getGeneratedDataDirectory(module.getProject(), compiler).getPath();
    //noinspection HardCodedStringLiteral
    final String moduleDir = module.getName().replaceAll("\\s+", "_") + "." + Integer.toHexString(module.getModuleFilePath().hashCode());
    return generatedCompilerDirectoryPath.replace(File.separatorChar, '/') + "/" + moduleDir + "/" + (forTestSources? "test" : "production");
  }
}
