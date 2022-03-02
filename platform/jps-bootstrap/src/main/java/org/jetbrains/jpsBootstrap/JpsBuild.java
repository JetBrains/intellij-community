// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtilRt;
import jetbrains.buildServer.messages.serviceMessages.PublishArtifacts;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
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
  private final Path myJpsLogDir;

  public JpsBuild(BuildDependenciesCommunityRoot communityRoot, JpsModel model, Path jpsBootstrapWorkDir, Path kotlincHome) throws Exception {
    myModel = model;
    myModuleNames = myModel.getProject().getModules().stream().map(JpsNamedElement::getName).collect(Collectors.toUnmodifiableSet());
    myDataStorageRoot = jpsBootstrapWorkDir.resolve("jps-build-data").toFile();

    System.setProperty("jps.kotlin.home", kotlincHome.toString());

    // Set IDEA home path to something or JPS can't instantiate ClasspathBoostrap.java for Groovy JPS
    // which calls PathManager.getLibPath() (it should not)
    System.setProperty(PathManager.PROPERTY_HOME_PATH, communityRoot.getCommunityRoot().toString());

    System.setProperty("kotlin.incremental.compilation", "true");
    System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "true");

    if (underTeamCity && System.getProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION) == null) {
      // Under TeamCity agents try to utilize all available cpu resources
      int cpuCount = Integer.parseInt(JpsBootstrapUtil.getTeamCityConfigPropertyOrThrow("teamcity.agent.hardware.cpuCount"));
      System.setProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, Integer.toString(cpuCount + 1));
    }

    System.setProperty(JpsGroovycRunner.GROOVYC_IN_PROCESS, "true");
    System.setProperty(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY, "false");
    System.setProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "true");

    myJpsLogDir = jpsBootstrapWorkDir.resolve("log");
    System.setProperty(GlobalOptions.LOG_DIR_OPTION, myJpsLogDir.toString());

    String url = "file://" + FileUtilRt.toSystemIndependentName(jpsBootstrapWorkDir.resolve("out").toString());
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(model.getProject()).setOutputUrl(url);

    info("Compilation log directory: " + System.getProperty(GlobalOptions.LOG_DIR_OPTION));
  }

  public void buildModules(Set<JpsModule> modules) throws Exception {
    runBuild(modules.stream().map(JpsNamedElement::getName).collect(Collectors.toSet()));
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

  private void runBuild(Set<String> modules) throws Exception {
    final long buildStart = System.currentTimeMillis();

    JpsMessageHandler messageHandler = new JpsMessageHandler();

    for (String moduleName : modules) {
      if (!myModuleNames.contains(moduleName)) {
        throw new IllegalStateException("Module '" + moduleName + "' was not found");
      }
    }

    Standalone.runBuild(
      () -> myModel,
      myDataStorageRoot,
      false,
      modules,
      false,
      Collections.emptyList(),
      false,
      messageHandler
    );

    System.out.println("Finished building '" + String.join(" ", modules) + "' in " + (System.currentTimeMillis() - buildStart) + " ms");

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
        case WARNING:
          // Warnings mean little for bootstrapping
          verbose(text);
          break;
        case ERROR:
        case INTERNAL_BUILDER_ERROR:
          error(text);
          myFirstError.compareAndSet(null, text);
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
        System.out.println(new PublishArtifacts(myJpsLogDir + "=>jps-bootstrap-jps-logs.zip").asString());
        throw new IllegalStateException("Build finished with errors. See TC artifacts for build log. First error:\n" + firstErrorText);
      }
    }
  }
}
