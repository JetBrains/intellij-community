// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightProducts

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.CloseProjectWindowHelper
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.provider.EelInitialization
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.setEelDescriptor
import com.intellij.platform.eel.provider.setEelMachine
import com.intellij.platform.eel.provider.setRemoteProjectBaseNioPath
import com.intellij.platform.eel.provider.setRemoteProjectIdentityNioPath
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.getInstance("#com.intellij.ide.lightProducts.LightProjectOpening")

private const val PROJECTS_DIR_NAME = "projects"

/**
 * Opens the project at [path] the way lightweight IDE products
 * (the JetBrains Client frontend and JetBrains Light) do:
 * the project settings are stored in a separate directory derived from [projectStoreSeed]
 * (see [createLightProjectStoreDir]), the project is hidden from the recent projects list,
 * and closing its window does not show the welcome frame.
 *
 * The platform checks project trust on the store directory.
 * The store directory is registered in [LightProjectTrustTargets], so a trust provider of the product can check [path] instead.
 *
 * [beforeInit] is invoked before the project is initialized, prior to associating the project with its Eel descriptor.
 * [eelMachineInitializer] initializes the Eel machine for the project's Eel descriptor after the project is opened;
 * a `null` result leaves the project without an Eel machine.
 * [showWelcomeScreen] tells the platform whether the welcome frame is a valid outcome of a project that does not open;
 * pass `false` in a product that has nothing to show without its project.
 * [projectRootDir] is the directory the platform treats as the project root.
 * When it is `null`, it is derived from a stat of [path], which costs a network round trip on a remote host;
 * pass the known value when the caller has already touched [path].
 * [forceOpenInNewFrame] and [projectToClose] are passed through to the [OpenProjectTask]. Leaving them at their defaults
 * always opens a new frame; passing `forceOpenInNewFrame = false` together with an open [projectToClose] lets the platform
 * ask the user whether to reuse that frame or to attach, see `ProjectManagerImpl.attachToExistingOrOpenInTheSameFrame`.
 */
@ApiStatus.Internal
suspend fun openProjectForLightProduct(
  path: Path,
  projectStoreSeed: String,
  materializeProject: Boolean,
  showWelcomeScreen: Boolean = true,
  projectRootDir: Path? = null,
  forceOpenInNewFrame: Boolean = true,
  projectToClose: Project? = null,
  beforeInit: (Project) -> Unit = {},
  eelMachineInitializer: suspend (EelDescriptor) -> EelMachine? = ::defaultLightEelMachineInitializer,
): Project? {
  val eelDescriptor = path.getEelDescriptor()

  val projectFile = if (materializeProject) path else createLightProjectStoreDir(projectStoreSeed)

  // The platform checks trust on the store directory, not on [path] (see `ProjectManagerImpl.checkTrustedState`).
  // Register the pair before the check, so the trust provider of the product can check [path] instead.
  if (!materializeProject) {
    LightProjectTrustTargets.getInstance().register(storeDir = projectFile, projectPath = path)
  }

  val rootDir = projectRootDir ?: if (path.isDirectory()) path else path.parent
  // Every startup activity may ask for the host path, so it is set before the project is initialized rather than
  // after the open call returns - post-startup activities are not joined before then. `ThinClientRdProjectViewSession`
  // sets the descriptor and both paths together for the same reason.
  val hostPath = path.asEelPath().toString()
  val options = OpenProjectTask {
    isNewProject = !ProjectUtil.isValidProjectPath(projectFile)
    this.showWelcomeScreen = showWelcomeScreen
    this.projectRootDir = rootDir
    createModule = false
    runConfigurators = false
    useDefaultProjectAsTemplate = false
    preventIprLookup = true
    this.forceOpenInNewFrame = forceOpenInNewFrame
    this.projectToClose = projectToClose
    beforeInitTasks += { project ->
      beforeInit(project)
      @OptIn(EelDelicateApi::class)
      project.setEelDescriptor(eelDescriptor)
      // `getRemoteProjectBaseNioPath` reads the descriptor, so it has to be set first.
      project.setRemoteProjectBaseNioPath(hostPath)
      project.setRemoteProjectIdentityNioPath(hostPath)
    }
    projectName = path.name
  }

  val project = ProjectManagerEx.getInstanceEx().openProjectAsync(projectFile, options)
  if (project == null) {
    logger.warn("Could not open project at ${path}")
    return null
  }

  if (!materializeProject) {
    @Suppress("UnsafeOpenServiceCast")
    (serviceAsync<RecentProjectsManager>() as RecentProjectsManagerBase).setProjectHidden(project, true)
  }
  CloseProjectWindowHelper.SHOW_WELCOME_FRAME_FOR_PROJECT.set(project, false)

  val machine = eelMachineInitializer(eelDescriptor)
  if (machine != null) {
    @OptIn(EelDelicateApi::class)
    project.setEelMachine(machine)
  }

  return project
}

/**
 * Creates the directory where the settings of a light project identified by [projectStoreSeed] are stored:
 * either a persistent per-project directory (when `rdct.persist.project.settings` is enabled)
 * or a fresh temporary directory removed on IDE exit.
 */
@ApiStatus.Internal
fun createLightProjectStoreDir(projectStoreSeed: String): Path {
  if (Registry.`is`("rdct.persist.project.settings", false)) {
    val projectHash = DigestUtil.sha1Hex(projectStoreSeed)
    return PathManager.getOriginalConfigDir().resolve(PROJECTS_DIR_NAME).resolve(projectHash).also {
      it.createDirectories()
    }
  }
  else {
    return FileUtil.createTempDirectory(File(PathManager.getConfigPath()), "thinProject", null, true).toPath()
  }
}

@ApiStatus.Internal
suspend fun defaultLightEelMachineInitializer(descriptor: EelDescriptor): EelMachine? {
  logger.info("Initializing Eel for descriptor=$descriptor")
  val machine = withTimeoutOrNull(30.seconds) {
    try {
      EelInitialization.runEelInitialization(descriptor)
    }
    catch (e: EelUnavailableException) {
      logger.error("Eel is unavailable for descriptor=$descriptor",
                   e)
      null
    }
  }
  if (machine == null) {
    logger.error("Failed to initialize Eel for descriptor=$descriptor within 30s (timeout or no resolver registered)")
  }
  else {
    logger.info("Eel initialized for descriptor=$descriptor, machine=$machine")
  }
  return machine
}
