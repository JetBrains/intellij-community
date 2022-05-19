// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.Formats;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SystemProperties;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.util.Node;
import groovy.util.XmlParser;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.*;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader;
import org.jetbrains.intellij.build.dependencies.Jdk11Downloader;
import org.jetbrains.intellij.build.impl.logging.BuildMessagesHandler;
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl;
import org.jetbrains.intellij.build.kotlin.KotlinBinaries;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsPathVariablesConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;

public final class CompilationContextImpl implements CompilationContext {
  @SuppressWarnings("GrUnresolvedAccess")
  public static CompilationContextImpl create(Path communityHome, Path projectHome, final String defaultOutputRoot) {
    //noinspection GroovyAssignabilityCheck
    return create(communityHome, projectHome, DefaultGroovyMethods.asType(new Closure<String>(null, null) {
      public String doCall(Object p, Object m) { return defaultOutputRoot; }
    }, (Class<T>)BiFunction.class), new BuildOptions());
  }

  public static CompilationContextImpl create(final Path communityHome,
                                              Path projectHome,
                                              BiFunction<JpsProject, BuildMessages, String> buildOutputRootEvaluator,
                                              BuildOptions options) {
    // This is not a proper place to initialize tracker for downloader
    // but this is the only place which is called in most build scripts
    BuildDependenciesDownloader.TRACER = BuildDependenciesOpenTelemetryTracer.INSTANCE;

    BuildMessagesImpl messages = BuildMessagesImpl.create();
    if (DefaultGroovyMethods.any(new ArrayList<String>(Arrays.asList("platform/build-scripts", "bin/idea.properties", "build.txt")),
                                 new Closure(null, null) {
                                   public Object doCall(String it) { return !Files.exists(communityHome.resolve(it)); }

                                   public Object doCall() {
                                     return doCall(null);
                                   }
                                 })) {
      messages.error(
        "communityHome (" + String.valueOf(communityHome) + ") doesn\'t point to a directory containing IntelliJ Community sources");
    }


    printEnvironmentDebugInfo();

    logFreeDiskSpace(messages, projectHome, "before downloading dependencies");
    KotlinBinaries kotlinBinaries = new KotlinBinaries(communityHome, options, messages);
    JpsModel model = loadProject(projectHome, kotlinBinaries, messages);
    Map<String, String> oldToNewModuleName =
      DefaultGroovyMethods.plus(loadModuleRenamingHistory(projectHome, messages), loadModuleRenamingHistory(communityHome, messages));

    CompilationContextImpl context =
      new CompilationContextImpl(model, communityHome, projectHome, messages, oldToNewModuleName, buildOutputRootEvaluator, options);
    defineJavaSdk(context);
    context.prepareForBuild();

    // not as part of prepareForBuild because prepareForBuild may be called several times per each product or another flavor
    // (see createCopyForProduct)
    TracerProviderManager.INSTANCE.setOutput(context.getPaths().getLogDir().resolve("trace.json"));
    messages.setDebugLogPath(context.getPaths().getLogDir().resolve("debug.log"));

    // This is not a proper place to initialize logging
    // but this is the only place which is called in most build scripts
    BuildMessagesHandler.initLogging(messages);

    return context;
  }

