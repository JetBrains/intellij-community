/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.diagnostic.CompositeLogger
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.MultiMap
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.jps.api.CmdlineRemoteProto
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.build.Standalone
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.JpsModelLoader
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.*
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
/**
 * @author nik
 */
@CompileStatic
class JpsCompilationRunner {
  private final CompilationContext context
  private final JpsCompilationData compilationData

  JpsCompilationRunner(CompilationContext context) {
    this.context = context
    compilationData = context.compilationData
  }

  void buildModules(List<JpsModule> modules) {
    Set<String> names = new LinkedHashSet<>()
    context.messages.info("Collecting dependencies for ${modules.size()} modules")
    for (JpsModule module : modules) {
      for (String dependency : getModuleDependencies(module, false)) {
        if (names.add(dependency)) {
          context.messages.info(" adding $dependency required for $module.name")
        }
      }
    }
    runBuild(names, false, false, false)
  }

  void resolveProjectDependencies() {
    runBuild([] as Set, false, false, true)
  }

  void buildModuleTests(JpsModule module) {
    runBuild(getModuleDependencies(module, true), false, true, false)
  }

  void buildAll() {
    runBuild(Collections.<String> emptySet(), true, true, false)
  }

  void buildProduction() {
    runBuild(Collections.<String> emptySet(), true, false, false)
  }

