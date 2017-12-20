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
package com.intellij.compiler.ant;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
// todo: move path variables properties and jdk home properties into te generated property file
public class BuildPropertiesImpl extends BuildProperties {

  public BuildPropertiesImpl(Project project, final GenerationOptions genOptions) {
    add(new Property(genOptions.getPropertiesFileName()));

    //noinspection HardCodedStringLiteral
    add(new Comment(CompilerBundle.message("generated.ant.build.disable.tests.property.comment"),
                    new Property(PROPERTY_SKIP_TESTS, "true")));
    final JpsJavaCompilerOptions javacSettings = JavacConfiguration.getOptions(project, JavacConfiguration.class);
    add(new Comment(CompilerBundle.message("generated.ant.build.compiler.options.comment")), 1);
    //noinspection HardCodedStringLiteral
    add(new Property(PROPERTY_COMPILER_GENERATE_DEBUG_INFO, javacSettings.DEBUGGING_INFO ? "on" : "off"), 1);
    //noinspection HardCodedStringLiteral
    add(new Property(PROPERTY_COMPILER_GENERATE_NO_WARNINGS, javacSettings.GENERATE_NO_WARNINGS ? "on" : "off"));
    add(new Property(PROPERTY_COMPILER_ADDITIONAL_ARGS, javacSettings.ADDITIONAL_OPTIONS_STRING));
    //noinspection HardCodedStringLiteral
    final int heapSize = CompilerConfiguration.getInstance(project).getBuildProcessHeapSize(javacSettings.MAXIMUM_HEAP_SIZE);
    add(new Property(PROPERTY_COMPILER_MAX_MEMORY, Integer.toString(heapSize) + "m"));

    add(new IgnoredFiles());

    if (CompilerExcludes.isAvailable(project)) {
      add(new CompilerExcludes(project, genOptions));
    }

    if (!genOptions.expandJarDirectories) {
      add(new LibraryPatterns(project, genOptions));
    }

    add(new CompilerResourcePatterns(project));

    if (genOptions.forceTargetJdk) {
      createJdkGenerators(project);
    }

    LibraryDefinitionsGeneratorFactory factory = new LibraryDefinitionsGeneratorFactory((ProjectEx)project, genOptions);

    final LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    final Generator projectLibs = factory.create(registrar.getLibraryTable(project), getProjectBaseDir(project),
                                                 CompilerBundle.message("generated.ant.build.project.libraries.comment"));
    if (projectLibs != null) {
      add(projectLibs);
    }

    final Generator globalLibs =
      factory.create(registrar.getLibraryTable(), null, CompilerBundle.message("generated.ant.build.global.libraries.comment"));
    if (globalLibs != null) {
      add(globalLibs);
    }

    for (final LibraryTable table : registrar.getCustomLibraryTables()) {
      if (table.getLibraries().length != 0) {
        final Generator appServerLibs = factory.create(table, null, table.getPresentation().getDisplayName(true));
        if (appServerLibs != null) {
          add(appServerLibs);
        }
      }
    }

    final ChunkCustomCompilerExtension[] customCompilers = genOptions.getCustomCompilers();
    if (genOptions.enableFormCompiler || customCompilers.length > 0) {
      add(new Comment(CompilerBundle.message("generated.ant.build.custom.compilers.comment")));
      Target register = new Target(TARGET_REGISTER_CUSTOM_COMPILERS, null, null, null);
      if (genOptions.enableFormCompiler) {
        //noinspection HardCodedStringLiteral
        add(new Property(PROPERTY_JAVAC2_HOME, propertyRelativePath(PROPERTY_IDEA_HOME, "lib")));
        Path javac2 = new Path(PROPERTY_JAVAC2_CLASSPATH_ID);
        javac2.add(new PathElement(propertyRelativePath(PROPERTY_JAVAC2_HOME, "javac2.jar")));
        javac2.add(new PathElement(propertyRelativePath(PROPERTY_JAVAC2_HOME, "jdom.jar")));
        javac2.add(new PathElement(propertyRelativePath(PROPERTY_JAVAC2_HOME, "asm-all.jar")));
        javac2.add(new PathElement(propertyRelativePath(PROPERTY_JAVAC2_HOME, "jgoodies-forms.jar")));
        add(javac2);
        //noinspection HardCodedStringLiteral
        register.add(new Tag("taskdef", Couple.of("name", "javac2"), Couple.of("classname", "com.intellij.ant.Javac2"),
                             Couple.of("classpathref", PROPERTY_JAVAC2_CLASSPATH_ID)));
        register.add(new Tag("taskdef", Couple.of("name", "instrumentIdeaExtensions"),
                             Couple.of("classname", "com.intellij.ant.InstrumentIdeaExtensions"),
                             Couple.of("classpathref", PROPERTY_JAVAC2_CLASSPATH_ID)));
      }
      if (customCompilers.length > 0) {
        for (ChunkCustomCompilerExtension ext : customCompilers) {
          ext.generateCustomCompilerTaskRegistration(project, genOptions, register);
        }
      }
      add(register);
    }
  }

