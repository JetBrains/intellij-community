// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.build.Standalone;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.*;

public class JpsBuild {
  public static final String CLASSES_FROM_JPS_BUILD_ENV_NAME = "JPS_BOOTSTRAP_CLASSES_FROM_JPS_BUILD";
  private final JpsModel myModel;
  private final Set<String> myModuleNames;
  private final File myDataStorageRoot;

  public JpsBuild(Path ideaHomePath, JpsModel model, Path jpsBootstrapWorkDir) throws Exception {
    myModel = model;
    myModuleNames = myModel.getProject().getModules().stream().map(JpsNamedElement::getName).collect(Collectors.toUnmodifiableSet());
    myDataStorageRoot = jpsBootstrapWorkDir.resolve("jps-build-data").toFile();

    // Workaround for KTIJ-19065
    System.setProperty(PathManager.PROPERTY_HOME_PATH, ideaHomePath.toString());

    System.setProperty("kotlin.incremental.compilation", "true");
    System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "true");

    if (underTeamCity) {
      // Under TeamCity agents try to utilize all available cpu resources
      int cpuCount = Integer.parseInt(JpsBootstrapUtil.getTeamCityConfigPropertyOrThrow("teamcity.agent.hardware.cpuCount"));
      System.setProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, Integer.toString(cpuCount + 1));
    }

    System.setProperty(JpsGroovycRunner.GROOVYC_IN_PROCESS, "true");
    System.setProperty(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY, "false");
    System.setProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "true");
    System.setProperty(GlobalOptions.LOG_DIR_OPTION, jpsBootstrapWorkDir.resolve("log").toString());

    String url = "file://" + FileUtilRt.toSystemIndependentName(jpsBootstrapWorkDir.resolve("out").toString());
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(model.getProject()).setOutputUrl(url);

    info("Compilation log directory: " + System.getProperty(GlobalOptions.LOG_DIR_OPTION));
  }

  public void buildModule(JpsModule module) throws Exception {
    // kotlin.util.compiler-dependencies downloads all dependencies required for running Kotlin JPS compiler
    // see org.jetbrains.kotlin.idea.artifacts.KotlinArtifactsFromSources
    runBuild("kotlin.util.compiler-dependencies");

    runBuild(module.getName());
  }

  public void resolveProjectDependencies() throws Exception {
    final long buildStart = System.currentTimeMillis();

    List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> scopes = new ArrayList<>();

    CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope.Builder builder = CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope.newBuilder();
    scopes.add(builder.setTypeId("project-dependencies-resolving").setForceBuild(false).setAllTargets(true).build());

    JpsMessageHandler messageHandler = new JpsMessageHandler();

    Standalone.runBuild(
      () -> myModel,
      myDataStorageRoot,
      messageHandler,
      scopes,
      false
    );

    System.out.println("Finished resolving project dependencies in " + (System.currentTimeMillis() - buildStart) + " ms");

    messageHandler.assertNoErrors();
  }

  private void runBuild(String moduleName) throws Exception {
    final long buildStart = System.currentTimeMillis();

    JpsMessageHandler messageHandler = new JpsMessageHandler();

    if (!myModuleNames.contains(moduleName)) {
      throw new IllegalStateException("Module '" + moduleName + "' was not found");
    }

    Standalone.runBuild(
      () -> myModel,
      myDataStorageRoot,
      false,
      ContainerUtil.set(moduleName),
      false,
      Collections.emptyList(),
      false,
      messageHandler
    );

    System.out.println("Finished building '" + moduleName + "' in " + (System.currentTimeMillis() - buildStart) + " ms");

    messageHandler.assertNoErrors();
  }

  private class JpsMessageHandler implements MessageHandler {
    private final AtomicReference<String> myFirstError = new AtomicReference<>();

    @Override
    public void processMessage(BuildMessage msg) {
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
          if (text.contains("Groovyc:WARNING") || text.contains("Kotlin:WARNING")) {
            warn(text);
          }
          else {
            error(text);
            myFirstError.compareAndSet(null, text);
          }
          break;
        default:
          if (!msg.getMessageText().isBlank()) {
            if (myModuleNames.contains(msg.getMessageText())) {
              verbose(text);
            }
            else {
              info(text);
            }
          }
          break;
      }
    }

    public void assertNoErrors() {
      String firstErrorText = myFirstError.get();
      if (firstErrorText != null) {
        fatal("Build finished with errors. First error:\n" + firstErrorText);
      }
    }
  }
}