  private static Set<String> getModuleDependencies(JpsModule module, boolean includeTests) {
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).recursively()
    if (!includeTests) {
      enumerator = enumerator.productionOnly()
    }
    return enumerator.modules.collect(new HashSet<>()) { it.name } as Set<String>
  }

  private void runBuild(final Set<String> modulesSet, final boolean allModules, boolean includeTests, boolean resolveProjectDependencies) {
    System.setProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "false")
    final AntMessageHandler messageHandler = new AntMessageHandler()
    AntLoggerFactory.ourMessageHandler = new AntMessageHandler()
    AntLoggerFactory.ourFileLoggerFactory = compilationData.fileLoggerFactory
    Logger.setFactory(AntLoggerFactory.class)
    boolean forceBuild = !context.options.incrementalCompilation

    List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> scopes = new ArrayList<>()
    for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
      if (includeTests || !type.isTests()) {
        List<String> namesToCompile = new ArrayList<>(allModules ? context.project.modules.collect {it.name} : modulesSet)
        if (type.isTests()) {
          namesToCompile.removeAll(compilationData.compiledModuleTests)
          compilationData.compiledModuleTests.addAll(namesToCompile)
        }
        else {
          namesToCompile.removeAll(compilationData.compiledModules)
          compilationData.compiledModules.addAll(namesToCompile)
        }
        if (namesToCompile.isEmpty()) continue

        CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope.Builder builder = CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope.newBuilder().setTypeId(type.getTypeId()).setForceBuild(forceBuild)
        if (allModules) {
          scopes.add(builder.setAllTargets(true).build())
        }
        else {
          scopes.add(builder.addAllTargetId(namesToCompile).build())
        }
      }
    }
    if (resolveProjectDependencies) {
      scopes.add(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope.newBuilder().setTypeId("project-dependencies-resolving").setForceBuild(false).setAllTargets(true).build())
    }

    context.messages.info("Starting build; incremental: $context.options.incrementalCompilation, cache directory: $compilationData.dataStorageRoot.absolutePath")
    context.messages.info("Build scope: ${allModules ? "all" : modulesSet.size()} modules, ${includeTests ? "including tests" : "production only"}${resolveProjectDependencies ? ", resolve dependencies" : ""}")
    long compilationStart = System.currentTimeMillis()
    context.messages.block("Compilation") {
      try {
        JpsModelLoader loader = { context.projectModel }
        Standalone.runBuild(loader, compilationData.dataStorageRoot, messageHandler, scopes, false)
      }
      catch (Throwable e) {
        context.messages.error("Compilation failed unexpectedly", e)
      }
    }
    if (!messageHandler.errorMessagesByCompiler.isEmpty()) {
      for (Map.Entry<String, Collection<String>> entry : messageHandler.errorMessagesByCompiler.entrySet()) {
        context.messages.compilationErrors(entry.key, (List<String>)entry.value)
      }
      context.messages.error("Compilation failed")
    }
    else if (!compilationData.statisticsReported) {
      context.messages.reportStatisticValue("Compilation time, ms", String.valueOf(System.currentTimeMillis() - compilationStart))
      compilationData.statisticsReported = true
    }
  }

  private class AntMessageHandler implements MessageHandler {
    private MultiMap<String, String> errorMessagesByCompiler = MultiMap.createLinked()
    private float progress = -1.0

    @Override
    void processMessage(BuildMessage msg) {
      String text = msg.messageText
      switch (msg.kind) {
        case BuildMessage.Kind.ERROR:
          String compilerName
          String messageText
          if (msg instanceof CompilerMessage) {
            CompilerMessage compilerMessage = (CompilerMessage)msg
            compilerName = compilerMessage.getCompilerName()
            String sourcePath = compilerMessage.getSourcePath()
            if (sourcePath != null) {
              messageText = sourcePath + (compilerMessage.getLine() != -1 ? ":" + compilerMessage.getLine() : "") + ":\n" + text
            }
            else {
              messageText = text
            }
          }
          else {
            compilerName = ""
            messageText = text
          }
          errorMessagesByCompiler.putValue(compilerName, messageText)
          break
        case BuildMessage.Kind.WARNING:
          context.messages.warning(text)
          break
        case BuildMessage.Kind.INFO:
          if (msg instanceof BuilderStatisticsMessage) {
            BuilderStatisticsMessage message = (BuilderStatisticsMessage)msg
            String buildKind = context.options.incrementalCompilation ? " (incremental)" : ""
            context.messages.reportStatisticValue("Compilation time '$message.builderName'$buildKind, ms", String.valueOf(message.elapsedTimeMs))
            int sources = message.getNumberOfProcessedSources()
            context.messages.reportStatisticValue("Processed files by '$message.builderName'$buildKind", String.valueOf(sources))
            if (!context.options.incrementalCompilation && sources > 0) {
              context.messages.reportStatisticValue("Compilation time per file for '$message.builderName', ms",
                                                    String.format(Locale.US, "%.2f", (double)message.elapsedTimeMs / sources))
            }
          }
          else if (!text.isEmpty()) {
            context.messages.info(text)
          }
          break
        case BuildMessage.Kind.PROGRESS:
          if (msg instanceof ProgressMessage) {
            progress = ((ProgressMessage)msg).done
          }
          else if (msg instanceof BuildingTargetProgressMessage && ((BuildingTargetProgressMessage)msg).eventType == BuildingTargetProgressMessage.Event.STARTED) {
            def targets = ((BuildingTargetProgressMessage)msg).targets
            def targetsString = targets.collect { StringUtil.decapitalize(it.presentableName) }.join(", ")
            String progressText = progress > 0 ? " (${(int)(100 * progress)}%)" : ""
            context.messages.progress("Compiling$progressText: $targetsString")
          }
          break
      }
    }
  }

  static class AntLoggerFactory implements Logger.Factory {
    private static final String COMPILER_NAME = "build runner"
    private static AntMessageHandler ourMessageHandler
    private static Logger.Factory ourFileLoggerFactory

    @NotNull
    @Override
    Logger getLoggerInstance(@NotNull String category) {
      DefaultLogger antLogger = new DefaultLogger(category) {
        @Override
        void error(@NonNls String message, @Nullable Throwable t, @NotNull @NonNls String... details) {
          if (t != null) {
            ourMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, t))
          }
          else {
            ourMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.ERROR, message))
          }
        }

        @Override
        void warn(@NonNls String message, @Nullable Throwable t) {
          ourMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.WARNING, message))
        }
      }
      if (ourFileLoggerFactory != null) {
        return new CompositeLogger(antLogger, ourFileLoggerFactory.getLoggerInstance(category))
      }
      return antLogger
    }
  }
}