  private static void defineJavaSdk(final CompilationContext context) {
    Path homePath = Jdk11Downloader.getJdkHome(context.getPaths().getBuildDependenciesCommunityRoot());
    final String jbrHome = toCanonicalPath(homePath.toString());
    String jbrVersionName = "11";

    JdkUtils.INSTANCE.defineJdk(context.getProjectModel().getGlobal(), jbrVersionName, jbrHome, context.getMessages());
    readModulesFromReleaseFile(context.getProjectModel(), jbrVersionName, jbrHome);

    DefaultGroovyMethods.each(DefaultGroovyMethods.toSet(DefaultGroovyMethods.findAll(
      DefaultGroovyMethods.collect(context.getProjectModel().getProject().getModules(), new Closure<String>(null, null) {
        public String doCall(JpsModule it) {
          final JpsSdkReference<JpsDummyElement> reference = it.getSdkReference(JpsJavaSdkType.INSTANCE);
          return (reference == null ? null : reference.getSdkName());
        }

        public String doCall() {
          return doCall(null);
        }
      }), new Closure<Boolean>(null, null) {
        public Boolean doCall(String it) { return it != null; }

        public Boolean doCall() {
          return doCall(null);
        }
      })), new Closure<List<String>>(null, null) {
      public List<String> doCall(Object sdkName) {
        int vendorPrefixEnd = ((String)sdkName).indexOf("-");
        String sdkNameWithoutVendor = (String)vendorPrefixEnd != -1 ? ((String)sdkName).substring(vendorPrefixEnd + 1) : sdkName;
        if (!sdkNameWithoutVendor.equals("11")) {
          throw new IllegalStateException("Project model at " +
                                          String.valueOf(context.getPaths().getProjectHomeDir()) +
                                          " requested SDK " +
                                          sdkNameWithoutVendor +
                                          ", but only \'11\' is supported as SDK in intellij project");
        }


        if (context.getProjectModel().getGlobal().getLibraryCollection().findLibrary((String)sdkName) == null) {
          JdkUtils.INSTANCE.defineJdk(context.getProjectModel().getGlobal(), (String)sdkName, jbrHome, context.getMessages());
          return readModulesFromReleaseFile(context.getProjectModel(), (String)sdkName, jbrHome);
        }
      }
    });
  }

  private static List<String> readModulesFromReleaseFile(JpsModel model, String sdkName, String sdkHome) {
    final JpsLibrary additionalSdk = model.getGlobal().getLibraryCollection().findLibrary(sdkName);
    if (additionalSdk == null) {
      throw new IllegalStateException("Sdk '" + sdkName + "' is not found");
    }


    final List<String> urls =
      DefaultGroovyMethods.collect(additionalSdk.getRoots(JpsOrderRootType.COMPILED), new Closure<String>(null, null) {
        public String doCall(JpsLibraryRoot it) { return it.getUrl(); }

        public String doCall() {
          return doCall(null);
        }
      });
    return DefaultGroovyMethods.each(JdkUtils.INSTANCE.readModulesFromReleaseFile(Path.of(sdkHome)), new Closure<Void>(null, null) {
      public void doCall(String it) {
        if (!urls.contains(it)) {
          additionalSdk.addRoot(it, JpsOrderRootType.COMPILED);
        }
      }

      public void doCall() {
        doCall(null);
      }
    });
  }

  @SuppressWarnings({"GrUnresolvedAccess", "GroovyAssignabilityCheck"})
  private static Map<String, String> loadModuleRenamingHistory(Path projectHome, BuildMessages messages) {
    Path modulesXml = projectHome.resolve(".idea/modules.xml");
    if (!Files.exists(modulesXml)) {
      messages.error("Incorrect project home: " + String.valueOf(modulesXml) + " doesn\'t exist");
    }


    try {
      Node root = new XmlParser().parse(stream);
      Object renamingHistoryTag = DefaultGroovyMethods.find(root.component, new Closure<Boolean>(null, null) {
        public Boolean doCall(Object it) { return it.name.equals("ModuleRenamingHistory"); }

        public Boolean doCall() {
          return doCall(null);
        }
      });
      final LinkedHashMap<String, String> mapping = new LinkedHashMap<String, String>();
      DefaultGroovyMethods.each((renamingHistoryTag == null ? null : renamingHistoryTag.module), new Closure(null, null) {
        public Object doCall(Object it) { return mapping[it. @old -name] =it.@new - name; }

        public Object doCall() {
          return doCall(null);
        }
      });
      return ((Map<String, String>)(mapping));
    }
  }

