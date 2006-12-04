package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.FileSet;
import com.intellij.compiler.ant.taskdefs.Include;
import com.intellij.compiler.ant.taskdefs.Path;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 16, 2004
 */
// todo: move path variables properties and jdk home properties into te generated property file
public class BuildProperties extends CompositeGenerator {
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

  public BuildProperties(Project project, final GenerationOptions genOptions) {
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

  private void createJdkGenerators(final Project project) {
    final ProjectJdk[] jdks = getUsedJdks(project);

    if (jdks.length > 0) {
      add(new Comment(CompilerBundle.message("generated.ant.build.jdk.definitions.comment")), 1);

      for (final ProjectJdk jdk : jdks) {
        if (jdk.getHomeDirectory() == null) {
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

        final File binPath = toCanonicalFile(new File(jdk.getBinPath()));
        add(new Property(BuildProperties.getJdkBinProperty(jdkName), propertyRef(jdkHomeProperty) + "/" + FileUtil.getRelativePath(homeDir, binPath).replace(File.separatorChar, '/')), 1);

        final Path jdkPath = new Path(getJdkPathId(jdkName));
        jdkPath.add(fileSet);
        add(jdkPath);
      }
    }

    final ProjectJdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    add(new Property(PROPERTY_PROJECT_JDK_HOME, projectJdk != null? propertyRef(getJdkHomeProperty(projectJdk.getName())) : ""), 1);
    add(new Property(PROPERTY_PROJECT_JDK_BIN, projectJdk != null? propertyRef(getJdkBinProperty(projectJdk.getName())) : ""));
    add(new Property(PROPERTY_PROJECT_JDK_CLASSPATH, projectJdk != null? getJdkPathId(projectJdk.getName()) : ""));
  }

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

  public static @NonNls String getPropertyFileName(Project project) {
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
    return new File(project.getProjectFilePath()).getParentFile();
  }

  private static @NonNls String convertName(@NonNls final String name) {
    //noinspection HardCodedStringLiteral
    return JDOMUtil.escapeText(name.replaceAll("\"", "").replaceAll("\\s+", "_").toLowerCase());
  }

  public static @NonNls String getPathMacroProperty(@NonNls String pathMacro) {
    return "path.variable." + convertName(pathMacro);
  }

  // J2EE
  public static @NonNls String getJ2EEExplodedPathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".dir.exploded";
  }

  public static @NonNls String getJ2EEExplodedPathProperty() {
    return "j2ee.dir.exploded";
  }

  public static @NonNls String getJ2EEJarPathProperty() {
    return "j2ee.path.jar";
  }

  public static @NonNls String getJ2EEJarPathProperty(@NonNls String moduleName) {
    return convertName(moduleName) + ".path.jar";
  }

  public static @NonNls String getJ2EEBuildTargetName(@NonNls String moduleName) {
    return "j2ee.build."+convertName(moduleName);
  }

  public static @NonNls String getJ2EEExplodedBuildTargetName(@NonNls String moduleName) {
    return "j2ee.build.exploded."+convertName(moduleName);
  }

  public static @NonNls String getJ2EEJarBuildTargetName(@NonNls String moduleName) {
    return "j2ee.build.jar."+convertName(moduleName);
  }

  public static @NonNls String getTempDirForModuleProperty(@NonNls String moduleName) {
    return "tmp.dir."+convertName(moduleName);
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
}
