// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Processor
import com.intellij.util.containers.MultiMap
import com.intellij.util.lang.JavaVersion
import groovy.transform.CompileStatic
import org.apache.tools.ant.BuildException
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.jps.api.CmdlineRemoteProto
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.build.Standalone
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.JpsModelLoader
import org.jetbrains.jps.gant.Log4jFileLoggerFactory
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType
import org.jetbrains.jps.incremental.artifacts.impl.ArtifactSorter
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner
import org.jetbrains.jps.incremental.messages.*
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.artifact.elements.JpsModuleOutputPackagingElement
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@CompileStatic
class JpsCompilationRunner {
  private static boolean ourToolsJarAdded
  private final CompilationContext context
  private final JpsCompilationData compilationData

  JpsCompilationRunner(CompilationContext context) {
    this.context = context
    compilationData = context.compilationData
  }

  void buildModules(List<JpsModule> modules) {
    Set<String> names = new LinkedHashSet<>()
    context.messages.debug("Collecting dependencies for ${modules.size()} modules")
    for (JpsModule module : modules) {
      for (String dependency : getModuleDependencies(module, false)) {
        if (names.add(dependency)) {
          context.messages.debug(" adding $dependency required for $module.name")
        }
      }
    }
    runBuild(names, false, [], false, false)
  }

  void buildModulesWithoutDependencies(Collection<JpsModule> modules, boolean includeTests) {
    runBuild(modules.collect { it.name }.toSet(), false, [], includeTests, false)
  }

  void resolveProjectDependencies() {
    runBuild([] as Set, false, [], false, true)
  }

  void buildModuleTests(JpsModule module) {
    runBuild(getModuleDependencies(module, true), false, [], true, false)
  }

  void buildAll() {
    runBuild(Collections.<String> emptySet(), true, [], true, false)
  }

  void buildProduction() {
    runBuild(Collections.<String> emptySet(), true, [], false, false)
  }

  /**
   * @deprecated use {@link #buildArtifacts(java.util.Collection, boolean)} instead
   */
  void buildArtifacts(Collection<String> artifactNames) {
    buildArtifacts(artifactNames, true)
  }

  void buildArtifacts(Collection<String> artifactNames, boolean buildIncludedModules) {
    Set<JpsArtifact> artifacts = getArtifactsWithIncluded(artifactNames)
    Set<String> modules = buildIncludedModules ? getModulesIncludedInArtifacts(artifacts) : [] as Set<String>
    runBuild(modules, false, artifacts.collect {it.name}, false, false)
  }