  private CompilationContextImpl(JpsModel model,
                                 Path communityHome,
                                 Path projectHome,
                                 BuildMessages messages,
                                 Map<String, String> oldToNewModuleName,
                                 BiFunction<JpsProject, BuildMessages, String> buildOutputRootEvaluator,
                                 BuildOptions options) {
    this.projectModel = model;
    this.project = model.getProject();
    this.global = model.getGlobal();
    this.options = options;
    this.messages = messages;
    this.oldToNewModuleName = oldToNewModuleName;
    this.newToOldModuleName =
      DefaultGroovyMethods.asType(DefaultGroovyMethods.collectEntries(oldToNewModuleName, new Closure<List<String>>(this, this) {
        public List<String> doCall(Object oldName, Object newName) { return new ArrayList<String>(Arrays.asList(newName, oldName)); }
      }), Map.class);

    List<JpsModule> modules = model.getProject().getModules();
    Map.Entry<String, JpsModule>[] nameToModule = new Map.Entry<String, JpsModule>[modules.size()];
    for (int i = 0; ; i < modules.size() ;){
      JpsModule module = modules.get(i);
      nameToModule[i] = Map.entry(module.getName(), module);
    }

    this.nameToModule = Map.ofEntries(nameToModule);

    final String path = options.getOutputRootPath();
    String buildOutputRoot = StringGroovyMethods.asBoolean(path) ? path : buildOutputRootEvaluator.apply(project, messages);
    Path logDir = options.getLogPath() != null ? Path.of(options.getLogPath()) : Path.of(buildOutputRoot, "log");
    paths = new BuildPathsImpl(communityHome, projectHome, buildOutputRoot, logDir);

    this.dependenciesProperties = new DependenciesProperties(this);
    this.bundledRuntime = new BundledRuntimeImpl(this);

    stableJdkHome = Jdk11Downloader.getJdkHome(paths.getBuildDependenciesCommunityRoot());
    stableJavaExecutable = Jdk11Downloader.getJavaExecutable(stableJdkHome);
  }

  public CompilationContextImpl createCopy(BuildMessages messages,
                                           BuildOptions options,
                                           BiFunction<JpsProject, BuildMessages, String> buildOutputRootEvaluator) {
    CompilationContextImpl copy =
      new CompilationContextImpl(projectModel, paths.getCommunityHomeDir(), paths.getProjectHomeDir(), messages, oldToNewModuleName,
                                 buildOutputRootEvaluator, options);
    copy.setCompilationData(compilationData);
    return copy;
  }

  private CompilationContextImpl(BuildMessages messages, CompilationContextImpl context) {
    this.projectModel = context.getProjectModel();
    this.project = context.getProject();
    this.global = context.getGlobal();
    this.options = context.getOptions();
    this.messages = messages;
    this.oldToNewModuleName = context.getOldToNewModuleName();
    this.newToOldModuleName = context.getNewToOldModuleName();
    this.nameToModule = context.getNameToModule();
    this.paths = context.getPaths();
    this.compilationData = context.getCompilationData();
    this.dependenciesProperties = context.getDependenciesProperties();
    this.bundledRuntime = context.getBundledRuntime();
    this.stableJavaExecutable = context.getStableJavaExecutable();
    this.stableJdkHome = context.getStableJdkHome();
  }

  public CompilationContextImpl cloneForContext(BuildMessages messages) {
    return new CompilationContextImpl(messages, this);
  }

  private static JpsModel loadProject(Path projectHome, KotlinBinaries kotlinBinaries, BuildMessages messages) {
    final JpsModel model = JpsElementFactory.getInstance().createModel();
    JpsPathVariablesConfiguration pathVariablesConfiguration =
      JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.getGlobal());
    if (kotlinBinaries.isCompilerRequired()) {
      final Path kotlinCompilerHome = kotlinBinaries.getKotlinCompilerHome();
      System.setProperty("jps.kotlin.home", kotlinCompilerHome.toFile().getAbsolutePath());
      pathVariablesConfiguration.addPathVariable("KOTLIN_BUNDLED", String.valueOf(kotlinCompilerHome));
    }

    pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", FileUtilRt.toSystemIndependentName(
      new File(SystemProperties.getUserHome(), ".m2/repository").getAbsolutePath()));

    Map<String, String> pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.getGlobal());
    JpsProjectLoader.loadProject(model.getProject(), pathVariables, projectHome);
    messages.info("Loaded project " +
                  String.valueOf(projectHome) +
                  ": " +
                  String.valueOf(model.getProject().getModules().size()) +
                  " modules, " +
                  String.valueOf(model.getProject().getLibraryCollection().getLibraries().size()) +
                  " libraries");
    return ((JpsModel)(model));
  }

  public void prepareForBuild() {
    checkCompilationOptions();
    NioFiles.deleteRecursively(paths.getLogDir());
    Files.createDirectories(paths.getLogDir());
    compilationData =
      new JpsCompilationData(new File(paths.getBuildOutputRoot(), ".jps-build-data"), paths.getLogDir().resolve("compilation.log").toFile(),
                             System.getProperty("intellij.build.debug.logging.categories", ""));

    String projectArtifactsDirName = "project-artifacts";
    overrideProjectOutputDirectory();

    final String baseArtifactsOutput = (String)getPaths().getBuildOutputRoot() + "/" + projectArtifactsDirName;
    DefaultGroovyMethods.each(JpsArtifactService.getInstance().getArtifacts(project), new Closure<GString>(this, this) {
      public GString doCall(JpsArtifact it) {
        return setOutputPath(it, baseArtifactsOutput + "/" + PathUtilRt.getFileName(it.getOutputPath()));
      }

      public GString doCall() {
        return doCall(null);
      }
    });

    if (!options.getUseCompiledClassesFromProjectOutput()) {
      messages.info("Incremental compilation: " + options.getIncrementalCompilation());
    }


    if (options.getIncrementalCompilation()) {
      System.setProperty("kotlin.incremental.compilation", "true");
    }


    suppressWarnings(project);
    exportModuleOutputProperties();

    TracerProviderManager.INSTANCE.flush();
    ConsoleSpanExporter.setPathRoot(paths.getBuildOutputDir());

    /**
     * FIXME should be called lazily yet it breaks {@link TestingTasks#runTests}, needs investigation
     */
    CompilationTasks.create(this).reuseCompiledClassesIfProvided();
  }

  private GString overrideProjectOutputDirectory() {
    if (options.getProjectClassesOutputDirectory() != null && !options.getProjectClassesOutputDirectory().isEmpty()) {
      return setProjectOutputDirectory0(this, options.getProjectClassesOutputDirectory());
    }
    else if (options.getUseCompiledClassesFromProjectOutput()) {
      File outputDir = getProjectOutputDirectory();
      if (!outputDir.exists()) {
        messages.error(BuildOptions.USE_COMPILED_CLASSES_PROPERTY +
                       " is enabled, but the project output directory " +
                       String.valueOf(outputDir) +
                       " doesn\'t exist");
      }
    }
    else {
      return setProjectOutputDirectory0(this, getPaths().getBuildOutputRoot() + "/classes");
    }
  }

  @Override
  public File getProjectOutputDirectory() {
    return JpsPathUtil.urlToFile(JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).getOutputUrl());
  }

  public void setProjectOutputDirectory(final String outputDirectory) {
    String url = (String)"file://" + FileUtilRt.toSystemIndependentName(outputDirectory);
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).setOutputUrl(url);
  }

  public void exportModuleOutputProperties() {
    // defines Ant properties which are used by jetbrains.antlayout.datatypes.IdeaModuleBase class to locate module outputs
    // still used, please get rid of LayoutBuilder usages
    for (JpsModule module : project.getModules()) {
      for (boolean test : new ArrayList<Boolean>(Arrays.asList(true, false))) {
        DefaultGroovyMethods.each(
          DefaultGroovyMethods.findAll(new ArrayList<String>(Arrays.asList(module.getName(), getOldModuleName(module.getName()))),
                                       new Closure<Boolean>(this, this) {
                                         public Boolean doCall(String it) { return it != null; }

                                         public Boolean doCall() {
                                           return doCall(null);
                                         }
                                       }), new Closure<Void>(this, this) {
            public void doCall(String it) {
              String outputPath = getOutputPath(module, test);
              if (outputPath != null) {
                LayoutBuilder.getAnt().getProject()
                  .setProperty(StringGroovyMethods.asBoolean("module." + it + ".output." + test) ? "test" : "main", outputPath);
              }
            }

            public void doCall() {
              doCall(null);
            }
          });
      }
    }
  }

  private void checkCompilationOptions() {
    if (options.getUseCompiledClassesFromProjectOutput() && options.getIncrementalCompilation()) {
      messages.warning(
        "\'" + BuildOptions.USE_COMPILED_CLASSES_PROPERTY + "\' is specified, so \'incremental compilation\' option will be ignored");
      options.setIncrementalCompilation(false);
    }

    if (options.getPathToCompiledClassesArchive() != null && options.getIncrementalCompilation()) {
      messages.warning("Paths to the compiled project output is specified, so 'incremental compilation' option will be ignored");
      options.setIncrementalCompilation(false);
    }

    if (options.getPathToCompiledClassesArchive() != null && options.getUseCompiledClassesFromProjectOutput()) {
      messages.warning(
        "\'" + BuildOptions.USE_COMPILED_CLASSES_PROPERTY + "\' is specified, so the archive with compiled project output won\'t be used");
      options.setPathToCompiledClassesArchive(null);
    }

    if (options.getPathToCompiledClassesArchivesMetadata() != null && options.getIncrementalCompilation()) {
      messages.warning("Paths to the compiled project output metadata is specified, so 'incremental compilation' option will be ignored");
      options.setIncrementalCompilation(false);
    }

    if (options.getPathToCompiledClassesArchivesMetadata() != null && options.getUseCompiledClassesFromProjectOutput()) {
      messages.warning("\'" +
                       BuildOptions.USE_COMPILED_CLASSES_PROPERTY +
                       "\' is specified, so the archive with the compiled project output metadata won\'t be used to fetch compile output");
      options.setPathToCompiledClassesArchivesMetadata(null);
    }

    if (options.getIncrementalCompilation() && "false".equals(System.getProperty("teamcity.build.branch.is_default"))) {
      messages.warning(
        "Incremental builds for feature branches have no sense because JPS caches are out of date, so 'incremental compilation' option will be ignored");
      options.setIncrementalCompilation(false);
    }
  }

  private static void suppressWarnings(JpsProject project) {
    JpsJavaCompilerOptions compilerOptions =
      JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).getCurrentCompilerOptions();
    compilerOptions.GENERATE_NO_WARNINGS = true;
    compilerOptions.DEPRECATION = false;
    compilerOptions.ADDITIONAL_OPTIONS_STRING = compilerOptions.ADDITIONAL_OPTIONS_STRING.replace("-Xlint:unchecked", "");
  }

  @Override
  public JpsModule findRequiredModule(@NotNull String name) {
    JpsModule module = findModule(name);
    if (module == null) {
      messages.error("Cannot find required module \'" + name + "\' in the project");
    }

    return module;
  }

  public JpsModule findModule(@NotNull String name) {
    String actualName;
    if (oldToNewModuleName.containsKey(name)) {
      actualName = oldToNewModuleName.get(name);
      messages.warning("Old module name \'" + name + "\' is used in the build scripts; use the new name \'" + actualName + "\' instead");
    }
    else {
      actualName = name;
    }

    return nameToModule.get(actualName);
  }

  @Override
  @Nullable
  public String getOldModuleName(String newName) {
    return newToOldModuleName.get(newName);
  }

  @Override
  @NotNull
  public Path getModuleOutputDir(@NotNull JpsModule module) {
    String url = JpsJavaExtensionService.getInstance().getOutputUrl(module, false);
    if (url == null) {
      messages.error("Output directory for \'" + module.getName() + "\' isn\'t set");
    }

    return Path.of(JpsPathUtil.urlToPath(url));
  }

  @Override
  public String getModuleTestsOutputPath(JpsModule module) {
    return getOutputPath(module, true);
  }

  private String getOutputPath(JpsModule module, boolean forTests) {
    File outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(module, forTests);
    if (outputDirectory == null) {
      messages.warning("Output directory for \'" + module.getName() + "\' isn\'t set");
    }

    return (outputDirectory == null ? null : outputDirectory.getAbsolutePath());
  }

  @Override
  public List<String> getModuleRuntimeClasspath(JpsModule module, final boolean forTests) {
    JpsJavaDependenciesEnumerator enumerator = DefaultGroovyMethods.with(JpsJavaExtensionService.dependencies(module).recursively(),
                                                                         new Closure<JpsJavaDependenciesEnumerator>(this, this) {
                                                                           public JpsJavaDependenciesEnumerator doCall(
                                                                             JpsJavaDependenciesEnumerator it) {
                                                                             return forTests
                                                                                    ? withoutSdk()
                                                                                    : it;
                                                                           }

                                                                           public JpsJavaDependenciesEnumerator doCall() {
                                                                             return doCall(null);
                                                                           }
                                                                         }).includedIn(JpsJavaClasspathKind.runtime(forTests));
    return DefaultGroovyMethods.collect(enumerator.classes().getRoots(), new Closure<String>(this, this) {
      public String doCall(File it) { return it.getAbsolutePath(); }

      public String doCall() {
        return doCall(null);
      }
    });
  }

  @Override
  public void notifyArtifactBuilt(String artifactPath) {
    notifyArtifactWasBuilt(Path.of(artifactPath).toAbsolutePath().normalize());
  }

  @Override
  public void notifyArtifactWasBuilt(Path file) {
    if (options.getBuildStepsToSkip().contains(BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP)) {
      return;
    }


    boolean isRegularFile = Files.isRegularFile(file);

    String targetDirectoryPath = "";
    if (file.getParent().startsWith(paths.getArtifactDir())) {
      targetDirectoryPath = FileUtilRt.toSystemIndependentName(paths.getArtifactDir().relativize(file.getParent()).toString());
    }


    if (!isRegularFile) {
      targetDirectoryPath = (StringGroovyMethods.asBoolean(targetDirectoryPath) ? targetDirectoryPath + "/" : "") + file.getFileName();
    }


    String pathToReport = file.toString();
    if (StringGroovyMethods.asBoolean(targetDirectoryPath)) {
      pathToReport += "=>" + targetDirectoryPath;
    }

    messages.artifactBuilt(pathToReport);
  }

  private static String toCanonicalPath(String path) {
    return FileUtilRt.toSystemIndependentName(new File(path).getCanonicalPath());
  }

  public static void logFreeDiskSpace(BuildMessages buildMessages, final Path dir, String phase) {
    buildMessages.debug("Free disk space " +
                        phase +
                        ": " +
                        Formats.formatFileSize(Files.getFileStore(dir).getUsableSpace()) +
                        " (on disk containing " +
                        String.valueOf(dir) +
                        ")");
  }

  public static void printEnvironmentDebugInfo() {
    // print it to the stdout since TeamCity will remove any sensitive fields from build log automatically
    // don't write it to debug log file!
    final Map<String, String> env = System.getenv();
    for (String key : DefaultGroovyMethods.toSorted(env.keySet())) {
      DefaultGroovyMethods.println(this, "ENV " + key + " = " + env.get(key));
    }


    final Properties properties = System.getProperties();
    for (String propertyName : DefaultGroovyMethods.toSorted(properties.keySet())) {
      DefaultGroovyMethods.println(this, "PROPERTY " + propertyName + " = " + String.valueOf(properties.get(propertyName)));
    }
  }

  public final BuildOptions getOptions() {
    return options;
  }

  public final BuildMessages getMessages() {
    return messages;
  }

  public final BuildPaths getPaths() {
    return paths;
  }

  public final JpsProject getProject() {
    return project;
  }

  public final JpsGlobal getGlobal() {
    return global;
  }

  public final JpsModel getProjectModel() {
    return projectModel;
  }

  public final Map<String, String> getOldToNewModuleName() {
    return oldToNewModuleName;
  }

  public final Map<String, String> getNewToOldModuleName() {
    return newToOldModuleName;
  }

  public final Map<String, JpsModule> getNameToModule() {
    return nameToModule;
  }

  public final DependenciesProperties getDependenciesProperties() {
    return dependenciesProperties;
  }

  public final BundledRuntime getBundledRuntime() {
    return bundledRuntime;
  }

  public JpsCompilationData getCompilationData() {
    return compilationData;
  }

  public void setCompilationData(JpsCompilationData compilationData) {
    this.compilationData = compilationData;
  }

  public final Path getStableJavaExecutable() {
    return stableJavaExecutable;
  }

  public final Path getStableJdkHome() {
    return stableJdkHome;
  }

  private final BuildOptions options;
  private final BuildMessages messages;
  private final BuildPaths paths;
  private final JpsProject project;
  private final JpsGlobal global;
  private final JpsModel projectModel;
  private final Map<String, String> oldToNewModuleName;
  private final Map<String, String> newToOldModuleName;
  private final Map<String, JpsModule> nameToModule;
  private final DependenciesProperties dependenciesProperties;
  private final BundledRuntime bundledRuntime;
  private JpsCompilationData compilationData;
  private final Path stableJavaExecutable;
  private final Path stableJdkHome;

  private static <Value extends String> Value setOutputPath(JpsArtifact propOwner, Value outputPath) {
    propOwner.setOutputPath(outputPath);
    return outputPath;
  }

  private static <Value extends String> Value setProjectOutputDirectory0(CompilationContextImpl propOwner, Value outputDirectory) {
    propOwner.setProjectOutputDirectory(outputDirectory);
    return outputDirectory;
  }
}

