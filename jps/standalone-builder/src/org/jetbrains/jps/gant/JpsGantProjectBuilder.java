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

import com.intellij.openapi.diagnostic.CompositeLogger;
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
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.build.Standalone;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.cmdline.JpsModelLoader;
import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.BuilderStatisticsMessage;
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
 * @deprecated use {@link org.jetbrains.intellij.build.CompilationTasks} from platform-build-scripts module for building IDEs based
 * on IntelliJ Platform. If you need to build another project use {@link Standalone} directly.
 */
public class JpsGantProjectBuilder {
  private final Project myProject;
  private final JpsModel myModel;
  private boolean myCompressJars;
  private boolean myBuildIncrementally;
  private File myDataStorageRoot;
  private JpsModelLoader myModelLoader;
  private BuildInfoPrinter myBuildInfoPrinter = new DefaultBuildInfoPrinter();
  private Set<String> myCompiledModules = new HashSet<>();
  private Set<String> myCompiledModuleTests = new HashSet<>();
  private boolean myStatisticsReported;
  private Logger.Factory myFileLoggerFactory;

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

  public boolean isBuildIncrementally() {
    return myBuildIncrementally;
  }

  public void setBuildIncrementally(boolean buildIncrementally) {
    myBuildIncrementally = buildIncrementally;
  }

  public void setBuildInfoPrinter(BuildInfoPrinter printer) {
    myBuildInfoPrinter = printer;
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

  public void setupAdditionalLogging(File buildLogFile, String categoriesWithDebugLevel) {
    categoriesWithDebugLevel = StringUtil.notNullize(categoriesWithDebugLevel);
    try {
      myFileLoggerFactory = new Log4jFileLoggerFactory(buildLogFile, categoriesWithDebugLevel);
      info("Build log (" + (!categoriesWithDebugLevel.isEmpty() ? "debug level for " + categoriesWithDebugLevel : "info") + ") will be written to " + buildLogFile.getAbsolutePath());
    }
    catch (Throwable t) {
      myProject.log("Cannot setup additional logging to " + buildLogFile.getAbsolutePath() + ": " + t.getMessage(), t, Project.MSG_WARN);
    }
  }

  public void buildModules(List<JpsModule> modules) {
    Set<String> names = new LinkedHashSet<>();
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
    runBuild(Collections.emptySet(), true, true);
  }

  public void buildProduction() {
    runBuild(Collections.emptySet(), true, false);
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
    Set<String> names = new HashSet<>();
    for (JpsModule depModule : enumerator.getModules()) {
      names.add(depModule.getName());
    }
    return names;
  }

  private void runBuild(final Set<String> modulesSet, final boolean allModules, boolean includeTests) {
    System.setProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "false");
    final AntMessageHandler messageHandler = new AntMessageHandler();
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    AntLoggerFactory.ourMessageHandler = new AntMessageHandler();
    AntLoggerFactory.ourFileLoggerFactory = myFileLoggerFactory;
    Logger.setFactory(AntLoggerFactory.class);
    boolean forceBuild = !myBuildIncrementally;

    List<TargetTypeBuildScope> scopes = new ArrayList<>();
    for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
      if (includeTests || !type.isTests()) {
        List<String> namesToCompile = new ArrayList<>(allModules ? getAllModules() : modulesSet);
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

    info("Starting build; incremental: " + myBuildIncrementally + ", cache directory: " + myDataStorageRoot.getAbsolutePath());
    info("Build scope: " + (allModules ? "all" : modulesSet.size()) + " modules, " + (includeTests ? "including tests" : "production only"));
    long compilationStart = System.currentTimeMillis();
    try {
      myBuildInfoPrinter.printBlockOpenedMessage(this, "Compilation");
      Standalone.runBuild(myModelLoader, myDataStorageRoot, messageHandler, scopes, false);
    }
    catch (Throwable e) {
      error(e);
    }
    finally {
      myBuildInfoPrinter.printBlockClosedMessage(this, "Compilation");
    }
    if (messageHandler.myFailed) {
      error("Compilation failed");
    }
    else if (!myStatisticsReported) {
      myBuildInfoPrinter.printStatisticsMessage(this, "Compilation time, ms", String.valueOf(System.currentTimeMillis() - compilationStart));
      myStatisticsReported = true;
    }
  }

  private Set<String> getAllModules() {
    HashSet<String> modules = new HashSet<>();
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
    List<String> result = new ArrayList<>();
    for (File root : roots) {
      result.add(root.getAbsolutePath());
    }
    return result;
  }

  private class AntMessageHandler implements MessageHandler {
    private boolean myFailed;

    @Override
    public void processMessage(BuildMessage msg) {
      BuildMessage.Kind kind = msg.getKind();
      String text = msg.getMessageText();
      switch (kind) {
        case ERROR:
          String compilerName;
          String messageText;
          if (msg instanceof CompilerMessage) {
            CompilerMessage compilerMessage = (CompilerMessage)msg;
            compilerName = compilerMessage.getCompilerName();
            String sourcePath = compilerMessage.getSourcePath();
            if (sourcePath != null) {
              messageText = sourcePath + (compilerMessage.getLine() != -1 ? ":" + compilerMessage.getLine() : "") + ":\n" + text;
            }
            else {
              messageText = text;
            }
          }
          else {
            compilerName = "";
            messageText = text;
          }
          myFailed = true;
          myBuildInfoPrinter.printCompilationErrors(JpsGantProjectBuilder.this, compilerName, messageText);
          break;
        case WARNING:
          warning(text);
          break;
        case INFO:
          if (msg instanceof BuilderStatisticsMessage) {
            BuilderStatisticsMessage message = (BuilderStatisticsMessage)msg;
            String buildKind = myBuildIncrementally ? " (incremental)" : "";
            myBuildInfoPrinter.printStatisticsMessage(JpsGantProjectBuilder.this, "Compilation time '" + message.getBuilderName() + "'" + buildKind + ", ms",
                                                      String.valueOf(message.getElapsedTimeMs()));
            int sources = message.getNumberOfProcessedSources();
            myBuildInfoPrinter.printStatisticsMessage(JpsGantProjectBuilder.this, "Processed files by '" + message.getBuilderName() + "'" + buildKind,
                                                      String.valueOf(sources));
            if (!myBuildIncrementally && sources > 0) {
              myBuildInfoPrinter.printStatisticsMessage(JpsGantProjectBuilder.this, "Compilation time per file for '" + message.getBuilderName() + "', ms",
                                                        String.format(Locale.US, "%.2f", (double)message.getElapsedTimeMs() / sources));
            }
          }
          else if (!text.isEmpty()) {
            info(text);
          }
          break;
        case PROGRESS:
          if (msg instanceof BuildingTargetProgressMessage) {
            String targetsString = StringUtil.join(((BuildingTargetProgressMessage)msg).getTargets(),
                                                   (NotNullFunction<BuildTarget<?>, String>)dom -> dom.getPresentableName(), ",");
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
    private static Logger.Factory ourFileLoggerFactory;

    @NotNull
    @Override
    public Logger getLoggerInstance(@NotNull String category) {
      DefaultLogger antLogger = new DefaultLogger(category) {
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
      if (ourFileLoggerFactory != null) {
        return new CompositeLogger(antLogger, ourFileLoggerFactory.getLoggerInstance(category));
      }
      return antLogger;
    }
  }
}