  private static Set<String> getModuleDependencies(JpsModule module, boolean includeTests) {
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).recursively()
    if (!includeTests) {
      enumerator = enumerator.productionOnly()
    }
    return enumerator.modules.collect(new HashSet<>()) { it.name } as Set<String>
  }

  private Set<String> getModulesIncludedInArtifacts(Collection<JpsArtifact> artifacts) {
    Set<String> modulesSet = new HashSet<>()
    artifacts.each { artifact ->
      JpsArtifactUtil.processPackagingElements(artifact.rootElement, { element ->
        if (element instanceof JpsModuleOutputPackagingElement) {
          modulesSet.addAll(getModuleDependencies(context.findRequiredModule(element.moduleReference.moduleName), false))
        }
        true
      } as Processor<JpsPackagingElement>)
    }
    return modulesSet
  }

  private Set<JpsArtifact> getArtifactsWithIncluded(Collection<String> artifactNames) {
    Set<String> artifactNamesSet = new HashSet<>(artifactNames)
    def artifacts = JpsArtifactService.instance.getArtifacts(context.project).findAll { it.name in artifactNamesSet }
    return ArtifactSorter.addIncludedArtifacts(artifacts)
  }

  private void setupAdditionalBuildLogging(JpsCompilationData compilationData) {
    def categoriesWithDebugLevel = compilationData.categoriesWithDebugLevel
    def buildLogFile = compilationData.buildLogFile

    try {
      Logger.Factory factory = new Log4jFileLoggerFactory(buildLogFile, categoriesWithDebugLevel)
      AntLoggerFactory.ourFileLoggerFactory = factory
      context.messages.info("Build log (${!categoriesWithDebugLevel.isEmpty() ? "debug level for $categoriesWithDebugLevel" : "info"}) will be written to ${buildLogFile.absolutePath}")
    }
    catch (Throwable t) {
      context.messages.warning("Cannot setup additional logging to $buildLogFile.absolutePath: $t.message")
    }
  }

  private void runBuild(final Set<String> modulesSet, final boolean allModules, Collection<String> artifactNames, boolean includeTests,
                        boolean resolveProjectDependencies) {
    if (JavaVersion.current().feature < 9 && (!modulesSet.isEmpty() || allModules)) {
      addToolsJarToSystemClasspath(context.paths.jdkHome, context.messages)
    }
    System.setProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "false")
    System.setProperty(JpsGroovycRunner.GROOVYC_IN_PROCESS, "true")
    System.setProperty(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY, "false")
    final AntMessageHandler messageHandler = new AntMessageHandler()
    AntLoggerFactory.ourMessageHandler = messageHandler
    if (context.options.compilationLogEnabled) {
      setupAdditionalBuildLogging(compilationData)
    }
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
    if (resolveProjectDependencies && !compilationData.projectDependenciesResolved) {
      scopes.add(CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope.newBuilder().setTypeId("project-dependencies-resolving").setForceBuild(false).setAllTargets(true).build())
      compilationData.projectDependenciesResolved = true
    }
    Collection<String> artifactsToBuild = artifactNames - compilationData.builtArtifacts
    if (!artifactsToBuild.isEmpty()) {
      CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope.Builder builder = CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope.newBuilder().setTypeId(ArtifactBuildTargetType.INSTANCE.getTypeId()).setForceBuild(forceBuild)
      scopes.add(builder.addAllTargetId(artifactsToBuild).build())
      compilationData.builtArtifacts.addAll(artifactsToBuild)
    }

    context.messages.info("Starting build; incremental: $context.options.incrementalCompilation, cache directory: $compilationData.dataStorageRoot.absolutePath")
    String buildArtifactsMessage = !artifactsToBuild.isEmpty() ? ", ${artifactsToBuild.size()} artifacts" : ""
    String resolveDependenciesMessage = resolveProjectDependencies ? ", resolve dependencies" : ""
    context.messages.info("Build scope: ${allModules ? "all" : modulesSet.size()} modules, ${includeTests ? "including tests" : "production only"}$buildArtifactsMessage$resolveDependenciesMessage")
    long compilationStart = System.nanoTime()
    context.messages.block("Compilation") {
      try {
        JpsModelLoader loader = { context.projectModel }
        Standalone.runBuild(loader, compilationData.dataStorageRoot, messageHandler, scopes, false)
      }
      catch (Throwable e) {
        throw new BuildException("Compilation failed unexpectedly", e)
      }
    }
    if (!messageHandler.errorMessagesByCompiler.isEmpty()) {
      for (Map.Entry<String, Collection<String>> entry : messageHandler.errorMessagesByCompiler.entrySet()) {
        context.messages.compilationErrors(entry.key, (List<String>)entry.value)
      }
      context.messages.error("Compilation failed")
    }
    else if (!compilationData.statisticsReported) {
      messageHandler.printPerModuleCompilationStatistics(compilationStart)
      context.messages.reportStatisticValue("Compilation time, ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - compilationStart)))
      compilationData.statisticsReported = true
    }
  }

  /**
   * Add tools.jar to the system classloader's classpath. {@link javax.tools.ToolProvider} will load javac implementation classes by its own URLClassLoader,
   * which uses the system classloader as its parent, so we need to ensure that tools.jar will be accessible from the system classloader,
   * otherwise the loaded classes will be incompatible with the classes loaded by {@link org.jetbrains.jps.javac.ast.JavacReferenceCollectorListener}.
   */
  private static void addToolsJarToSystemClasspath(String jdkHome, BuildMessages messages) {
    if (ourToolsJarAdded) {
      return
    }
    File toolsJar = new File(jdkHome, "lib/tools.jar")
    if (!toolsJar.exists()) {
      messages.error("Failed to add tools.jar to classpath: $toolsJar doesn't exist")
    }
    BuildUtils.addToSystemClasspath(toolsJar)
    ourToolsJarAdded = true
  }

  private class AntMessageHandler implements MessageHandler {
    private MultiMap<String, String> errorMessagesByCompiler = MultiMap.createConcurrent()
    private Map<String, Long> compilationStartTimeForTarget = new ConcurrentHashMap<>()
    private Map<String, Long> compilationFinishTimeForTarget = new ConcurrentHashMap<>()
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
            def currentTargets = msg.currentTargets
            if (currentTargets != null) {
              Collection<? extends BuildTarget<?>> buildTargets = currentTargets.targets as Collection
              reportProgress(buildTargets, msg.messageText)
            }
          }
          else if (msg instanceof BuildingTargetProgressMessage) {
            def targets = ((BuildingTargetProgressMessage)msg).targets
            def target = targets.first()
            def targetId = "$target.id${targets.size() > 1 ? " and ${targets.size()} more" : ""} ($target.targetType.typeId)".toString()
            if (((BuildingTargetProgressMessage)msg).eventType == BuildingTargetProgressMessage.Event.STARTED) {
              reportProgress(targets, "")
              compilationStartTimeForTarget.put(targetId, System.nanoTime())
            }
            else {
              compilationFinishTimeForTarget.put(targetId, System.nanoTime())
            }
          }
          break
      }
    }

    void printPerModuleCompilationStatistics(long compilationStart) {
      if (compilationStartTimeForTarget.isEmpty()) return
      new File(context.paths.buildOutputRoot, "log/compilation-time.csv").withWriter { out ->
        compilationFinishTimeForTarget.each {
          def startTime = compilationStartTimeForTarget[it.key] - compilationStart
          def finishTime = it.value - compilationStart
          out.println("$it.key,$startTime,$finishTime")
        }
      }
      def buildMessages = context.messages
      buildMessages.info("Compilation time per target:")
      def compilationTimeForTarget = compilationFinishTimeForTarget.collect {new Pair<String, Long>(it.key, (it.value - compilationStartTimeForTarget[it.key]) as long)}
      buildMessages.info(" average: ${String.format("%.2f",((compilationTimeForTarget.collect {it.second}.sum() as double) / compilationTimeForTarget.size()) / 1000000)}ms")
      def topTargets = compilationTimeForTarget.toSorted { it.second }.reverse().take(10)
      buildMessages.info(" top ${topTargets.size()} targets by compilation time:")
      topTargets.each { entry -> buildMessages.info("  $entry.first: ${TimeUnit.NANOSECONDS.toMillis(entry.second)}ms") }
    }

    private void reportProgress(Collection<? extends BuildTarget<?>> targets, String targetSpecificMessage) {
      def targetsString = targets.collect { StringUtil.decapitalize(it.presentableName) }.join(", ")
      String progressText = progress >= 0 ? " (${(int)(100 * progress)}%)" : ""
      String targetSpecificText = !targetSpecificMessage.isEmpty() ? ", $targetSpecificMessage" : ""
      context.messages.progress("Compiling$progressText: $targetsString$targetSpecificText")
    }
  }

  static class AntLoggerFactory implements Logger.Factory {
    public static final String COMPILER_NAME = "build runner" //it's public to workaround Groovy bug (IDEA-179735)
    private static AntMessageHandler ourMessageHandler
    private static Logger.Factory ourFileLoggerFactory

    @NotNull
    @Override
    Logger getLoggerInstance(@NotNull String category) {
      Logger fileLogger = ourFileLoggerFactory != null ? ourFileLoggerFactory.getLoggerInstance(category) : null
      return new BackedLogger(category, fileLogger)
    }

    private static class BackedLogger extends DefaultLogger {
      private final @Nullable Logger myFileLogger

      BackedLogger(String category, @Nullable Logger fileLogger) {
        super(category)
        myFileLogger = fileLogger
      }

      @Override
      void error(@NonNls String message, @Nullable Throwable t, @NotNull @NonNls String... details) {
        if (t != null) {
          ourMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, t))
        }
        else {
          ourMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.ERROR, message))
        }
        if (myFileLogger != null) {
          myFileLogger.error(message, t, details)
        }
      }

      @Override
      void warn(@NonNls String message, @Nullable Throwable t) {
        ourMessageHandler.processMessage(new CompilerMessage(COMPILER_NAME, BuildMessage.Kind.WARNING, message))
        if (myFileLogger != null) {
          myFileLogger.warn(message, t)
        }
      }

      @Override
      void info(String message) {
        if (myFileLogger != null) {
          myFileLogger.info(message)
        }
      }

      @Override
      void info(String message, @Nullable Throwable t) {
        if (myFileLogger != null) {
          myFileLogger.info(message, t)
        }
      }

      @Override
      boolean isDebugEnabled() {
        return myFileLogger != null && myFileLogger.isDebugEnabled()
      }

      @Override
      void debug(String message) {
        if (myFileLogger != null) {
          myFileLogger.debug(message)
        }
      }

      @Override
      void debug(@Nullable Throwable t) {
        if (myFileLogger != null) {
          myFileLogger.debug(t)
        }
      }

      @Override
      void debug(String message, @Nullable Throwable t) {
        if (myFileLogger != null) {
          myFileLogger.debug(message, t)
        }
      }

      @Override
      boolean isTraceEnabled() {
        return myFileLogger != null && myFileLogger.isTraceEnabled()
      }

      @Override
      void trace(String message) {
        if (myFileLogger != null) {
          myFileLogger.trace(message)
        }
      }

      @Override
      void trace(@Nullable Throwable t) {
        if (myFileLogger != null) {
          myFileLogger.trace(t)
        }
      }
    }
  }
}