  protected void createJdkGenerators(final Project project) {
    final Sdk[] jdks = getUsedJdks(project);

    if (jdks.length > 0) {
      add(new Comment(CompilerBundle.message("generated.ant.build.jdk.definitions.comment")), 1);

      for (final Sdk jdk : jdks) {
        if (jdk.getHomeDirectory() == null) {
          continue;
        }
        final SdkTypeId sdkType = jdk.getSdkType();
        if (!(sdkType instanceof JavaSdkType) || ((JavaSdkType)sdkType).getBinPath(jdk) == null) {
          continue;
        }
        final File home = VfsUtil.virtualToIoFile(jdk.getHomeDirectory());
        File homeDir;
        try {
          // use canonical path in order to resolve symlinks
          homeDir = home.getCanonicalFile();
        }
        catch (IOException e) {
          homeDir = home;
        }
        final String jdkName = jdk.getName();
        final String jdkHomeProperty = getJdkHomeProperty(jdkName);
        final FileSet fileSet = new FileSet(propertyRef(jdkHomeProperty));
        final String[] urls = jdk.getRootProvider().getUrls(OrderRootType.CLASSES);
        for (String url : urls) {
          final String path = GenerationUtils.trimJarSeparator(VirtualFileManager.extractPath(url));
          final File pathElement = new File(path);
          final String relativePath = FileUtil.getRelativePath(homeDir, pathElement);
          if (relativePath != null) {
            fileSet.add(new Include(relativePath.replace(File.separatorChar, '/')));
          }
        }

        final File binPath = toCanonicalFile(new File(((JavaSdkType)sdkType).getBinPath(jdk)));
        final String relativePath = FileUtil.getRelativePath(homeDir, binPath);
        if (relativePath != null) {
          add(new Property(BuildProperties.getJdkBinProperty(jdkName),
                           propertyRef(jdkHomeProperty) + "/" + FileUtil.toSystemIndependentName(relativePath)), 1);
        }
        else {
          add(new Property(BuildProperties.getJdkBinProperty(jdkName), FileUtil.toSystemIndependentName(binPath.getPath())), 1);
        }

        final Path jdkPath = new Path(getJdkPathId(jdkName));
        jdkPath.add(fileSet);
        add(jdkPath);
      }
    }

    final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    add(new Property(PROPERTY_PROJECT_JDK_HOME, projectJdk != null ? propertyRef(getJdkHomeProperty(projectJdk.getName())) : ""), 1);
    add(new Property(PROPERTY_PROJECT_JDK_BIN, projectJdk != null ? propertyRef(getJdkBinProperty(projectJdk.getName())) : ""));
    add(new Property(PROPERTY_PROJECT_JDK_CLASSPATH, projectJdk != null ? getJdkPathId(projectJdk.getName()) : ""));
  }
}
