// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.ui.GotItTooltip
import com.intellij.ui.JBColor
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.ColorPalette
import java.awt.*
import javax.swing.Icon
import javax.swing.JComponent

private fun getProjectPath(project: Project): String {
  val recentProjectManager = RecentProjectsManagerBase.getInstanceEx()
  return recentProjectManager.getProjectPath(project) ?: project.basePath ?: run {
    //thisLogger().warn("Impossible: no path for project $project")
    ""
  }
}

private fun getProjectNameForIcon(project: Project): String {
  val path = getProjectPath(project)
  return RecentProjectIconHelper.getProjectName(path)
}

@Service(Service.Level.PROJECT)
private class ProjectWindowCustomizerIconCache(private val project: Project) {
  var cachedIcon = getIconRaw()
    private set

  init {
    project.messageBus.connect().subscribe(UISettingsListener.TOPIC, UISettingsListener {
      revalidate()
    })

    project.messageBus.connect().subscribe(LafManagerListener.TOPIC, LafManagerListener {
      revalidate()
    })
  }

  private fun revalidate() {
    cachedIcon = getIconRaw()
  }

  private fun getIconRaw(): Icon {
    val path = getProjectPath(project)
    return RecentProjectsManagerBase.getInstanceEx().getProjectIcon(path, true)
  }
}

enum class MainToolbarCustomizationType {
  JUST_ICON,
  LINEAR_GRAD_WITH_ICON,
  CIRCULAR_GRADIENT_WITH_ICON,
  DROPDOWN_WITH_ICON,
  JUST_DROPDOWN;

  fun isLinearGradient() = this == LINEAR_GRAD_WITH_ICON
  fun isCircularGradient() = this == CIRCULAR_GRADIENT_WITH_ICON
  fun isGradient() = isLinearGradient() || isCircularGradient()
  fun isShowIcon() = this != JUST_DROPDOWN
}

