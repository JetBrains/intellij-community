// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.GradientTextureCache
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CoroutineSupport.UiDispatcherKind
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.ui
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectNameListener
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBColor
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.paint.PaintUtil.alignIntToInt
import com.intellij.ui.paint.PaintUtil.alignTxToInt
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.AvatarIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Window
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.math.abs

@Service(Service.Level.PROJECT)
private class ProjectWindowCustomizerIconCache(private val project: Project, coroutineScope: CoroutineScope) {
  val cachedIcon: SynchronizedClearableLazy<Icon> = SynchronizedClearableLazy { getIconRaw() }

  init {
    val projectConnection = project.messageBus.connect(coroutineScope)
    val appConnection = ApplicationManager.getApplication().messageBus.connect(coroutineScope)
    projectConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      cachedIcon.drop()
    })
    appConnection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      cachedIcon.drop()
    })
    projectConnection.subscribe(ProjectNameListener.TOPIC, object: ProjectNameListener {
      override fun nameChanged(newName: String?) {
        cachedIcon.drop()
      }
    })
  }

  private fun getIconRaw(): Icon {
    val path = ProjectWindowCustomizerService.projectPath(project) ?: ""
    val size = JBUI.CurrentTheme.Toolbar.recentProjectAvatarIconSize()
    return RecentProjectsManagerBase.getInstanceEx().getProjectIcon(path = path, isProjectValid = true, unscaledIconSize = size, name = project.name)
  }
}

private const val TOOLBAR_BACKGROUND_KEY = "PROJECT_TOOLBAR_COLOR"
private const val LAST_CALCULATED_COLOR_INDEX_KEY = "LAST_CALCULATED_COLOR_INDEX_KEY"

private fun isForceColorfulToolbar() = Registry.`is`("ide.colorful.toolbar.force", true)

private fun conditionToEnable() = isForceColorfulToolbar() || ProjectManagerEx.getOpenProjects().size > 1

private data class ProjectColors(val gradient: Color,
                                 val iconColorStart: Color,
                                 val iconColorEnd: Color,
                                 val index: Int? = null)

@Service
class ProjectWindowCustomizerService : Disposable {
  companion object {
    private var instance: ProjectWindowCustomizerService? = null
    private var leftGradientCache: GradientTextureCache = GradientTextureCache()
    private var rightGradientCache: GradientTextureCache = GradientTextureCache()
    private val LOG = Logger.getInstance(ProjectWindowCustomizerService::class.java)

    init {
      ApplicationManager.registerCleaner { instance = null }
    }

    fun getInstance(): ProjectWindowCustomizerService {
      var result = instance
      if (result == null) {
        result = service<ProjectWindowCustomizerService>()
        instance = result
      }
      return result
    }

    @Internal
    fun projectPath(project: Project): String? = RecentProjectsManagerBase.getInstanceEx().getProjectPath(project)
  }

  private var wasGradientPainted = isForceColorfulToolbar()

  private var ourSettingsValue = UISettings.getInstance().differentiateProjects
  private val colorCache = mutableMapOf<String, ProjectColors>()
  private val listeners = mutableListOf<(Boolean) -> Unit>()
  private val defaultColors = ProjectColors(gradient = gradientColors[0],
                                            iconColorStart = ProjectIconPalette.gradients[0].first,
                                            iconColorEnd = ProjectIconPalette.gradients[0].second,
                                            index = 0)

  @Suppress("UnregisteredNamedColor")
  private val gradientColors: Array<Color>
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

  private val gradientRepaintRoots = HashSet<JComponent>()

  internal fun addGradientRepaintRoot(component: JComponent) {
    gradientRepaintRoots.add(component)
  }

  internal fun removeGradientRepaintRoot(component: JComponent) {
    gradientRepaintRoots.remove(component)
  }

  internal fun getGradientRepaintRoots(): Set<JComponent> = gradientRepaintRoots

  fun getProjectIcon(project: Project): Icon {
    return project.service<ProjectWindowCustomizerIconCache>().cachedIcon.get()
  }

