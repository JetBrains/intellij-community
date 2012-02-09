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
package com.intellij.openapi.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * A set of utility methods for working with paths
 */
public class CompilerPaths {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.compiler.CompilerPaths");
  private static volatile String ourSystemPath;
  private static final Comparator<String> URLS_COMPARATOR = new Comparator<String>() {
    public int compare(String o1, String o2) {
      return o1.compareTo(o2);
    }
  };
  private static final String DEFAULT_GENERATED_DIR_NAME = "generated";

  /**
   * Returns a directory
   * @param project
   * @param compiler
   * @return a directory where compiler may generate files. All generated files are not deleted when the application exits
   */
  public static File getGeneratedDataDirectory(Project project, Compiler compiler) {
    //noinspection HardCodedStringLiteral
    return new File(getGeneratedDataDirectory(project), compiler.getDescription().replaceAll("\\s+", "_"));
  }

  /**
   * @param project
   * @return a root directory where generated files for various compilers are stored
   */
  public static File getGeneratedDataDirectory(Project project) {
    //noinspection HardCodedStringLiteral
    return new File(getCompilerSystemDirectory(project), ".generated");
  }

  /**
   * @param project
   * @return a root directory where compiler caches for the given project are stored
   */
  public static File getCacheStoreDirectory(final Project project) {
    //noinspection HardCodedStringLiteral
    return new File(getCompilerSystemDirectory(project), ".caches");
  }

  public static File getRebuildMarkerFile(Project project) {
    return new File(getCompilerSystemDirectory(project), "rebuild_required");
  }

  /**
   * @param project
   * @return a directory under IDEA "system" directory where all files related to compiler subsystem are stored (such as compiler caches or generated files)
   */
  public static File getCompilerSystemDirectory(Project project) {
    //noinspection HardCodedStringLiteral
    String projectName = getPresentableName(project);
    return new File(getCompilerSystemDirectory(), projectName + "." + project.getLocationHash());
  }

  private static String getPresentableName(final Project project) {
    if (project.isDefault()) {
      return project.getName();
    }

    String location = project.getLocation();
    if (location == null) return null;
    String projectName = FileUtil.toSystemIndependentName(location);
    if (projectName.endsWith("/")) {
      projectName = projectName.substring(0, projectName.length() - 1);
    }

    final int lastSlash = projectName.lastIndexOf('/');
    if (lastSlash >= 0 && lastSlash + 1 < projectName.length()) {
      projectName = projectName.substring(lastSlash + 1);
    }

    if (StringUtil.endsWithIgnoreCase(projectName, ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      projectName = projectName.substring(0, projectName.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length());
    }
    
    projectName = projectName.toLowerCase(Locale.US);
    return projectName;
  }

  public static File getCompilerSystemDirectory() {
    //noinspection HardCodedStringLiteral
    final String systemPath = ourSystemPath != null? ourSystemPath : (ourSystemPath = PathUtil.getCanonicalPath(PathManager.getSystemPath()));
    return new File(systemPath, "compiler");
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
    final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
    if (forTestClasses) {
      if (application.isDispatchThread()) {
        final String url = extension.getCompilerOutputUrlForTests();
        outPathUrl = url != null ? url : extension.getCompilerOutputUrl();
      }
      else {
        outPathUrl = application.runReadAction(new Computable<String>() {
          public String compute() {
            final String url = extension.getCompilerOutputUrlForTests();
            return url != null ? url : extension.getCompilerOutputUrl();
          }
        });
      }
    }
    else { // for ordinary classes
      if (application.isDispatchThread()) {
        outPathUrl = extension.getCompilerOutputUrl();
      }
      else {
        outPathUrl = application.runReadAction(new Computable<String>() {
          public String compute() {
            return extension.getCompilerOutputUrl();
          }
        });
      }
    }
    return outPathUrl != null? VirtualFileManager.extractPath(outPathUrl) : null;
  }

  @Nullable
  public static String getAnnotationProcessorsGenerationPath(Module module) {
    final CompilerConfiguration config = CompilerConfiguration.getInstance(module.getProject());

    final String sourceDirName = config.getGeneratedSourceDirName(module);
    if (sourceDirName != null && sourceDirName.length() > 0) {
      final String[] roots = ModuleRootManager.getInstance(module).getContentRootUrls();
      if (roots.length == 0) {
        return null;
      }
      if (roots.length > 1) {
        Arrays.sort(roots, URLS_COMPARATOR);
      }
      return VirtualFileManager.extractPath(roots[0]) + "/" + sourceDirName;
    }

    final CompilerProjectExtension extension = CompilerProjectExtension.getInstance(module.getProject());
    if (extension == null) {
      return null;
    }
    final String url = extension.getCompilerOutputUrl();
    if (url == null) {
      return null;
    }
    return VirtualFileManager.extractPath(url) + "/" + DEFAULT_GENERATED_DIR_NAME + "/" + module.getName().toLowerCase();
  }
  
  @NonNls
  public static String getGenerationOutputPath(IntermediateOutputCompiler compiler, Module module, final boolean forTestSources) {
    final String generatedCompilerDirectoryPath = getGeneratedDataDirectory(module.getProject(), compiler).getPath();
    //noinspection HardCodedStringLiteral
    final String moduleDir = module.getName().replaceAll("\\s+", "_") + "." + Integer.toHexString(module.getModuleFilePath().hashCode());
    return generatedCompilerDirectoryPath.replace(File.separatorChar, '/') + "/" + moduleDir + "/" + (forTestSources? "test" : "production");
  }
}