@Service
class ProjectWindowCustomizerService : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(): ProjectWindowCustomizerService = service<ProjectWindowCustomizerService>()
  }

  private var wasGradientPainted = false
  private var ourSettingsValue = UISettings.getInstance().differentiateProjects
  private val colorCache = mutableMapOf<String, Color>()
  private val listeners = mutableListOf<(Boolean) -> Unit>()

  private val colors: Array<Color>
    get() = arrayOf(
      JBColor.namedColor("RecentProject.Color1.MainToolbarGradientStart", JBColor(0xDB3D3C, 0xCE443C)),
      JBColor.namedColor("RecentProject.Color2.MainToolbarGradientStart", JBColor(0xF57236, 0xE27237)),
      JBColor.namedColor("RecentProject.Color3.MainToolbarGradientStart", JBColor(0x2BC8BB, 0x2DBCAD)),
      JBColor.namedColor("RecentProject.Color4.MainToolbarGradientStart", JBColor(0x359AF2, 0x3895E1)),
      JBColor.namedColor("RecentProject.Color5.MainToolbarGradientStart", JBColor(0x8379FB, 0x7B75E8)),
      JBColor.namedColor("RecentProject.Color6.MainToolbarGradientStart", JBColor(0x7E54B5, 0x7854AD)),
      JBColor.namedColor("RecentProject.Color7.MainToolbarGradientStart", JBColor(0xD63CC8, 0x8F4593)),
      JBColor.namedColor("RecentProject.Color8.MainToolbarGradientStart", JBColor(0x954294, 0xC840B9)),
      JBColor.namedColor("RecentProject.Color9.MainToolbarGradientStart", JBColor(0xE75371, 0xD75370))
    )

  fun getProjectIcon(project: Project): Icon {
    return project.service<ProjectWindowCustomizerIconCache>().cachedIcon
  }

  fun getPaintingType() = when (Registry.get("ide.colorful.toolbar.gradient.type").selectedOption) {
      "Just Icon"                    -> MainToolbarCustomizationType.JUST_ICON
      "Linear Gradient and Icon"     -> MainToolbarCustomizationType.LINEAR_GRAD_WITH_ICON
      "Circular Gradient and Icon"   -> MainToolbarCustomizationType.CIRCULAR_GRADIENT_WITH_ICON
      "Dropdown Background and Icon" -> MainToolbarCustomizationType.DROPDOWN_WITH_ICON
      "Just Dropdown"                -> MainToolbarCustomizationType.JUST_DROPDOWN
      else                           -> MainToolbarCustomizationType.LINEAR_GRAD_WITH_ICON
  }

  internal fun update(newValue: Boolean) {
    if (newValue != ourSettingsValue) {
      ourSettingsValue = newValue
      wasGradientPainted = newValue && conditionToEnable()
      fireUpdate()
    }
  }

  fun isAvailable(): Boolean = PlatformUtils.isRider() || Registry.`is`("ide.colorful.toolbar")

  fun isActive(): Boolean = wasGradientPainted && ourSettingsValue && isAvailable()

  private var gotItShown
    get() = PropertiesComponent.getInstance().getBoolean("colorful.instances.gotIt.shown", false)
    set(value) { PropertiesComponent.getInstance().setValue("colorful.instances.gotIt.shown", value) }

  fun paint(window: Window, parent: JComponent, g: Graphics?): Boolean {
    g ?: return false
    val project = ProjectFrameHelper.getFrameHelper(window)?.project ?: return false

    return paint(project, parent, g as Graphics2D)
  }

  fun enableIfNeeded() {
    if (conditionToEnable() && !wasGradientPainted) {
      wasGradientPainted = true
      fireUpdate()
    }
  }

  private fun conditionToEnable() = ProjectManagerEx.getOpenProjects().size > 1 || Registry.`is`("ide.colorful.toolbar.force")

  fun addListener(disposable: Disposable, fireFirstTime: Boolean, listener: (Boolean) -> Unit) {
    if (fireFirstTime) {
      listener(isActive())
    }
    listeners.add(listener)
    Disposer.register(disposable) {
      listeners.remove(listener)
    }
  }

  private fun fireUpdate() {
    listeners.forEach { it(isActive()) }
  }

  fun shouldShowGotIt(): Boolean = !gotItShown

  private fun recordGotItShown() {
    gotItShown = true
  }

  fun showGotIt(project: Project, component: JComponent) {
    if (!shouldShowGotIt() || !isActive()) return

    val gotIt = GotItTooltip("colorful.instances", IdeBundle.message("colorfulInstances.gotIt.text"), this).apply {
      withHeader(IdeBundle.message("colorfulInstances.gotIt.title"))
      // withTimeout(5000) TODO: to discuss with designers: do we want autohide or do we want a button?
    }

    gotIt.showCondition = { true }

    if (WindowManagerEx.getInstanceEx().getFrameHelper(project)?.frame?.isFocused == true) {
      gotIt.show(component, GotItTooltip.BOTTOM_MIDDLE)
      recordGotItShown()
    }
  }

  /**
   * @return true if method painted something
   */
  fun paint(project: Project, parent: JComponent, g: Graphics2D): Boolean {
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.color = parent.background
    g.fillRect(0, 0, parent.width, parent.height)

    if (!isActive() || !getPaintingType().isGradient()) return false
    val projectPath = getProjectNameForIcon(project)
    val color = computeOrGetColor(projectPath, project)

    val length = Registry.intValue("ide.colorful.toolbar.gradient.length", 600)
    val x = parent.x.toFloat()
    val y = parent.y.toFloat()
    if (getPaintingType().isCircularGradient()) {
      val offset = 150f
      g.paint = RadialGradientPaint(x + offset, y + parent.height / 2, length - offset, floatArrayOf(0.0f, 0.6f), arrayOf(color, parent.background))
    } else {
      g.paint = GradientPaint(x, y, color, length.toFloat(), y, parent.background)
    }
    g.fillRect(0, 0, length, parent.height)

    return true
  }

  private fun computeOrGetColor(projectPath: String, disposable: Disposable): Color {
    return colorCache.getOrPut(projectPath) {
      Disposer.register(disposable) { colorCache.remove(projectPath) }
      ColorPalette.select(colors, projectPath)
    }
  }

  override fun dispose() {}
}

private class ProjectWindowCustomizerListener : ProjectActivity, UISettingsListener {
  override suspend fun execute(project: Project) {
    ProjectWindowCustomizerService.getInstance().enableIfNeeded()
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    ProjectWindowCustomizerService.getInstance().update(uiSettings.differentiateProjects)
  }
}