  @Internal
  fun getGradientProjectColor(project: Project): Color {
    return getDeprecatedCustomToolbarColor(project)
           ?: storageFor(project)?.let { getProjectColor(it).gradient }
           ?: defaultColors.gradient
  }

  @Internal
  fun getProjectColorToCustomize(project: Project): Color {
    return getDeprecatedCustomToolbarColor(project)
           ?: storageFor(project)?.let { getProjectColor(it).iconColorStart }
           ?: defaultColors.iconColorStart
  }

  fun getRecentProjectIconColor(projectPath: String): Pair<Color, Color> {
    val projectColors = getProjectColor(storageFor(projectPath))
    return Pair(projectColors.iconColorStart, projectColors.iconColorEnd)
  }

  private fun getDeprecatedCustomToolbarColor(project: Project): Color? {
    val colorStr = project.serviceIfCreated<PropertiesComponent>()?.getValue(TOOLBAR_BACKGROUND_KEY)
    return ColorHexUtil.fromHex(colorStr, null)
  }

  @Internal
  fun getCurrentProjectColorIndex(project: Project): Int? =  storageFor(project)?.let { getProjectColor(it).index }

  private fun getProjectColor(colorStorage: ProjectColorStorage): ProjectColors {
    ThreadingAssertions.assertEventDispatchThread()
    val projectPath = colorStorage.projectPath ?: return defaultColors

    // Get calculated earlier color or calculate the next color
    return colorCache.computeIfAbsent(projectPath) {
      if (colorStorage is WorkspaceProjectColorStorage && colorStorage.isEmpty) {
        setupWorkspaceStorage(colorStorage.project)
      }

      // get the custom project color and transform it for the toolbar
      val customColors = colorStorage.customColor?.takeIf { it.isNotEmpty() }?.let {
        val color = ColorHexUtil.fromHex(it)
        val toolbarColor = ColorUtil.toAlpha(color, 90)
        ProjectColors(toolbarColor, color, color)
      }

      if (customColors != null) {
        customColors
      }
      else {
        val associatedIndex = getOrGenerateAssociatedColorIndex(colorStorage)
        ProjectColors(gradient = gradientColors[associatedIndex],
                      iconColorStart = ProjectIconPalette.gradients[associatedIndex].first,
                      iconColorEnd = ProjectIconPalette.gradients[associatedIndex].second,
                      index = associatedIndex)
      }
    }
  }

  private fun getOrGenerateAssociatedColorIndex(colorStorage: ProjectColorStorage): Int {
    getAssociatedColorIndex(colorStorage)?.let { return it }

    // Calculate next colors by incrementing (and saving the new value) color index
    val index = PropertiesComponent.getInstance().nextColorIndex(gradientColors.size)

    // Save calculated colors and clear customized colors for the project
    setAssociatedColorsIndex(colorStorage, index)

    return index
  }

  private fun getAssociatedColorIndex(colorStorage: ProjectColorStorage): Int? {
    val index = colorStorage.associatedIndex ?: return null
    if (index >= 0 && index < gradientColors.size) return index
    return null
  }

  @Internal
  fun dropProjectIconCache(project: Project) {
    project.service<ProjectWindowCustomizerIconCache>().cachedIcon.drop()
  }

  @Internal
  fun setIconMainColorAsProjectColor(project: Project): Boolean {
    if (!RecentProjectsManagerBase.getInstanceEx().hasCustomIcon(project)) return false

    val icon = project.service<ProjectWindowCustomizerIconCache>().cachedIcon.get()
    if (icon is AvatarIcon) {
      // Somehow, the icon may be an AvatarIcon already in the cache. AvatarIcon can't be a custom icon.
      LOG.warn("Unexpected cached AvatarIcon as a custom icon during the project color setup")
      return false
    }

    val iconMainColor = IconUtil.mainColor(icon)
    setCustomProjectColor(project, iconMainColor)

    return true
  }

  @Internal
  fun setAssociatedColorsIndex(project: Project, index: Int) {
    val storage = storageFor(project) ?: return
    setAssociatedColorsIndex(storage, index)
  }

  private fun setAssociatedColorsIndex(colorStorage: ProjectColorStorage, index: Int) {
    colorStorage.associatedIndex = index
    if (index >= 0) colorStorage.customColor = null
  }

