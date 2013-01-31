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

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package com.intellij.compiler.ant;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class BuildProperties extends CompositeGenerator {
  public static final @NonNls String TARGET_ALL = "all";
  public static final @NonNls String TARGET_BUILD_MODULES = "build.modules";
  public static final @NonNls String TARGET_CLEAN = "clean";
  public static final @NonNls String TARGET_INIT = "init";
  public static final @NonNls String TARGET_REGISTER_CUSTOM_COMPILERS = "register.custom.compilers";
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
  public static final @NonNls String PROPERTY_LIBRARIES_PATTERNS = "library.patterns";
  public static final @NonNls String PROPERTY_IDEA_HOME = "idea.home";
  public static final @NonNls String PROPERTY_JAVAC2_HOME = "javac2.home";
  public static final @NonNls String PROPERTY_JAVAC2_CLASSPATH_ID = "javac2.classpath";
  public static final @NonNls String PROPERTY_INCLUDE_JAVA_RUNTIME_FOR_INSTRUMENTATION = "javac2.instrumentation.includeJavaRuntime";

  protected abstract void createJdkGenerators(Project project);

  public static Sdk[] getUsedJdks(Project project) {
    final Set<Sdk> jdks = new HashSet<Sdk>();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
      if (jdk != null) {
        jdks.add(jdk);
      }
    }
    return jdks.toArray(new Sdk[jdks.size()]);
  }

  @NonNls
  public static String getJdkPathId(@NonNls final String jdkName) {
    return "jdk.classpath." + convertName(jdkName);
  }

  @NonNls
  public static String getModuleChunkJdkClasspathProperty(@NonNls final String moduleChunkName) {
    return "module.jdk.classpath." + convertName(moduleChunkName);
  }

  @NonNls
  public static String getModuleChunkJdkHomeProperty(@NonNls final String moduleChunkName) {
    return "module.jdk.home." + convertName(moduleChunkName);
  }

  @NonNls
  public static String getModuleChunkJdkBinProperty(@NonNls final String moduleChunkName) {
    return "module.jdk.bin." + convertName(moduleChunkName);
  }

  @NonNls
  public static String getModuleChunkCompilerArgsProperty(@NonNls final String moduleName) {
    return "compiler.args." + convertName(moduleName);
  }

  @NonNls
  public static String getLibraryPathId(@NonNls final String libraryName) {
    return "library." + convertName(libraryName) + ".classpath";
  }

  @NonNls
  public static String getJdkHomeProperty(@NonNls final String jdkName) {
    return "jdk.home." + convertName(jdkName);
  }

  @NonNls
  public static String getJdkBinProperty(@NonNls final String jdkName) {
    return "jdk.bin." + convertName(jdkName);
  }

  @NonNls
  public static String getCompileTargetName(@NonNls String moduleName) {
    return "compile.module." + convertName(moduleName);
  }

  @NonNls
  public static String getOutputPathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".output.dir";
  }

  @NonNls
  public static String getOutputPathForTestsProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".testoutput.dir";
  }

  @NonNls
  public static String getClasspathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".module.production.classpath";
  }

  @NonNls
  public static String getTestClasspathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".module.classpath";
  }

  @NonNls
  public static String getRuntimeClasspathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".runtime.production.module.classpath";
  }

  @NonNls
  public static String getTestRuntimeClasspathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".runtime.module.classpath";
  }

  @NonNls
  public static String getBootClasspathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".module.bootclasspath";
  }

  @NonNls
  public static String getSourcepathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".module.sourcepath";
  }

  @NonNls
  public static String getTestSourcepathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".module.test.sourcepath";
  }

  @NonNls
  public static String getExcludedFromModuleProperty(@NonNls String moduleName) {
    return "excluded.from.module." + convertName(moduleName);
  }

  @NonNls
  public static String getExcludedFromCompilationProperty(@NonNls String moduleName) {
    return "excluded.from.compilation." + convertName(moduleName);
  }

  @NonNls
  public static String getProjectBuildFileName(Project project) {
    return convertName(project.getName());
  }

  @NonNls
  public static String getModuleChunkBuildFileName(final ModuleChunk chunk) {
    return "module_" + convertName(chunk.getName());
  }

  @NonNls
  public static String getModuleCleanTargetName(@NonNls String moduleName) {
    return "clean.module." + convertName(moduleName);
  }

  @NonNls
  public static String getModuleChunkBasedirProperty(ModuleChunk chunk) {
    return "module." + convertName(chunk.getName()) + ".basedir";
  }

  /**
   * left for compatibility
   *
   * @param module the module to get property for
   * @return name of the property
   */
  @NonNls
  public static String getModuleBasedirProperty(Module module) {
    return "module." + convertName(module.getName()) + ".basedir";
  }

  @NonNls
  public static String getProjectBaseDirProperty() {
    return "basedir";
  }

  public static File getModuleChunkBaseDir(ModuleChunk chunk) {
    return chunk.getBaseDir();
  }

  public static File getProjectBaseDir(final Project project) {
    final VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    return VfsUtil.virtualToIoFile(baseDir);
  }

  /**
   * Convert name. All double quotes are removed and spaces are replaced with underscore.
   *
   * @param name a name to convert
   * @return a converted name
   */
  @NonNls
  public static String convertName(@NonNls final String name) {
    //noinspection HardCodedStringLiteral
    return name.replaceAll("\"", "").replaceAll("\\s+", "_").toLowerCase();
  }

  @NonNls
  public static String getPathMacroProperty(@NonNls String pathMacro) {
    return "path.variable." + convertName(pathMacro);
  }

  @NonNls
  public static String propertyRef(@NonNls String propertyName) {
    return "${" + propertyName + "}";
  }

  /**
   * Construct path relative to the specified property
   *
   * @param propertyName the property name
   * @param relativePath the relative path
   * @return the path relative to the property
   */
  @NonNls
  public static String propertyRelativePath(@NonNls String propertyName, @NonNls String relativePath) {
    return "${" + propertyName + "}/" + relativePath;
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

  @NonNls
  public static String getTempDirForModuleProperty(@NonNls String moduleName) {
    return "tmp.dir." + convertName(moduleName);
  }
}
