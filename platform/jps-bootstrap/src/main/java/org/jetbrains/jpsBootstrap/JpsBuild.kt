// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtilRt
import jetbrains.buildServer.messages.serviceMessages.PublishArtifacts
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.error
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.info
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.verbose
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.warn
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.cleanDirectory
import org.jetbrains.intellij.build.dependencies.DotNetPackagesCredentials.setupSystemCredentials
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.build.Standalone
import org.jetbrains.jps.cmdline.LogSetup
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.stream.Collectors
import kotlin.io.path.bufferedWriter

class JpsBuild(communityRoot: BuildDependenciesCommunityRoot, private val myModel: JpsModel, jpsBootstrapWorkDir: Path, kotlincHome: Path?) {
  private val myModuleNames: Set<String>
  private val myDataStorageRoot: Path
  private val myJpsLogDir: Path

  init {
    myModuleNames = myModel.project.modules.stream().map { obj: JpsModule -> obj.name }.collect(Collectors.toUnmodifiableSet())
    myDataStorageRoot = jpsBootstrapWorkDir.resolve("jps-build-data")
    System.setProperty("aether.connector.resumeDownloads", "false")
    System.setProperty("jps.kotlin.home", kotlincHome.toString())

    System.setProperty("kotlin.incremental.compilation", "true")
    System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, "true")
    if (JpsBootstrapMain.Companion.underTeamCity && System.getProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION) == null) {
      // Under TeamCity agents try to utilize all available cpu resources
      val cpuCount = JpsBootstrapUtil.getTeamCityConfigPropertyOrThrow("teamcity.agent.hardware.cpuCount").toInt()
      System.setProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, Integer.toString(cpuCount + 1))
    }
    System.setProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "true")
    myJpsLogDir = jpsBootstrapWorkDir.resolve("log")
    setupJpsLogging()
    System.setProperty(GlobalOptions.LOG_DIR_OPTION, myJpsLogDir.toString())
    val url = "file://" + FileUtilRt.toSystemIndependentName(jpsBootstrapWorkDir.resolve("out").toString())
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myModel.project).outputUrl = url
    info("Compilation log directory: " + System.getProperty(GlobalOptions.LOG_DIR_OPTION))
  }

  @Throws(Exception::class)
  fun buildModules(modules: Set<JpsModule?>?) {
    runBuild(modules!!.stream().map { obj: JpsModule? -> obj!!.name }.collect(Collectors.toSet()), false)
  }

  /**
   * @see com.intellij.space.java.jps.SpaceDependencyAuthenticationDataProvider
   */
  @Throws(Exception::class)
  fun resolveProjectDependencies() {
    info("Resolving project dependencies...")
    val spaceUsername = System.getProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME)
    val spacePassword = System.getProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD)
    if (spaceUsername == null || spaceUsername.isBlank() || spacePassword == null || spacePassword.isBlank()) {
      if (!setupSystemCredentials()) {
        warn("Space credentials are not provided via -D" + BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME
          + " and -D" + BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD
          + ". Private Space Maven dependencies, if not available locally, will fail to be resolved.")
      }
    }
    val buildStart = System.currentTimeMillis()
    val scopes: MutableList<TargetTypeBuildScope> = ArrayList()
    val builder = TargetTypeBuildScope.newBuilder()
    scopes.add(builder.setTypeId("project-dependencies-resolving").setForceBuild(false).setAllTargets(true).build())
    val messageHandler = JpsMessageHandler()
    if (!JpsBootstrapMain.Companion.underTeamCity) {
      // Show downloading process on local run, very handy
      messageHandler.setExplicitlyVerbose()
    }
    Standalone.runBuild(
      { myModel },
      myDataStorageRoot.toFile(),
      messageHandler,
      scopes,
      false
    )
    info("Finished resolving project dependencies in " + (System.currentTimeMillis() - buildStart) + " ms")
    messageHandler.assertNoErrors()
  }

  @Throws(Exception::class)
  private fun runBuild(modules: Set<String>, rebuild: Boolean) {
    val buildStart = System.currentTimeMillis()
    val messageHandler = JpsMessageHandler()
    for (moduleName in modules) {
      check(myModuleNames.contains(moduleName)) { "Module '$moduleName' was not found" }
    }
    Standalone.runBuild(
      { myModel },
      myDataStorageRoot.toFile(),
      rebuild,
      modules,
      false, emptyList(),
      false,
      messageHandler
    )
    println("Finished building '" + java.lang.String.join(" ", modules) + "' in " + (System.currentTimeMillis() - buildStart) + " ms")
    val errors: List<String> = ArrayList(messageHandler.myErrors)
    if (!errors.isEmpty() && !rebuild && System.getProperty("intellij.build.incremental.compilation.fallback.rebuild", "true") == "true") {
      warn("""
        Incremental build finished with errors. Forcing rebuild. Compilation errors:
        ${java.lang.String.join("\n", errors)}
        """.trimIndent())
      cleanDirectory(myDataStorageRoot)
      runBuild(modules, true)
    }
    else {
      messageHandler.assertNoErrors()
    }
  }

  private inner class JpsMessageHandler : MessageHandler {
    private var myExplicitlyVerbose = false
    val myErrors: MutableList<String> = CopyOnWriteArrayList()
    private val myLastMessage = AtomicReference<String>()
    fun setExplicitlyVerbose() {
      myExplicitlyVerbose = true
    }

    override fun processMessage(msg: BuildMessage) {
      val kind = msg.kind
      val text = msg.toString()
      when (kind) {
        BuildMessage.Kind.PROGRESS, BuildMessage.Kind.WARNING -> {
          val lastMessage = myLastMessage.get()
          if (text == lastMessage) {
            // Quick and dirty way to remove duplicate verbose messages
            return
          }
          else {
            myLastMessage.set(text)
          }
          if (myExplicitlyVerbose) {
            info(text)
          }
          else {
            // Warnings mean little for bootstrapping
            verbose(text)
          }
        }

        BuildMessage.Kind.ERROR, BuildMessage.Kind.INTERNAL_BUILDER_ERROR ->           // Do not log since we may call rebuild later and teamcity will fail on the first error
          myErrors.add(text)

        else -> if (!msg.messageText.isBlank()) {
          if (myModuleNames.contains(msg.messageText)) {
            verbose(text)
          }
          else {
            info(text)
          }
        }
      }
    }

    fun assertNoErrors() {
      val errors: List<String> = ArrayList(myErrors)
      if (!errors.isEmpty()) {
        println(PublishArtifacts("$myJpsLogDir=>jps-bootstrap-jps-logs.zip").asString())
        for (error in errors) {
          error(error)
        }
        throw IllegalStateException("""
    Build finished with errors. See TC artifacts for build log. First error:
    ${errors[0]}
    """.trimIndent())
      }
    }
  }

  private fun setupJpsLogging() {
    val logSettingsFile = myJpsLogDir.resolve(LogSetup.LOG_CONFIG_FILE_NAME)
    Files.deleteIfExists(logSettingsFile) // non-existing file will reset JPS logging settings to defaults

    val debugCategories = System.getProperty("intellij.build.debug.logging.categories", "")
      .split(",")
      .filterNot(String::isBlank)
    if (debugCategories.isEmpty()) {
      return
    }

    val level = Level.FINER.name
    info("Setting logging level to $level for: $debugCategories")

    val loggingSettingsProperties = Properties().apply {
      debugCategories.forEach { category ->
        setProperty("$category.level", level)
      }
    }

    Files.createDirectories(myJpsLogDir)
    logSettingsFile.bufferedWriter().use { writer ->
      loggingSettingsProperties.store(writer, "Created by ${JpsBuild::class.qualifiedName}")
    }
  }

  companion object {
    const val CLASSES_FROM_JPS_BUILD_ENV_NAME = "JPS_BOOTSTRAP_CLASSES_FROM_JPS_BUILD"
  }
}
