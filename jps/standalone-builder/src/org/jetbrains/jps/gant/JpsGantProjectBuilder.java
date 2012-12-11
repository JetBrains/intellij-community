/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.gant;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.build.Standalone;
import org.jetbrains.jps.cmdline.JpsModelLoader;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class JpsGantProjectBuilder {
  private final Project myProject;
  private final JpsModel myModel;
  private boolean myCompressJars;
  private File myDataStorageRoot;
  private JpsModelLoader myModelLoader;
  private boolean myDryRun;
  private BuildInfoPrinter myBuildInfoPrinter = new DefaultBuildInfoPrinter();

  public JpsGantProjectBuilder(Project project, JpsModel model) {
    myProject = project;
    myModel = model;
    myModelLoader = new JpsModelLoader() {
      @Override
      public JpsModel loadModel() {
        return myModel;
      }
    };
  }

  public void setDryRun(boolean dryRun) {
    myDryRun = dryRun;
  }

  public void setTargetFolder(String targetFolder) {
    String url = "file://" + FileUtil.toSystemIndependentName(targetFolder);
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myModel.getProject()).setOutputUrl(url);
  }

  public boolean isCompressJars() {
    return myCompressJars;
  }

  public void setCompressJars(boolean compressJars) {
    myCompressJars = compressJars;
  }

  public void setBuildInfoPrinter(BuildInfoPrinter printer) {
    myBuildInfoPrinter = printer;
  }

  public void setUseInProcessJavac(boolean value) {
    //doesn't make sense for new builders
  }

  public void setArrangeModuleCyclesOutputs(boolean value) {
    //doesn't make sense for new builders
  }

  public void error(String message) {
    throw new BuildException(message);
  }

  public void error(Throwable t) {
    throw new BuildException(t);
  }

  public void warning(String message) {
    myProject.log(message, Project.MSG_WARN);
  }

  public void info(String message) {
    myProject.log(message, Project.MSG_INFO);
  }

  public void stage(String message) {
    myBuildInfoPrinter.printProgressMessage(this, message);
  }

  public File getDataStorageRoot() {
    return myDataStorageRoot;
  }

  public void setDataStorageRoot(File dataStorageRoot) {
    myDataStorageRoot = dataStorageRoot;
  }

  public void cleanOutput() {
    if (myDryRun) {
      info("Cleaning skipped as we're running dry");
      return;
    }

    for (JpsModule module : myModel.getProject().getModules()) {
      for (boolean test : new boolean[]{false, true}) {
        File output = JpsJavaExtensionService.getInstance().getOutputDirectory(module, test);
        if (output != null) {
          FileUtil.delete(output);
        }
      }
    }
  }

  public void makeModule(JpsModule module) {
    runBuild(getModuleDependencies(module, false), false);
  }

  public void makeModuleTests(JpsModule module) {
    runBuild(getModuleDependencies(module, true), true);
  }

  public void buildAll() {
    runBuild(Collections.<String>emptySet(), true);
  }

  public void buildProduction() {
    runBuild(Collections.<String>emptySet(), false);
  }

  public void exportModuleOutputProperties() {
    for (JpsModule module : myModel.getProject().getModules()) {
      for (boolean test : new boolean[]{true, false}) {
        myProject.setProperty("module." + module.getName() + ".output." + (test ? "test" : "main"), getModuleOutput(module, test));
      }
    }

  }

  private static Set<String> getModuleDependencies(JpsModule module, boolean includeTests) {
    Set<JpsModule> modules = JpsJavaExtensionService.dependencies(module).recursively().includedIn(JpsJavaClasspathKind.compile(includeTests)).getModules();
    Set<String> names = new HashSet<String>();
    for (JpsModule depModule : modules) {
      names.add(depModule.getName());
    }
    return names;
  }

  private void runBuild(final Set<String> modulesSet, boolean includeTests) {
    if (!myDryRun) {
      final AntMessageHandler messageHandler = new AntMessageHandler();
      Logger.setFactory(new AntLoggerFactory(messageHandler));
      info("Starting build: modules = " + modulesSet + ", caches are saved to " + myDataStorageRoot.getAbsolutePath());
      try {
        Standalone.runBuild(myModelLoader, myDataStorageRoot, BuildType.PROJECT_REBUILD, modulesSet, Collections.<String>emptyList(),
                            includeTests, messageHandler);
      }
      catch (Throwable e) {
        error(e);
      }
    }
    else {
      info("Building skipped as we're running dry");
    }
  }

  public String moduleOutput(JpsModule module) {
    return getModuleOutput(module, false);
  }

  public String moduleTestsOutput(JpsModule module) {
    return getModuleOutput(module, true);
  }

  public String getModuleOutput(JpsModule module, boolean forTests) {
    File directory = JpsJavaExtensionService.getInstance().getOutputDirectory(module, forTests);
    return directory != null ? directory.getAbsolutePath() : null;
  }

  public List<String> moduleRuntimeClasspath(JpsModule module, boolean forTests) {
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).recursively().includedIn(JpsJavaClasspathKind.runtime(forTests));
    Collection<File> roots = enumerator.classes().getRoots();
    List<String> result = new ArrayList<String>();
    for (File root : roots) {
      result.add(root.getAbsolutePath());
    }
    return result;
  }

  private class AntMessageHandler implements MessageHandler {
    @Override
    public void processMessage(BuildMessage msg) {
      BuildMessage.Kind kind = msg.getKind();
      String text = msg.getMessageText();
      switch (kind) {
        case ERROR:
          String compilerName = msg instanceof CompilerMessage ? ((CompilerMessage)msg).getCompilerName() : "";
          myBuildInfoPrinter.printCompilationErrors(JpsGantProjectBuilder.this, compilerName, text);
          break;
        case WARNING:
          warning(text);
          break;
        case INFO:
          if (!text.isEmpty()) {
            info(text);
          }
          break;
        case PROGRESS:
          myBuildInfoPrinter.printProgressMessage(JpsGantProjectBuilder.this, text);
          break;
      }
    }
  }

  private class AntLoggerFactory implements Logger.Factory {
    private static final String COMPILER_NAME = "build runner";

    private final AntMessageHandler myMessageHandler;

    public AntLoggerFactory(AntMessageHandler messageHandler) {
      myMessageHandler = messageHandler;
    }

    @Override
    public Logger getLoggerInstance(String category) {
      return new DefaultLogger(category) {
        @Override
        public void error(@NonNls String message, @Nullable Throwable t, @NonNls String... details) {
          if (t != null) {
            myMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, t));
          }
          else {
            myMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.ERROR, message));
          }
        }

        @Override
        public void warn(@NonNls String message, @Nullable Throwable t) {
          myMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.WARNING, message));
        }
      };
    }
  }
}
