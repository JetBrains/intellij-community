// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.LottieUtils
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import javax.swing.SwingUtilities

@ApiStatus.Internal
object NewUiOnboardingUtil {
  private const val LOTTIE_SCRIPT_PATH = "newUiOnboarding/lottie.js"

  inline fun <reified T : Component> findUiComponent(project: Project, predicate: (T) -> Boolean): T? {
    val root = WindowManager.getInstance().getFrame(project) ?: return null
    findUiComponent(root, predicate)?.let { return it }
    for (window in root.ownedWindows) {
      findUiComponent(window, predicate)?.let { return it }
    }
    return null
  }

  inline fun <reified T : Component> findUiComponent(root: Component, predicate: (T) -> Boolean): T? {
    val component = UIUtil.uiTraverser(root).find {
      it is T && it.isVisible && it.isShowing && predicate(it)
    }
    return component as? T
  }

  fun convertPointToFrame(project: Project, source: Component, point: Point): RelativePoint? {
    val frame = WindowManager.getInstance().getFrame(project) ?: return null
    val framePoint = SwingUtilities.convertPoint(source, point, frame)
    return RelativePoint(frame, framePoint)
  }

  /**
   * Creates an HTML page with provided lottie animation
   * @return a pair of html page text and a size of the animation
   */
  fun createLottieAnimationPage(lottieJsonPath: String, classLoader: ClassLoader): Pair<String, Dimension>? {
    // read json using provided class loader, because it can be in the plugin jar
    val lottieJson = readResourceOrLog(lottieJsonPath, classLoader) ?: return null
    // read js script by platform class loader, because it is in the platform jar
    val lottieScript = readResourceOrLog(LOTTIE_SCRIPT_PATH, NewUiOnboardingUtil::class.java.classLoader) ?: return null
    val htmlPage = LottieUtils.createLottieAnimationPage(lottieJson, lottieScript)
    val size = getLottieImageSize(lottieJson) ?: return null
    return htmlPage to size
  }

  private fun readResourceOrLog(path: String, classLoader: ClassLoader): String? {
    return try {
      val url = classLoader.getResource(path) ?: run {
        LOG.error("Failed to find resource by path: $path")
        return null
      }
      url.readText()
    }
    catch (t: Throwable) {
      LOG.error("Failed to read resource by path: $path", t)
      null
    }
  }

  private fun getLottieImageSize(lottieJson: String): Dimension? {
    return try {
      LottieUtils.getLottieImageSize(lottieJson)
    }
    catch (t: Throwable) {
      LOG.error("Failed to parse lottie json", t)
      null
    }
  }

  private val LOG = thisLogger()
}