  @Internal
  fun setCustomProjectColor(project: Project, color: Color?) {
    val storage = storageFor(project) ?: return
    setCustomProjectColor(storage, color)
  }

  private fun setCustomProjectColor(colorStorage: ProjectColorStorage, color: Color?) {
    clearToolbarColorsAndInMemoryCache(colorStorage)
    colorStorage.customColor = color?.let {
      ColorUtil.toHex(color, true)
    }
  }

  @Internal
  internal fun setupWorkspaceStorage(project: Project) {
    clearToolbarColorsAndInMemoryCache(project)

    val workspaceStorage = WorkspaceProjectColorStorage(project)
    if (!workspaceStorage.isEmpty) return

    if (RecentProjectsManagerBase.getInstanceEx().hasCustomIcon(project)) {
      // On the first opening if there is a custom icon, we set the custom color generated from the icon
      setIconMainColorAsProjectColor(project)
      return
    }

    // Perform initial setup of storages for the project
    val path = projectPath(project) ?: return
    val recentProjectsStorage = RecentProjectColorStorage(path)

    if (recentProjectsStorage.isEmpty) {
      // If recent projects storage is empty, generate the associated index and save it into workspace
      getOrGenerateAssociatedColorIndex(workspaceStorage)
    }
    else {
      workspaceStorage.getDataFrom(recentProjectsStorage)
    }
  }

  @Internal
  fun clearToolbarColorsAndInMemoryCache(project: Project) {
    val storage = storageFor(project) ?: return
    clearToolbarColorsAndInMemoryCache(storage)
  }

  private fun clearToolbarColorsAndInMemoryCache(colorStorage: ProjectColorStorage) {
    ThreadingAssertions.assertEventDispatchThread()
    colorStorage.projectPath?.let { colorCache.remove(it) }

    if (colorStorage is WorkspaceProjectColorStorage) {
      // Remove toolbar color for those users who set it up before it's removal
      PropertiesComponent.getInstance(colorStorage.project).unsetValue(TOOLBAR_BACKGROUND_KEY)
    }
  }

  private fun PropertiesComponent.nextColorIndex(colorsCount: Int): Int {
    val randomDefault = Random().nextInt(colorsCount)
    val result = (getInt(LAST_CALCULATED_COLOR_INDEX_KEY, randomDefault) + 1) % colorsCount
    setValue(LAST_CALCULATED_COLOR_INDEX_KEY, result, -1)
    return result
  }

  private fun storageFor(projectPath: String) = RecentProjectColorStorage(projectPath)
  private fun storageFor(project: Project) = if (project.isDisposed) null else WorkspaceProjectColorStorage(project)

  internal fun update(newValue: Boolean) {
    if (newValue != ourSettingsValue) {
      ourSettingsValue = newValue
      wasGradientPainted = newValue && conditionToEnable()
      fireUpdate()
    }
  }

  fun isAvailable(): Boolean {
    return !DistractionFreeModeController.shouldMinimizeCustomHeader() &&
           InternalUICustomization.getInstance()?.isProjectCustomDecorationActive != false &&
           (PlatformUtils.isRider () || Registry.`is`("ide.colorful.toolbar", true))
  }

  fun isActive(): Boolean = wasGradientPainted && ourSettingsValue && isAvailable()

  fun enableIfNeeded() {
    if (!wasGradientPainted && conditionToEnable()) {
      wasGradientPainted = true
      fireUpdate()
    }
  }

  fun addListener(coroutineScope: CoroutineScope, fireFirstTime: Boolean, listener: (Boolean) -> Unit) {
    if (fireFirstTime) {
      listener(isActive())
    }
    listeners.add(listener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      listeners.remove(listener)
    }
  }

  private fun fireUpdate() {
    listeners.forEach { it(isActive()) }
  }

  private fun doPaint(): Boolean {
    val customization = InternalUICustomization.getInstance()
    return customization == null || customization.isProjectCustomDecorationGradientPaint
  }

