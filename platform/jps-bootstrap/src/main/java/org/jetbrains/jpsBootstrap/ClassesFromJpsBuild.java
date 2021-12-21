// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.build.Standalone;
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.*;
import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.fatal;

public class ClassesFromJpsBuild {
  public static final String CLASSES_FROM_JPS_BUILD_ENV_NAME = "JPS_BOOTSTRAP_CLASSES_FROM_JPS_BUILD";

  public static void buildModule(JpsModule module, Path ideaHomePath, JpsModel model, Path jpsBootstrapWorkDir) throws Exception {
    // Workaround for KTIJ-19065
    System.setProperty(PathManager.PROPERTY_HOME_PATH, ideaHomePath.toString());

    System.setProperty("kotlin.incremental.compilation", "true");
    System.setProperty("kotlin.daemon.enabled", "false");
    System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "true");

    System.setProperty(JpsGroovycRunner.GROOVYC_IN_PROCESS, "true");
    System.setProperty(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY, "false");
    System.setProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "true");
    System.setProperty(GlobalOptions.LOG_DIR_OPTION, jpsBootstrapWorkDir.resolve("log").toString());

    String url = "file://" + FileUtilRt.toSystemIndependentName(jpsBootstrapWorkDir.resolve("out").toString());
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(model.getProject()).setOutputUrl(url);

    info("Compilation log directory: " + System.getProperty(GlobalOptions.LOG_DIR_OPTION));

    // kotlin.util.compiler-dependencies downloads all dependencies required for running Kotlin JPS compiler
    // see org.jetbrains.kotlin.idea.artifacts.KotlinArtifactsFromSources
    runBuild(model, jpsBootstrapWorkDir, "kotlin.util.compiler-dependencies");

    runBuild(model, jpsBootstrapWorkDir, module.getName());
  }

  private static void runBuild(JpsModel model, Path workDir, String moduleName) throws Exception {
    final long buildStart = System.currentTimeMillis();
    final AtomicReference<String> firstError = new AtomicReference<>();

    Path dataStorageRoot = workDir.resolve("jps-build-data");
    final Set<String> moduleNames = model.getProject().getModules().stream().map(JpsNamedElement::getName).collect(Collectors.toUnmodifiableSet());
    Standalone.runBuild(
      () -> model,
      dataStorageRoot.toFile(),
      false,
      ContainerUtil.set(moduleName),
      false,
      Collections.emptyList(),
      false,
      msg -> {
        BuildMessage.Kind kind = msg.getKind();
        String text = msg.toString();

        switch (kind) {
          case PROGRESS:
            verbose(text);
            break;
          case WARNING:
            warn(text);
          case ERROR:
          case INTERNAL_BUILDER_ERROR:
            error(text);
            break;
          default:
            if (!msg.getMessageText().isBlank()) {
              if (moduleNames.contains(msg.getMessageText())) {
                verbose(text);
              }
              else {
                info(text);
              }
            }
            break;
        }

        if ((kind == BuildMessage.Kind.ERROR || kind == BuildMessage.Kind.INTERNAL_BUILDER_ERROR)) {
          firstError.compareAndSet(null, text);
        }
      }
    );

    System.out.println("Finished building '" + moduleName + "' in " + (System.currentTimeMillis() - buildStart) + " ms");

    String firstErrorText = firstError.get();
    if (firstErrorText != null) {
      fatal("Build finished with errors. First error:\n" + firstErrorText);
    }
  }
}
