package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.awt.Image
import javax.imageio.ImageIO
import javax.swing.JComponent

@ApiStatus.Internal
interface WelcomeScreenRightTabBannerProvider {
  companion object {
    private val EP_NAME =
      ExtensionPointName<WelcomeScreenRightTabBannerProvider>("com.intellij.platform.ide.welcomeScreenRightTabBannerProvider")

    fun createSingleBanner(project: Project): JComponent? {
      val provider = EP_NAME.lazySequence().firstOrNull { it.isApplicable(project) }
      if (provider != null) {
        return provider.createBanner(project)
      }
      return null
    }
  }

  fun loadImage(path: String): Image? {
    return try {
      ImageIO.read(javaClass.getResourceAsStream(path))
    }
    catch (e: Exception) {
      logger<WelcomeScreenRightTabBannerProvider>().error(e)
      null
    }
  }

  fun isApplicable(project: Project): Boolean

  fun createBanner(project: Project): JComponent
}
