// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import groovy.lang.Closure;
import groovy.lang.GString;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.*;
import org.jetbrains.intellij.build.projector.ProjectorPluginKt;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class BuildContextImpl implements BuildContext {
  @Override
  public String getFullBuildNumber() {
    return getApplicationInfo().getProductCode() + "-" + getBuildNumber();
  }

  @Override
  public String getSystemSelector() {
    return productProperties.getSystemSelector(applicationInfo, buildNumber);
  }

  public static BuildContext createContext(Path communityHome,
                                           Path projectHome,
                                           ProductProperties productProperties,
                                           ProprietaryBuildTools proprietaryBuildTools,
                                           BuildOptions options) {
    return create(communityHome, projectHome, productProperties, proprietaryBuildTools, options);
  }

  public static BuildContext createContext(Path communityHome,
                                           Path projectHome,
                                           ProductProperties productProperties,
                                           ProprietaryBuildTools proprietaryBuildTools) {
    return BuildContextImpl.createContext(communityHome, projectHome, productProperties, proprietaryBuildTools, new BuildOptions());
  }

  public static BuildContext createContext(Path communityHome, Path projectHome, ProductProperties productProperties) {
    return BuildContextImpl.createContext(communityHome, projectHome, productProperties, ProprietaryBuildTools.getDUMMY(),
                                          new BuildOptions());
  }

  public static BuildContextImpl create(Path communityHome,
                                        Path projectHome,
                                        ProductProperties productProperties,
                                        ProprietaryBuildTools proprietaryBuildTools,
                                        BuildOptions options) {
    String projectHomeAsString = FileUtilRt.toSystemIndependentName(projectHome.toString());
    WindowsDistributionCustomizer windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeAsString);
    LinuxDistributionCustomizer linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeAsString);
    MacDistributionCustomizer macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeAsString);

    CompilationContextImpl compilationContext = CompilationContextImpl.create(communityHome, projectHome,
                                                                              createBuildOutputRootEvaluator(projectHomeAsString,
                                                                                                             productProperties, options),
                                                                              options);

    return new BuildContextImpl(compilationContext, productProperties, windowsDistributionCustomizer, linuxDistributionCustomizer,
                                macDistributionCustomizer, proprietaryBuildTools, new ConcurrentLinkedQueue<Map.Entry<Path, String>>());
  }

  private BuildContextImpl(CompilationContextImpl compilationContext,
                           ProductProperties productProperties,
                           WindowsDistributionCustomizer windowsDistributionCustomizer,
                           LinuxDistributionCustomizer linuxDistributionCustomizer,
                           MacDistributionCustomizer macDistributionCustomizer,
                           ProprietaryBuildTools proprietaryBuildTools,
                           @NotNull ConcurrentLinkedQueue<Map.Entry<Path, String>> distFiles) {
    this.compilationContext = compilationContext;
    this.global = compilationContext.getGlobal();
    this.productProperties = productProperties;
    this.distFiles = distFiles;
    this.proprietaryBuildTools = proprietaryBuildTools == null ? ProprietaryBuildTools.getDUMMY() : proprietaryBuildTools;
    this.windowsDistributionCustomizer = windowsDistributionCustomizer;
    this.linuxDistributionCustomizer = linuxDistributionCustomizer;
    this.macDistributionCustomizer = macDistributionCustomizer;

    final String number = getOptions().getBuildNumber();
    buildNumber = StringGroovyMethods.asBoolean(number) ? number : readSnapshotBuildNumber(getPaths().getCommunityHomeDir());

    XBootClassPathJarNames = productProperties.getXBootClassPathJarNames();
    bootClassPathJarNames = List.of("util.jar", "util_rt.jar");
    applicationInfo = new ApplicationInfoPropertiesImpl(getProject(), productProperties, getOptions(), getMessages()).patch(this);
    if (productProperties.getProductCode() == null && applicationInfo.getProductCode() != null) {
      productProperties.setProductCode(applicationInfo.getProductCode());
    }


    if (getSystemSelector().contains(" ")) {
      getMessages().error("System selector must not contain spaces: " + getSystemSelector());
    }


    getOptions().getBuildStepsToSkip().addAll(productProperties.getIncompatibleBuildSteps());
    if (!getOptions().getBuildStepsToSkip().isEmpty()) {
      getMessages().info("Build steps to be skipped: " + String.join(", ", getOptions().getBuildStepsToSkip()));
    }

    ProjectorPluginKt.configure(productProperties);
  }

  private BuildContextImpl(@NotNull BuildContextImpl parent,
                           @NotNull BuildMessages messages,
                           @NotNull ConcurrentLinkedQueue<Map.Entry<Path, String>> distFiles) {
    compilationContext = parent.compilationContext.cloneForContext(messages);
    this.distFiles = distFiles;
    global = compilationContext.getGlobal();
    productProperties = parent.getProductProperties();
    proprietaryBuildTools = parent.getProprietaryBuildTools();
    windowsDistributionCustomizer = parent.getWindowsDistributionCustomizer();
    linuxDistributionCustomizer = parent.getLinuxDistributionCustomizer();
    macDistributionCustomizer = parent.getMacDistributionCustomizer();

    buildNumber = parent.getBuildNumber();

    XBootClassPathJarNames = parent.getXBootClassPathJarNames();
    bootClassPathJarNames = parent.getBootClassPathJarNames();
    applicationInfo = parent.getApplicationInfo();
    builtinModulesData = parent.builtinModulesData;
  }

  @Override
  public void addDistFile(@NotNull Map.Entry<? extends Path, String> file) {
    getMessages().debug(String.valueOf(file) + " requested to be added to app resources");
    distFiles.add((Map.Entry<Path, String>)file);
  }

  @NotNull
  public Collection<Map.Entry<Path, String>> getDistFiles() {
    return List.copyOf(distFiles);
  }

  public static String readSnapshotBuildNumber(Path communityHome) {
    return Files.readString(communityHome.resolve("build.txt")).trim();
  }

  private static BiFunction<JpsProject, BuildMessages, String> createBuildOutputRootEvaluator(final String projectHome,
                                                                                              final ProductProperties productProperties,
                                                                                              final BuildOptions buildOptions) {
    return DefaultGroovyMethods.asType(new Closure<GString>(null, null) {
      public GString doCall(JpsProject project, BuildMessages messages) {
        final ApplicationInfoProperties applicationInfo =
          new ApplicationInfoPropertiesImpl(project, productProperties, buildOptions, messages);
        return projectHome + "/out/" + productProperties.getOutputDirectoryName(applicationInfo);
      }
    }, (Class<T>)BiFunction.class);
  }

  @Override
  public JpsModule findApplicationInfoModule() {
    return findRequiredModule(productProperties.getApplicationInfoModule());
  }

  @Override
  public BuildOptions getOptions() {
    return compilationContext.getOptions();
  }

  @Override
  public BuildMessages getMessages() {
    return compilationContext.getMessages();
  }

  @Override
  public DependenciesProperties getDependenciesProperties() {
    return compilationContext.getDependenciesProperties();
  }

  @Override
  public BuildPaths getPaths() {
    return compilationContext.getPaths();
  }

  @Override
  public BundledRuntime getBundledRuntime() {
    return compilationContext.getBundledRuntime();
  }

  @Override
  public JpsProject getProject() {
    return compilationContext.getProject();
  }

  @Override
  public JpsModel getProjectModel() {
    return compilationContext.getProjectModel();
  }

  @Override
  public JpsCompilationData getCompilationData() {
    return compilationContext.getCompilationData();
  }

  @Override
  public Path getStableJavaExecutable() {
    return compilationContext.getStableJavaExecutable();
  }

  @Override
  public Path getStableJdkHome() {
    return compilationContext.getStableJdkHome();
  }

  @Override
  public Path getProjectOutputDirectory() {
    return compilationContext.getProjectOutputDirectory();
  }

  @Override
  public JpsModule findRequiredModule(String name) {
    return compilationContext.findRequiredModule(name);
  }

  public JpsModule findModule(String name) {
    return compilationContext.findModule(name);
  }

  @Override
  public String getOldModuleName(String newName) {
    return compilationContext.getOldModuleName(newName);
  }

  @Override
  public Path getModuleOutputDir(JpsModule module) {
    return compilationContext.getModuleOutputDir(module);
  }

  @Override
  public String getModuleTestsOutputPath(JpsModule module) {
    return compilationContext.getModuleTestsOutputPath(module);
  }

  @Override
  public List<String> getModuleRuntimeClasspath(JpsModule module, boolean forTests) {
    return compilationContext.getModuleRuntimeClasspath(module, forTests);
  }

  @Override
  public void notifyArtifactBuilt(Path artifactPath) {
    compilationContext.notifyArtifactWasBuilt(artifactPath);
  }

  @Override
  public void notifyArtifactWasBuilt(Path artifactPath) {
    compilationContext.notifyArtifactWasBuilt(artifactPath);
  }

  @Override
  @Nullable
  public Path findFileInModuleSources(@NotNull String moduleName, @NotNull String relativePath) {
    for (Pair<Path, String> info : getSourceRootsWithPrefixes(findRequiredModule(moduleName))) {
      if (relativePath.startsWith(info.getSecond())) {
        Path result = info.getFirst().resolve(Strings.trimStart(Strings.trimStart(relativePath, info.getSecond()), "/"));
        if (Files.exists(result)) {
          return result;
        }
      }
    }

    return null;
  }

  @NotNull
  private static List<Pair<Path, String>> getSourceRootsWithPrefixes(@NotNull JpsModule module) {
    return module.getSourceRoots().stream().filter(new Predicate<JpsModuleSourceRoot>() {
      @Override
      public boolean test(JpsModuleSourceRoot root) {
        return JavaModuleSourceRootTypes.PRODUCTION.contains(root.getRootType());
      }
    }).map(new Function<JpsModuleSourceRoot, Pair<Path, String>>() {
      @Override
      public Pair<Path, String> apply(JpsModuleSourceRoot moduleSourceRoot) {
        String prefix;
        JpsElement properties = moduleSourceRoot.getProperties();
        if (properties instanceof JavaSourceRootProperties) {
          prefix = ((JavaSourceRootProperties)properties).getPackagePrefix().replace(".", "/");
        }
        else {
          prefix = ((JavaResourceRootProperties)properties).getRelativeOutputPath();
        }

        if (!prefix.endsWith("/")) {
          prefix += "/";
        }

        return new Pair<Path, String>(Path.of(JpsPathUtil.urlToPath(moduleSourceRoot.getUrl())), Strings.trimStart(prefix, "/"));
      }
    }).collect((Collector<? super Pair<Path, String>, ?, List<Pair<Path, String>>>)Collectors.toList());
  }

  @Override
  public void signFiles(@NotNull List<? extends Path> files, @NotNull Map<String, String> options) {
    if (proprietaryBuildTools.getSignTool() == null) {
      Span.current().addEvent("files won't be signed",
                              Attributes.of(AttributeKey.stringArrayKey("files"), files.stream().map(new Closure<String>(this, this) {
                                              public String doCall(Object it) { return it.toString(); }
                                            }).collect((Collector<? super String, ?, List<String>>)Collectors.toList()), AttributeKey.stringKey("reason"),
                                            "sign tool isn't defined"));
    }
    else {
      proprietaryBuildTools.getSignTool().signFiles(files, this, options);
    }
  }

  @Override
  public boolean executeStep(String stepMessage, String stepId, final Runnable step) {
    if (getOptions().getBuildStepsToSkip().contains(stepId)) {
      Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("name"), stepMessage));
    }
    else {
      getMessages().block(stepMessage, new Closure<Void>(this, this) {
        public void doCall(Object it) {
          step.run();
        }

        public void doCall() {
          doCall(null);
        }
      });
    }

    return true;
  }

  @Override
  public void executeStep(SpanBuilder spanBuilder, String stepId, Runnable step) {
    if (getOptions().getBuildStepsToSkip().contains(stepId)) {
      spanBuilder.startSpan().addEvent("skip").end();
      return;
    }


    Span span = spanBuilder.startSpan();
    Scope scope = span.makeCurrent();
    // we cannot flush tracing after "throw e" as we have to end the current span before that
    boolean success = false;
    try {
      step.run();
      success = true;
    }
    catch (Throwable e) {
      if (e instanceof UndeclaredThrowableException) {
        e = e.getCause();
      }


      span.recordException(e);
      span.setStatus(StatusCode.ERROR, e.getMessage());
      throw e;
    }
    finally {
      try {
        scope.close();
      }
      finally {
        span.end();
      }


      if (!success) {
        // print all pending spans - after current span
        TracerProviderManager.INSTANCE.flush();
      }
    }
  }

  @Override
  public boolean shouldBuildDistributions() {
    return !getOptions().getTargetOs().toLowerCase().equals(BuildOptions.OS_NONE);
  }

  @Override
  public boolean shouldBuildDistributionForOS(String os) {
    return shouldBuildDistributions() && getOptions().getTargetOs().toLowerCase() in
    new ArrayList<String>(Arrays.asList(BuildOptions.OS_ALL, os));
  }

  @Override
  public BuildContext forkForParallelTask(String taskName) {
    return new BuildContextImpl(this, getMessages().forkForParallelTask(taskName), distFiles);
  }

  @Override
  public BuildContext createCopyForProduct(ProductProperties productProperties, Path projectHomeForCustomizers) {
    String projectHomeForCustomizersAsString = FileUtilRt.toSystemIndependentName(projectHomeForCustomizers.toString());
    WindowsDistributionCustomizer windowsDistributionCustomizer =
      productProperties.createWindowsCustomizer(projectHomeForCustomizersAsString);
    LinuxDistributionCustomizer linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeForCustomizersAsString);
    MacDistributionCustomizer macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeForCustomizersAsString);
    /**
     * FIXME compiled classes are assumed to be already fetched in the FIXME from {@link CompilationContextImpl#prepareForBuild}, please change them together
     */
    BuildOptions options = new BuildOptions();
    options.setUseCompiledClassesFromProjectOutput(true);
    CompilationContextImpl compilationContextCopy = compilationContext.createCopy(getMessages(), options, createBuildOutputRootEvaluator(
      getPaths().getProjectHome(), productProperties, options));
    BuildContextImpl copy =
      new BuildContextImpl(compilationContextCopy, productProperties, windowsDistributionCustomizer, linuxDistributionCustomizer,
                           macDistributionCustomizer, proprietaryBuildTools, new ConcurrentLinkedQueue<Map.Entry<Path, String>>());
    copy.getPaths().setArtifactDir(getPaths().getArtifactDir().resolve(productProperties.getProductCode()));
    copy.getPaths().setArtifacts(getPaths().getArtifacts() + "/" + productProperties.getProductCode());
    copy.compilationContext.prepareForBuild();
    return copy;
  }

  @Override
  public boolean includeBreakGenLibraries() {
    return isJavaSupportedInProduct();
  }

  private boolean isJavaSupportedInProduct() {
    return productProperties.getProductLayout().getBundledPluginModules().contains("intellij.java.plugin");
  }

  @Override
  public void patchInspectScript(@NotNull Path path) {
    //todo[nik] use placeholder in inspect.sh/inspect.bat file instead
    Files.writeString(path, StringGroovyMethods.replaceAll(Files.readString(path), " inspect ",
                                                           " " + getProductProperties().getInspectCommandName() + " "));
  }

  @Override
  @SuppressWarnings("SpellCheckingInspection")
  @NotNull
  public List<String> getAdditionalJvmArguments() {
    List<String> jvmArgs = new ArrayList<String>();

    String classLoader = productProperties.getClassLoader();
    if (classLoader != null) {
      jvmArgs.add("-Djava.system.class.loader=" + classLoader);
      if (classLoader.equals("com.intellij.util.lang.PathClassLoader")) {
        jvmArgs.add("-Didea.strict.classpath=true");
      }
    }


    jvmArgs.add("-Didea.vendor.name=" + applicationInfo.getShortCompanyName());

    jvmArgs.add("-Didea.paths.selector=" + getSystemSelector());

    if (productProperties.getPlatformPrefix() != null) {
      jvmArgs.add("-Didea.platform.prefix=" + productProperties.getPlatformPrefix());
    }


    jvmArgs.addAll(productProperties.getAdditionalIdeJvmArguments());

    if (productProperties.getToolsJarRequired()) {
      jvmArgs.add("-Didea.jre.check=true");
    }


    if (productProperties.getUseSplash()) {
      //noinspection SpellCheckingInspection
      jvmArgs.add("-Dsplash=true");
    }


    jvmArgs.addAll(BuildHelperKt.getCommandLineArgumentsForOpenPackages(this));

    return jvmArgs;
  }

  @Override
  public OsSpecificDistributionBuilder getOsDistributionBuilder(OsFamily os, final Path ideaProperties) {
    OsSpecificDistributionBuilder builder;
    switch (os) {
      case OsFamily.WINDOWS:
        builder = DefaultGroovyMethods.with(windowsDistributionCustomizer, new Closure<WindowsDistributionBuilder>(this, this) {
          public WindowsDistributionBuilder doCall(WindowsDistributionCustomizer it) {
            return new WindowsDistributionBuilder(BuildContextImpl.this, it, ideaProperties, String.valueOf(getApplicationInfo()));
          }

          public WindowsDistributionBuilder doCall() {
            return doCall(null);
          }
        });
        break;
      case OsFamily.LINUX:
        builder = DefaultGroovyMethods.with(linuxDistributionCustomizer, new Closure<LinuxDistributionBuilder>(this, this) {
          public LinuxDistributionBuilder doCall(LinuxDistributionCustomizer it) {
            return new LinuxDistributionBuilder(BuildContextImpl.this, it, ideaProperties);
          }

          public LinuxDistributionBuilder doCall() {
            return doCall(null);
          }
        });
        break;
      case OsFamily.MACOS:
        builder = DefaultGroovyMethods.with(macDistributionCustomizer, new Closure<MacDistributionBuilder>(this, this) {
          public MacDistributionBuilder doCall(MacDistributionCustomizer it) {
            return new MacDistributionBuilder(BuildContextImpl.this, it, ideaProperties);
          }

          public MacDistributionBuilder doCall() {
            return doCall(null);
          }
        });
        break;
    }
    return builder;
  }

  @Override
  public BuiltinModulesFileData getBuiltinModule() {
    if (getOptions().getBuildStepsToSkip().contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
      return null;
    }


    BuiltinModulesFileData data = builtinModulesData;
    if (data == null) {
      throw new IllegalStateException(
        "builtinModulesData is not set. Make sure `BuildTasksImpl.buildProvidedModuleList` was called before");
    }

    return data;
  }

  public void setBuiltinModules(BuiltinModulesFileData data) {
    if (builtinModulesData != null) {
      throw new IllegalStateException("builtinModulesData was already set");
    }


    builtinModulesData = data;
  }

  @Override
  public Function1<Set<String>, Unit> getClasspathCustomizer() {
    return classpathCustomizer;
  }

  @SuppressWarnings("unused")
  public void setClasspathCustomizer(Function1<Set<String>, Unit> classpathCustomizer) {
    this.classpathCustomizer = classpathCustomizer;
  }

  public final ProductProperties getProductProperties() {
    return productProperties;
  }

  public final WindowsDistributionCustomizer getWindowsDistributionCustomizer() {
    return windowsDistributionCustomizer;
  }

  public final LinuxDistributionCustomizer getLinuxDistributionCustomizer() {
    return linuxDistributionCustomizer;
  }

  public final MacDistributionCustomizer getMacDistributionCustomizer() {
    return macDistributionCustomizer;
  }

  public final ProprietaryBuildTools getProprietaryBuildTools() {
    return proprietaryBuildTools;
  }

  public final String getBuildNumber() {
    return buildNumber;
  }

  public List<String> getXBootClassPathJarNames() {
    return XBootClassPathJarNames;
  }

  public void setXBootClassPathJarNames(List<String> XBootClassPathJarNames) {
    this.XBootClassPathJarNames = XBootClassPathJarNames;
  }

  public List<String> getBootClassPathJarNames() {
    return bootClassPathJarNames;
  }

  public void setBootClassPathJarNames(List<String> bootClassPathJarNames) {
    this.bootClassPathJarNames = bootClassPathJarNames;
  }

  public final ApplicationInfoProperties getApplicationInfo() {
    return applicationInfo;
  }

  private final ProductProperties productProperties;
  private final WindowsDistributionCustomizer windowsDistributionCustomizer;
  private final LinuxDistributionCustomizer linuxDistributionCustomizer;
  private final MacDistributionCustomizer macDistributionCustomizer;
  private final ProprietaryBuildTools proprietaryBuildTools;
  private final String buildNumber;
  private List<String> XBootClassPathJarNames;
  private List<String> bootClassPathJarNames;
  private Function1<Set<String>, Unit> classpathCustomizer = new Function1<Set<String>, Unit>() {
    @Override
    public Unit invoke(Set<String> strings) {
      return null;
    }
  };
  private final ApplicationInfoProperties applicationInfo;
  private final JpsGlobal global;
  private final CompilationContextImpl compilationContext;
  private final ConcurrentLinkedQueue<Map.Entry<Path, String>> distFiles;
  private BuiltinModulesFileData builtinModulesData;
}
