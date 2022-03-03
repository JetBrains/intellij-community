// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.util.Processor
import com.intellij.util.containers.MultiMap
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import org.apache.tools.ant.BuildException
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.retry.Retry
import org.jetbrains.jps.api.CmdlineRemoteProto
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.build.Standalone
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.cmdline.JpsModelLoader
import org.jetbrains.jps.gant.Log4jFileLoggerFactory
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType
import org.jetbrains.jps.incremental.artifacts.impl.ArtifactSorter
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil
import org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner
import org.jetbrains.jps.incremental.messages.*
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.artifact.elements.JpsModuleOutputPackagingElement
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule

import java.beans.Introspector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

@CompileStatic
final class JpsCompilationRunner {
  private static setSystemPropertyIfUndefined(String name, String value) {
    if (System.getProperty(name) == null) {
      System.setProperty(name, value)
    }
  }

  static {
    // Unset 'groovy.target.bytecode' which was possibly set by outside context
    // to get target bytecode version from corresponding java compiler settings
    System.clearProperty(GroovyRtConstants.GROOVY_TARGET_BYTECODE)

    setSystemPropertyIfUndefined(GlobalOptions.COMPILE_PARALLEL_OPTION, "true")
    def availableProcessors = Runtime.getRuntime().availableProcessors().toString()
    setSystemPropertyIfUndefined(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, availableProcessors)
    setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_PARALLELISM_PROPERTY, availableProcessors)
    setSystemPropertyIfUndefined(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "false")
    setSystemPropertyIfUndefined(JpsGroovycRunner.GROOVYC_IN_PROCESS, "true")
    setSystemPropertyIfUndefined(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY, "false")
  }

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
    runBuild(names, false, Collections.<String>emptyList(), false, false)
  }

  void buildModulesWithoutDependencies(Collection<JpsModule> modules, boolean includeTests) {
    runBuild(modules.collect { it.name }, false, Collections.<String>emptyList(), includeTests, false)
  }

  void resolveProjectDependencies() {
    new Retry(context.messages, context.options.resolveDependenciesMaxAttempts, context.options.resolveDependenciesDelayMs).call {
      try {
        runBuild(Collections.<String>emptyList(), false, Collections.<String>emptyList(), false, true)
      }
      catch (BuildException e) {
        compilationData.projectDependenciesResolved = false
        throw e
      }
    }
  }

  void buildModuleTests(JpsModule module) {
    runBuild(getModuleDependencies(module, true), false, Collections.<String>emptyList(), true, false)
  }

  void buildAll() {
    runBuild(Collections.<String>emptyList(), true, Collections.<String>emptyList(), true, false)
  }

  void buildProduction() {
    runBuild(Collections.<String>emptyList(), true, Collections.<String>emptyList(), false, false)
  }

  /**
   * @deprecated use {@link #buildArtifacts(java.util.Set, boolean)} instead
   */
  void buildArtifacts(Set<String> artifactNames) {
    buildArtifacts(artifactNames, true)
  }

  void buildArtifacts(Set<String> artifactNames, boolean buildIncludedModules) {
    Set<JpsArtifact> artifacts = getArtifactsWithIncluded(artifactNames)
    Collection<String> modules = buildIncludedModules ? getModulesIncludedInArtifacts(artifacts) : Collections.<String>emptyList()
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
    Set<String> modulesSet = new LinkedHashSet<>()
    for (JpsArtifact artifact in artifacts) {
      JpsArtifactUtil.processPackagingElements(artifact.rootElement, { element ->
        if (element instanceof JpsModuleOutputPackagingElement) {
          modulesSet.addAll(getModuleDependencies(context.findRequiredModule(element.moduleReference.moduleName), false))
        }
        true
      } as Processor<JpsPackagingElement>)
    }
    return modulesSet
  }

  private Set<JpsArtifact> getArtifactsWithIncluded(Set<String> artifactNames) {
    List<JpsArtifact> artifacts = JpsArtifactService.instance.getArtifacts(context.project).findAll { it.name in artifactNames }
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

  private void runBuild(Collection<String> modulesSet,
                        boolean allModules,
                        Collection<String> artifactNames,
                        boolean includeTests,
                        boolean resolveProjectDependencies) {
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

    long compilationStart = System.nanoTime()
    context.messages.block(TracerManager.spanBuilder("compilation")
                             .setAttribute("scope", "${allModules ? "all" : modulesSet.size()} modules")
                             .setAttribute("includeTests", includeTests)
                             .setAttribute("artifactsToBuild", artifactsToBuild.size())
                             .setAttribute("resolveProjectDependencies", resolveProjectDependencies)
                             .setAttribute("modules", String.join(", ", modulesSet))
                             .setAttribute("incremental", context.options.incrementalCompilation)
                             .setAttribute("includeTests", includeTests)
                             .setAttribute("cacheDir", compilationData.dataStorageRoot.path), new Supplier<Void>() {
      @Override
      Void get() {
        try {
          Standalone.runBuild(new JpsModelLoader() {
            @Override
            JpsModel loadModel() throws IOException {
              return context.projectModel
            }
          }, compilationData.dataStorageRoot, messageHandler, scopes, false)
        }
        catch (Throwable e) {
          Span.current().recordException(e)
          throw new BuildException("Compilation failed unexpectedly", e)
        }
        return null
      }
    })
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

  private final class AntMessageHandler implements MessageHandler {
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
              Collection<? extends BuildTarget<? extends BuildRootDescriptor>> buildTargets = currentTargets.targets as Collection
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

    private void reportProgress(Collection<? extends BuildTarget<? extends BuildRootDescriptor>> targets, String targetSpecificMessage) {
      def targetsString = targets.collect { Introspector.decapitalize(it.presentableName) }.join(", ")
      String progressText = progress >= 0 ? " (${(int)(100 * progress)}%)" : ""
      String targetSpecificText = !targetSpecificMessage.isEmpty() ? ", $targetSpecificMessage" : ""
      context.messages.progress("Compiling$progressText: $targetsString$targetSpecificText")
    }
  }

  static final class AntLoggerFactory implements Logger.Factory {
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