  /**
   * @return true if method painted something
   */
  fun paint(window: Window, parent: JComponent, g: Graphics2D): Boolean {
    if (!isActive() || !doPaint()) return false

    val frameHelper = ProjectFrameHelper.getFrameHelper(window) ?: return false
    val project = frameHelper.project ?: return false

    g.color = parent.background
    val height = parent.height
    g.fillRect(0, 0, parent.width, height)

    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    val color = getGradientProjectColor(project)

    val length = Registry.intValue("ide.colorful.toolbar.gradient.radius", 300)
    val offset = project.service<ProjectWidgetGradientLocationService>().gradientOffsetRelativeToRootPane

    if (ComponentUtil.findComponentsOfType(parent, MainToolbar::class.java).firstOrNull() == null
        && !(CustomWindowHeaderUtil.isToolbarInHeader(UISettings.getInstance(), frameHelper.isInFullScreen))) return true

    //additional multiplication by color.alpha is done because alpha will be lost after using blendColorsInRgb (sometimes it's not equals to 255)
    val saturation = Registry.doubleValue("ide.colorful.toolbar.gradient.saturation", 0.85)
                       .coerceIn(0.0, 1.0) * (color.alpha.toDouble() / 255)
    val blendedColor = ColorUtil.blendColorsInRgb(parent.background, color, saturation)

    alignTxToInt(g, null, true, false, PaintUtil.RoundingMode.FLOOR)
    val ctx = ScaleContext.create(g)
    val leftX = alignIntToInt((offset - length).coerceAtLeast(0f).toInt(), ctx, PaintUtil.RoundingMode.FLOOR, null)
    val leftWidth = alignIntToInt((offset - leftX).toInt(), ctx, PaintUtil.RoundingMode.CEIL, null)
    val rightX = leftX + leftWidth
    val rightWidth = alignIntToInt(length, ctx, PaintUtil.RoundingMode.CEIL, null)

    val leftGradientTexture = leftGradientCache.getHorizontalTexture(g, leftWidth, parent.background, blendedColor, leftX)
    val rightGradientTexture = rightGradientCache.getHorizontalTexture(g, rightWidth, blendedColor, parent.background, rightX)

    g.paint = leftGradientTexture
    g.fillRect(leftX, 0, leftWidth, height)

    g.paint = rightGradientTexture
    g.fillRect(rightX, 0, rightWidth, height)

    return true
  }

  override fun dispose() {}
}

/**
 * Automatically repaints the given component whenever the gradient position changes.
 *
 * The component is only repainted if it belongs to the same project frame as the project widget.
 *
 * It is guaranteed that the given component will not leak after it's removed from the Swing hierarchy or just hidden.
 */
internal fun repaintWhenProjectGradientOffsetChanged(componentToRepaint: JComponent) {
  // Using launchOnShow + try-finally prevents leaks:
  // the component is only kept in the set as long as it's showing,
  // and launchOnShow was designed not to keep any extra references to the lambda when the component is not showing.
  componentToRepaint.launchOnShow("ProjectWidgetGradientLocationService.repaintWhenChanged") {
    // This is more complicated than it should be.
    // Ideally, we just want to have a flow of offsets,
    // and a collector on that flow that repaints the component.
    // But for that the component must know the project,
    // and at this point it's still not known:
    // the component is shown before the project is assigned to the frame,
    // and there are no convenient "the project is now known" listeners either.
    // So we need to store the components and query their projects later, when the offset changes.
    val customizerService = ProjectWindowCustomizerService.getInstance()
    customizerService.addGradientRepaintRoot(componentToRepaint)
    try {
      awaitCancellation()
    }
    finally {
      customizerService.removeGradientRepaintRoot(componentToRepaint)
    }
  }
}

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
internal class ProjectWidgetGradientLocationService(private val project: Project, coroutineScope: CoroutineScope) {
  var gradientOffsetRelativeToRootPane: Float = DEFAULT_GRADIENT_OFFSET
    private set

  private val updateFlow = MutableStateFlow<Float?>(null)

