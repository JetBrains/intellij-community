// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.build.Standalone;
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsPathVariablesConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "SameParameterValue"})
public class JpsBootstrapMain {

  private static final String PROJECT_HOME_ENV = "JPS_BOOTSTRAP_PROJECT_HOME";

  @SuppressWarnings("ConfusingArgumentToVarargsMethod")
  public static void main(String[] args) throws Throwable {
    long startTime = System.currentTimeMillis();

    String projectHomeString = System.getenv(PROJECT_HOME_ENV);
    if (projectHomeString == null) {
      System.err.println("Please set " + PROJECT_HOME_ENV + " environment variable");
      System.exit(1);
    }

    Path projectHome = Path.of(projectHomeString);
    Path buildDir = projectHome.resolve("out").resolve("jps-bootstrap");

    JpsModel model = JpsElementFactory.getInstance().createModel();
    JpsPathVariablesConfiguration pathVariablesConfiguration =
      JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.getGlobal());
    pathVariablesConfiguration.addPathVariable(
      "MAVEN_REPOSITORY",
      FileUtilRt.toSystemIndependentName(buildDir.resolve("m2").toAbsolutePath().toString()));

    System.setProperty("kotlin.incremental.compilation", "true");
    System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "true");

    Map<String, String> pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.getGlobal());
    JpsProjectLoader.loadProject(model.getProject(), pathVariables, projectHome.toString());
    System.out.println(
      "Loaded project " + projectHome + ": " +
        model.getProject().getModules().size() + " modules, " +
        model.getProject().getLibraryCollection().getLibraries().size() + " libraries in " +
        (System.currentTimeMillis() - startTime) + " ms");

    long buildStart = System.currentTimeMillis();

    addSdk(model, "corretto-11", System.getProperty("java.home"));

    String url = "file://" + FileUtilRt.toSystemIndependentName(buildDir.resolve("out").toString());
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(model.getProject()).setOutputUrl(url);

    System.setProperty(JpsGroovycRunner.GROOVYC_IN_PROCESS, "true");
    System.setProperty(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY, "false");
    System.setProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "true");
    System.setProperty(GlobalOptions.LOG_DIR_OPTION, buildDir.resolve("log").toString());
    System.out.println("Log: " + System.getProperty(GlobalOptions.LOG_DIR_OPTION));

    JpsModule module = model.getProject().getModules()
      .stream()
      .filter(m -> "intellij.idea.community.build".equals(m.getName()))
      .findFirst().orElseThrow();

    final boolean[] errors = {false};

    Path dataStorageRoot = buildDir.resolve("jps-build-data");
    Standalone.runBuild(
      () -> model,
      dataStorageRoot.toFile(),
      false,
      //            setOf("intellij.platform.util"),
      ContainerUtil.set(module.getName()),
      false,
      Collections.emptyList(),
      false,
      msg -> {
        BuildMessage.Kind kind = msg.getKind();
        //if (kind == BuildMessage.Kind.ERROR || kind == BuildMessage.Kind.INTERNAL_BUILDER_ERROR || kind == BuildMessage.Kind.PROGRESS) {
        if (kind == BuildMessage.Kind.ERROR || kind == BuildMessage.Kind.INTERNAL_BUILDER_ERROR) {
          System.out.println(kind + " " + msg.getMessageText());
        }

        if (kind == BuildMessage.Kind.ERROR || kind == BuildMessage.Kind.INTERNAL_BUILDER_ERROR) {
          errors[0] = true;
        }
      }
    );

    System.out.println("Finished build in " + (System.currentTimeMillis() - buildStart) + " ms");

    if (errors[0]) {
      System.err.println("Build finished with errors");
      System.exit(1);
    }

    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService
      .dependencies(module)
      .runtimeOnly()
      .productionOnly()
      .recursively()
      .withoutSdk();

    List<URL> roots = new ArrayList<>();
    for (File file : enumerator.classes().getRoots()) {
      URL toURL = file.toURI().toURL();
      roots.add(toURL);
    }

    try (URLClassLoader classloader = new URLClassLoader(roots.toArray(new URL[0]), ClassLoader.getSystemClassLoader())) {
      Class<?> mainClass = classloader.loadClass("org.jetbrains.intellij.build.ExampleGroovyMain");

      MethodHandles.lookup()
        .findStatic(mainClass, "main", MethodType.methodType(Void.TYPE, String[].class))
        .invokeExact(args);
    }
  }

  private static List<String> readModulesFromReleaseFile(Path jdkDir) throws IOException {
    Path releaseFile = jdkDir.resolve("release");
    Properties p = new Properties();
    try (InputStream is = Files.newInputStream(releaseFile)) {
      p.load(is);
    }
    String jbrBaseUrl = URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR +
      FileUtil.toSystemIndependentName(jdkDir.toFile().getAbsolutePath()) +
      URLUtil.JAR_SEPARATOR;
    String modules = p.getProperty("MODULES");
    return ContainerUtil.map(StringUtil.split(StringUtil.unquoteString(modules), " "), s -> jbrBaseUrl + s);
  }

  private static void addSdk(JpsModel model, String sdkName, String sdkHome) throws IOException {
    JpsJavaExtensionService.getInstance().addJavaSdk(model.getGlobal(), sdkName, sdkHome);
    JpsLibrary additionalSdk = model.getGlobal().getLibraryCollection().findLibrary(sdkName);
    if (additionalSdk == null) {
      throw new IllegalStateException("SDK " + sdkHome + " was not found");
    }

    for (String moduleUrl : readModulesFromReleaseFile(Path.of(sdkHome))) {
      additionalSdk.addRoot(moduleUrl, JpsOrderRootType.COMPILED);
    }
  }
}
