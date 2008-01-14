package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.FileSet;
import com.intellij.compiler.ant.taskdefs.Include;
import com.intellij.compiler.ant.taskdefs.Path;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 16, 2004
 */
// todo: move path variables properties and jdk home properties into te generated property file
public class BuildPropertiesImpl extends BuildProperties {

  public BuildPropertiesImpl(Project project, final GenerationOptions genOptions) {
    add(new Property(getPropertyFileName(project)));

    //noinspection HardCodedStringLiteral
    add(new Comment(CompilerBundle.message("generated.ant.build.disable.tests.property.comment"), new Property(PROPERTY_SKIP_TESTS, "true")));
    final JavacSettings javacSettings = JavacSettings.getInstance(project);
    if (genOptions.enableFormCompiler) {
      add(new Comment(
        CompilerBundle.message("generated.ant.build.form.compiler.comment", ApplicationNamesInfo.getInstance().getFullProductName())), 1);
      //noinspection HardCodedStringLiteral
      add(new Comment("  javac2.jar; jdom.jar; asm.jar; asm-commons.jar"));
      //noinspection HardCodedStringLiteral
      add(new Tag("taskdef", new Pair[] {
        new Pair<String, String>("name", "javac2"),
        new Pair<String, String>("classname", "com.intellij.ant.Javac2"),
      }));
    }

    add(new Comment(CompilerBundle.message("generated.ant.build.compiler.options.comment")), 1);
    //noinspection HardCodedStringLiteral
    add(new Property(PROPERTY_COMPILER_GENERATE_DEBUG_INFO, javacSettings.DEBUGGING_INFO? "on" : "off"), 1);
    //noinspection HardCodedStringLiteral
    add(new Property(PROPERTY_COMPILER_GENERATE_NO_WARNINGS, javacSettings.GENERATE_NO_WARNINGS? "on" : "off"));
    add(new Property(PROPERTY_COMPILER_ADDITIONAL_ARGS, javacSettings.ADDITIONAL_OPTIONS_STRING));
    //noinspection HardCodedStringLiteral
    add(new Property(PROPERTY_COMPILER_MAX_MEMORY, Integer.toString(javacSettings.MAXIMUM_HEAP_SIZE) + "m"));

    add(new IgnoredFiles());

    if (CompilerExcludes.isAvailable(project)) {
      add(new CompilerExcludes(project, genOptions));
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

    final Generator globalLibs = factory.create(registrar.getLibraryTable(), null,
                                                CompilerBundle.message("generated.ant.build.global.libraries.comment"));
    if (globalLibs != null) {
      add(globalLibs);
    }

    for (final LibraryTable table : registrar.getCustomLibraryTables()) {
      if (table.getLibraries().length != 0) {
        final Generator appServerLibs = factory.create(table, null, table.getPresentation().getDisplayName(true));
        if (appServerLibs != null){
          add(appServerLibs);
        }
      }
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
        final SdkType sdkType = jdk.getSdkType();
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
        final String[] urls = jdk.getRootProvider().getUrls(OrderRootType.COMPILATION_CLASSES);
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
          add(new Property(BuildProperties.getJdkBinProperty(jdkName), propertyRef(jdkHomeProperty) + "/" + FileUtil.toSystemIndependentName(relativePath)), 1);
        } else {
          add(new Property(BuildProperties.getJdkBinProperty(jdkName), FileUtil.toSystemIndependentName(binPath.getPath())), 1);
        }

        final Path jdkPath = new Path(getJdkPathId(jdkName));
        jdkPath.add(fileSet);
        add(jdkPath);
      }
    }

    final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    add(new Property(PROPERTY_PROJECT_JDK_HOME, projectJdk != null? propertyRef(getJdkHomeProperty(projectJdk.getName())) : ""), 1);
    add(new Property(PROPERTY_PROJECT_JDK_BIN, projectJdk != null? propertyRef(getJdkBinProperty(projectJdk.getName())) : ""));
    add(new Property(PROPERTY_PROJECT_JDK_CLASSPATH, projectJdk != null? getJdkPathId(projectJdk.getName()) : ""));
  }

}
