/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package com.intellij.compiler.ant;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class BuildProperties extends CompositeGenerator {
  public static final @NonNls String TARGET_ALL = "all";
  public static final @NonNls String TARGET_CLEAN = "clean";
  public static final @NonNls String TARGET_INIT = "init";
  public static final @NonNls String DEFAULT_TARGET = TARGET_ALL;
  public static final @NonNls String PROPERTY_COMPILER_NAME = "compiler.name";
  public static final @NonNls String PROPERTY_COMPILER_ADDITIONAL_ARGS = "compiler.args";
  public static final @NonNls String PROPERTY_COMPILER_MAX_MEMORY = "compiler.max.memory";
  public static final @NonNls String PROPERTY_COMPILER_EXCLUDES = "compiler.excluded";
  public static final @NonNls String PROPERTY_COMPILER_RESOURCE_PATTERNS = "compiler.resources";
  public static final @NonNls String PROPERTY_IGNORED_FILES = "ignored.files";
  public static final @NonNls String PROPERTY_COMPILER_GENERATE_DEBUG_INFO = "compiler.debug";
  public static final @NonNls String PROPERTY_COMPILER_GENERATE_NO_WARNINGS = "compiler.generate.no.warnings";
  public static final @NonNls String PROPERTY_PROJECT_JDK_HOME = "project.jdk.home";
  public static final @NonNls String PROPERTY_PROJECT_JDK_BIN = "project.jdk.bin";
  public static final @NonNls String PROPERTY_PROJECT_JDK_CLASSPATH = "project.jdk.classpath";
  public static final @NonNls String PROPERTY_SKIP_TESTS = "skip.tests";

  protected abstract void createJdkGenerators(Project project);

  public static ProjectJdk[] getUsedJdks(Project project) {
    final Set<ProjectJdk> jdks = new HashSet<ProjectJdk>();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
      if (jdk != null) {
        jdks.add(jdk);
      }
    }
    return jdks.toArray(new ProjectJdk[jdks.size()]);
  }

  public static @NonNls
  String getPropertyFileName(Project project) {
    return getProjectBuildFileName(project) + ".properties";
  }

  public static @NonNls String getJdkPathId(@NonNls final String jdkName) {
    return "jdk.classpath." + convertName(jdkName);
  }

  public static @NonNls String getModuleChunkJdkClasspathProperty(@NonNls final String moduleChunkName) {
    return "module.jdk.classpath." + convertName(moduleChunkName);
  }

  public static @NonNls String getModuleChunkJdkHomeProperty(@NonNls final String moduleChunkName) {
    return "module.jdk.home." + convertName(moduleChunkName);
  }

  public static @NonNls String getModuleChunkJdkBinProperty(@NonNls final String moduleChunkName) {
    return "module.jdk.bin." + convertName(moduleChunkName);
  }

  public static @NonNls String getModuleChunkCompilerArgsProperty(@NonNls final String moduleName) {
    return "compiler.args." + convertName(moduleName);
  }

  public static @NonNls String getLibraryPathId(@NonNls final String libraryName) {
    return "library." + convertName(libraryName) + ".classpath";
  }

  public static @NonNls String getJdkHomeProperty(@NonNls final String jdkName) {
    return "jdk.home." + convertName(jdkName);
  }

  public static @NonNls String getJdkBinProperty(@NonNls final String jdkName) {
    return "jdk.bin." + convertName(jdkName);
  }

  public static @NonNls String getCompileTargetName(@NonNls String moduleName) {
    return "compile.module." + convertName(moduleName);
  }

  public static @NonNls String getOutputPathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".output.dir";
  }

  public static @NonNls String getOutputPathForTestsProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".testoutput.dir";
  }

  public static @NonNls String getClasspathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".module.classpath";
  }

  public static @NonNls String getRuntimeClasspathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".runtime.module.classpath";
  }

  public static @NonNls String getBootClasspathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".module.bootclasspath";
  }

  public static @NonNls String getSourcepathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".module.sourcepath";
  }

  public static @NonNls String getTestSourcepathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".module.test.sourcepath";
  }

  public static @NonNls String getExcludedFromModuleProperty(@NonNls String moduleName) {
    return "excluded.from.module." + convertName(moduleName);
  }

  public static @NonNls String getExcludedFromCompilationProperty(@NonNls String moduleName) {
    return "excluded.from.compilation." + convertName(moduleName);
  }

  public static @NonNls String getProjectBuildFileName(Project project) {
    return convertName(project.getName());
  }

  public static @NonNls String getModuleChunkBuildFileName(final ModuleChunk chunk) {
    return "module_" + convertName(chunk.getName());
  }

  public static @NonNls String getModuleCleanTargetName(@NonNls String moduleName) {
    return "clean.module." + convertName(moduleName);
  }

  public static @NonNls String getModuleChunkBasedirProperty(ModuleChunk chunk) {
    return "module." + convertName(chunk.getName()) + ".basedir";
  }

  /**
   * left for compatibility
   */
  public static @NonNls String getModuleBasedirProperty(Module module) {
    return "module." + convertName(module.getName()) + ".basedir";
  }

  public static @NonNls String getProjectBaseDirProperty() {
    return "basedir";
  }

  public static File getModuleChunkBaseDir(ModuleChunk chunk) {
    return chunk.getBaseDir();
  }

  public static File getProjectBaseDir(final Project project) {
    return VfsUtil.virtualToIoFile(project.getBaseDir());
  }

  public static @NonNls String convertName(@NonNls final String name) {
    //noinspection HardCodedStringLiteral
    return JDOMUtil.escapeText(name.replaceAll("\"", "").replaceAll("\\s+", "_").toLowerCase());
  }

  public static @NonNls String getPathMacroProperty(@NonNls String pathMacro) {
    return "path.variable." + convertName(pathMacro);
  }

  public static @NonNls String propertyRef(@NonNls String propertyName) {
    return "${" + propertyName + "}";
  }

  public static File toCanonicalFile(final File file) {
    File canonicalFile;
    try {
      canonicalFile = file.getCanonicalFile();
    }
    catch (IOException e) {
      canonicalFile = file;
    }
    return canonicalFile;
  }

  public static @NonNls String getTempDirForModuleProperty(@NonNls String moduleName) {
    return "tmp.dir."+ convertName(moduleName);
  }
}