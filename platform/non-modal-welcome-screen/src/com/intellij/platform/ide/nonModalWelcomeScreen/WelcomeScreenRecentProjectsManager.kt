package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ModuleAttachProcessor
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.ide.nonModalWelcomeScreen.GoWelcomeScreenUtil2.getWelcomeScreenProjectPath2
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.LinkOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Similar to DataSpellRecentProjectManager
 */
class WelcomeScreenRecentProjectsManager(coroutineScope: CoroutineScope) : RecentProjectsManagerBase(coroutineScope) {
  /**
   * See [com.intellij.ide.AttachedModuleAwareRecentProjectsManager.getProjectDisplayName]
   */
  override fun getProjectDisplayName(project: Project): String? {
    val name = ModuleAttachProcessor.getMultiProjectDisplayName(project)
    return name ?: super.getProjectDisplayName(project)
  }

  @ApiStatus.Internal
  suspend fun createOrOpenWelcomeScreenGoLandProject() : Project? {
    LOG.info("Opening a GoLand welcome screen project")

    val location = getWelcomeScreenProjectPath2()

    if (!location.exists(LinkOption.NOFOLLOW_LINKS)) {
      location.createDirectories()
    }

    TrustedProjects.setProjectTrusted(location, true)
    serviceAsync<WindowsDefenderChecker>().markProjectPath(location, /*skip =*/ true)

    val project = PlatformProjectOpenProcessor.getInstance().openProjectAndFile(location, tempProject = false)
    if (project != null) {
      LOG.info("Opened the GoLand welcome screen project at $location")
      LOG.debug("Project: ", project)
      setProjectHidden(project, true)
    }
    return project
  }

  companion object {
    private val LOG = logger<WelcomeScreenRecentProjectsManager>()

  }
}