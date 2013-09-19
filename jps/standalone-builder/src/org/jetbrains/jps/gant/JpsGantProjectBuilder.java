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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.build.Standalone;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.cmdline.JpsModelLoader;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.BuildingTargetProgressMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

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
  private Set<String> myCompiledModules = new HashSet<String>();
  private Set<String> myCompiledModuleTests = new HashSet<String>();
  private boolean myStatisticsReported;

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
    exportModuleOutputProperties();
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
    warning("projectBuilder.useInProcessJavac option is ignored because it doesn't make sense for new JPS builders");
  }

  public void setArrangeModuleCyclesOutputs(boolean value) {
    warning("projectBuilder.arrangeModuleCyclesOutputs option is ignored because it doesn't make sense for new JPS builders");
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
    myCompiledModules.clear();
    myCompiledModuleTests.clear();
  }

  public void makeModule(JpsModule module) {
    runBuild(getModuleDependencies(module, false), false, false);
  }

  public void buildModules(List<JpsModule> modules) {
    Set<String> names = new LinkedHashSet<String>();
    info("Collecting dependencies for " + modules.size() + " modules");
    for (JpsModule module : modules) {
      Set<String> dependencies = getModuleDependencies(module, false);
      for (String dependency : dependencies) {
        if (names.add(dependency)) {
          info(" adding " + dependency + " required for " + module.getName());
        }
      }
    }
    runBuild(names, false, false);
  }

  public void makeModuleTests(JpsModule module) {
    runBuild(getModuleDependencies(module, true), false, true);
  }

  public void buildAll() {
    runBuild(Collections.<String>emptySet(), true, true);
  }

  public void buildProduction() {
    runBuild(Collections.<String>emptySet(), true, false);
  }

  public void exportModuleOutputProperties() {
    for (JpsModule module : myModel.getProject().getModules()) {
      for (boolean test : new boolean[]{true, false}) {
        String propertyName = "module." + module.getName() + ".output." + (test ? "test" : "main");
        String outputPath = getModuleOutput(module, test);
        myProject.setProperty(propertyName, outputPath);
      }
    }

  }

  private static Set<String> getModuleDependencies(JpsModule module, boolean includeTests) {
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).recursively();
    if (!includeTests) {
      enumerator = enumerator.productionOnly();
    }
    Set<String> names = new HashSet<String>();
    for (JpsModule depModule : enumerator.getModules()) {
      names.add(depModule.getName());
    }
    return names;
  }

  private void runBuild(final Set<String> modulesSet, final boolean allModules, boolean includeTests) {
    if (!myDryRun) {
      final AntMessageHandler messageHandler = new AntMessageHandler();
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      AntLoggerFactory.ourMessageHandler = new AntMessageHandler();
      Logger.setFactory(AntLoggerFactory.class);
      boolean forceBuild = true;

      List<TargetTypeBuildScope> scopes = new ArrayList<TargetTypeBuildScope>();
      for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
        if (includeTests || !type.isTests()) {
          List<String> namesToCompile = new ArrayList<String>(allModules ? getAllModules() : modulesSet);
          if (type.isTests()) {
            namesToCompile.removeAll(myCompiledModuleTests);
            myCompiledModuleTests.addAll(namesToCompile);
          }
          else {
            namesToCompile.removeAll(myCompiledModules);
            myCompiledModules.addAll(namesToCompile);
          }
          if (namesToCompile.isEmpty()) continue;

          TargetTypeBuildScope.Builder builder = TargetTypeBuildScope.newBuilder().setTypeId(type.getTypeId()).setForceBuild(forceBuild);
          if (allModules) {
            scopes.add(builder.setAllTargets(true).build());
          }
          else if (!modulesSet.isEmpty()) {
            scopes.add(builder.addAllTargetId(modulesSet).build());
          }
        }
      }

      info("Starting build; cache directory: " + myDataStorageRoot.getAbsolutePath());
      try {
        long compilationStart = System.currentTimeMillis();
        Standalone.runBuild(myModelLoader, myDataStorageRoot, messageHandler, scopes, false);
        if (!myStatisticsReported) {
          myBuildInfoPrinter.printStatisticsMessage(this, "Compilation time, ms", String.valueOf(System.currentTimeMillis() - compilationStart));
          myStatisticsReported = true;
        }
      }
      catch (Throwable e) {
        error(e);
      }
    }
    else {
      info("Building skipped as we're running dry");
    }
  }

  private Set<String> getAllModules() {
    HashSet<String> modules = new HashSet<String>();
    for (JpsModule module : myModel.getProject().getModules()) {
      modules.add(module.getName());
    }
    return modules;
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
          error("Compilation failed");
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
          if (msg instanceof BuildingTargetProgressMessage) {
            String targetsString = StringUtil.join(((BuildingTargetProgressMessage)msg).getTargets(),
                                                   new NotNullFunction<BuildTarget<?>, String>() {
                                                     @NotNull
                                                     @Override
                                                     public String fun(BuildTarget<?> dom) {
                                                       return dom.getPresentableName();
                                                     }
                                                   }, ",");
            switch (((BuildingTargetProgressMessage)msg).getEventType()) {
              case STARTED:
                myBuildInfoPrinter.printBlockOpenedMessage(JpsGantProjectBuilder.this, targetsString);
                break;
              case FINISHED:
                myBuildInfoPrinter.printBlockClosedMessage(JpsGantProjectBuilder.this, targetsString);
                break;
            }
          }
          break;
      }
    }
  }

  private static class AntLoggerFactory implements Logger.Factory {
    private static final String COMPILER_NAME = "build runner";

    private static AntMessageHandler ourMessageHandler;

    @Override
    public Logger getLoggerInstance(String category) {
      return new DefaultLogger(category) {
        @Override
        public void error(@NonNls String message, @Nullable Throwable t, @NotNull @NonNls String... details) {
          if (t != null) {
            ourMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, t));
          }
          else {
            ourMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.ERROR, message));
          }
        }

        @Override
        public void warn(@NonNls String message, @Nullable Throwable t) {
          ourMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.WARNING, message));
        }
      };
    }
  }
}