  init {
    coroutineScope.launch(CoroutineName("ProjectWidgetGradientLocationService.updateFlow")) {
      // This unusual debouncing is needed because the project widget (that determines the location of the gradient)
      // is sometimes removed and immediately re-added to the toolbar by the toolbar's action update mechanism.
      // This causes the gradient to jump to its default position and then back to the project widget,
      // sometimes slowly enough to be actually visible to the user.
      // Because settings this value to null can only happen when the project widget is removed,
      // we delay the gradient position update then.
      // This can happen in two cases.
      // Either it's an action update, and then we hope that it'll be back within 100 milliseconds;
      // or the user actually removed the project widget (an uncommon case),
      // and then it's not the end of the world if the gradient reacts 100 milliseconds later.
      updateFlow
        .debounce { if (it == null) 100L else 0L }
        .collectLatest {
          withContext(Dispatchers.ui(UiDispatcherKind.STRICT)) {
            setValueNow(it)
          }
        }
    }
  }

  private fun setValueNow(value: Float?) {
    val newValue = value ?: DEFAULT_GRADIENT_OFFSET
    // the usual thing: don't compare floating point values with ==
    if (abs(gradientOffsetRelativeToRootPane - newValue) < 0.1) {
      return
    }

    gradientOffsetRelativeToRootPane = newValue
    for (repaintRoot in ProjectWindowCustomizerService.getInstance().getGradientRepaintRoots()) {
      if (ProjectUtil.getProjectForComponent(repaintRoot) == project) {
        repaintRoot.repaint()
      }
    }
  }

  fun setProjectWidgetIconCenterRelativeToRootPane(value: Float?) {
    updateFlow.value = value
  }
}

private const val DEFAULT_GRADIENT_OFFSET = 150.0f

private class ProjectWindowCustomizerListener : ProjectActivity, UISettingsListener {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val service = serviceAsync<ProjectWindowCustomizerService>()
    withContext(Dispatchers.ui(UiDispatcherKind.STRICT)) {
      service.enableIfNeeded()
      service.setupWorkspaceStorage(project)
    }
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    InternalUICustomization.getInstance()?.updateBackgroundPainter()
    ProjectWindowCustomizerService.getInstance().update(uiSettings.differentiateProjects)
  }
}

private interface ProjectColorStorage {
  var customColor: String?
  var associatedIndex: Int?
  val projectPath: String?

  val isEmpty: Boolean get() = (customColor?.isNotEmpty() != true) && (associatedIndex?.let { it >= 0 } != true)
}

private class WorkspaceProjectColorStorage(val project: Project): ProjectColorStorage {
  override var customColor: String?
    get() = manager.customColor
    set(value) {
      manager.customColor = value
      if (!isMigrating) RecentProjectsManagerBase.getInstanceEx().updateProjectColor(project)
    }

  override var associatedIndex: Int?
    get() = manager.associatedIndex
    set(value) {
      manager.associatedIndex = value
      if (!isMigrating) RecentProjectsManagerBase.getInstanceEx().updateProjectColor(project)
    }

  private var isMigrating = false

  override val projectPath: String? get() = ProjectWindowCustomizerService.projectPath(project)
  private val manager: ProjectColorInfoManager get() = ProjectColorInfoManager.getInstance(project)

  fun getDataFrom(storage: ProjectColorStorage) {
    isMigrating = true
    customColor = storage.customColor
    associatedIndex = storage.associatedIndex
    isMigrating = false
  }
}

private class RecentProjectColorStorage(override val projectPath: String): ProjectColorStorage {
  override var customColor: String?
    get() = getInfo(RecentProjectsManagerBase.getInstanceEx())?.customColor
    set(value) {
      update { info -> info.customColor = value }
    }

  override var associatedIndex: Int?
    get() = getInfo(RecentProjectsManagerBase.getInstanceEx())?.associatedIndex
    set(value) {
      update { info -> info.associatedIndex = value ?: -1 }
    }

  private fun update(block: (RecentProjectColorInfo) -> Unit) {
    val projectsManager = RecentProjectsManagerBase.getInstanceEx()
    val info = getInfo(projectsManager) ?: RecentProjectColorInfo()
    block(info)
    projectsManager.updateProjectColor(projectPath, info)
  }

  private fun getInfo(recentProjectManager: RecentProjectsManagerBase): RecentProjectColorInfo? {
    return recentProjectManager.getProjectMetaInfo(projectPath)?.colorInfo
  }
}
