// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.compiler.server.BuildManager
import com.intellij.facet.Facet
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataKey.Companion.create
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.NoMargin
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurableFilter.ConfigurableId
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseLibrariesConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.FacetStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.GlobalLibrariesConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.JdkListConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectLibrariesConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext
import com.intellij.openapi.ui.DetailsComponent
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.packaging.artifacts.Artifact
import com.intellij.ui.JBSplitter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.UIBundle
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.navigation.BackAction
import com.intellij.ui.navigation.ForwardAction
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Objects
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.max

class ProjectStructureConfigurable(val project: Project) : SearchableConfigurable, Place.Navigator, NoMargin, NoScroll, Disposable {
  protected val myUiState: UIState = UIState()
  private var mySplitter: JBSplitter? = null
  private var myToolbarComponent: JComponent? = null
  private var myToFocus: JComponent? = null

  class UIState {
    var proportion: Float = 0f
    var sideProportion: Float = 0f

    var lastEditedConfigurable: String? = null
  }

  val facetStructureConfigurable: FacetStructureConfigurable
  val artifactsStructureConfigurable: ArtifactsStructureConfigurable

  private var myHistory: History? = null
  private var mySidePanel: SidePanel? = null

  private var myComponent: JPanel? = null
  private var myDetails: Wrapper? = null

  private var mySelectedConfigurable: Configurable? = null

  val projectJdksModel: ProjectSdksModel = ProjectSdksModel()

  var projectConfig: ProjectConfigurable? = null
    private set
  val projectLibrariesConfigurable: ProjectLibrariesConfigurable
  val globalLibrariesConfigurable: GlobalLibrariesConfigurable
  val modulesConfig: ModuleStructureConfigurable

  var isUiInitialized: Boolean = false
    private set

  private val myName2Config: MutableList<Configurable> = ArrayList<Configurable>()
  val context: StructureConfigurableContext
  private val myModuleConfigurator: ModulesConfigurator
  val jdkConfig: JdkListConfigurable

  private var myEmptySelection: JLabel? = null

  private val myObsoleteLibraryFilesRemover: ObsoleteLibraryFilesRemover

  init {
    this.facetStructureConfigurable = FacetStructureConfigurable(this)
    this.artifactsStructureConfigurable = ArtifactsStructureConfigurable(this)

    myModuleConfigurator = ModulesConfigurator(project, this)
    this.context = StructureConfigurableContext(this.project, myModuleConfigurator)
    myModuleConfigurator.setContext(this.context)

    this.projectLibrariesConfigurable = ProjectLibrariesConfigurable(this)
    this.globalLibrariesConfigurable = GlobalLibrariesConfigurable(this)
    this.modulesConfig = ModuleStructureConfigurable(this)

    this.jdkConfig = JdkListConfigurable(this)

    projectLibrariesConfigurable.init(this.context)
    globalLibrariesConfigurable.init(this.context)
    modulesConfig.init(this.context)
    facetStructureConfigurable.init(this.context)
    jdkConfig.init(this.context)
    if (!project.isDefault()) {
      artifactsStructureConfigurable.init(
        this.context,
        this.modulesConfig,
        this.projectLibrariesConfigurable,
        this.globalLibrariesConfigurable
      )
    }
    else {
      Disposer.register(this, this.artifactsStructureConfigurable)
    }

    val propertiesComponent = PropertiesComponent.getInstance(this.project)
    myUiState.lastEditedConfigurable = propertiesComponent.getValue("project.structure.last.edited")
    val proportion = propertiesComponent.getValue("project.structure.proportion")
    myUiState.proportion = if (proportion != null) proportion.toFloat() else 0f
    val sideProportion = propertiesComponent.getValue("project.structure.side.proportion")
    myUiState.sideProportion = if (sideProportion != null) sideProportion.toFloat() else 0f
    myObsoleteLibraryFilesRemover = ObsoleteLibraryFilesRemover(project)
  }

  @NonNls
  override fun getId(): @NonNls String {
    return "project.structure"
  }

  @Nls
  override fun getDisplayName(): @Nls String {
    return JavaUiBundle.message("project.settings.display.name")
  }

  @NonNls
  override fun getHelpTopic(): @NonNls String? {
    val topic = if (mySelectedConfigurable != null) mySelectedConfigurable!!.getHelpTopic() else null
    return Objects.requireNonNullElse<String?>(topic, "reference.settingsdialog.project.structure.general")
  }

  override fun createComponent(): JComponent? {
    myComponent = MyPanel()
    myDetails = Wrapper()
    myHistory = History(this)
    myEmptySelection = JLabel(JavaUiBundle.message("project.structure.empty.text"), SwingConstants.CENTER)
    mySplitter = OnePixelSplitter(false, .15f)
    mySplitter!!.setSplitterProportionKey("ProjectStructure.TopLevelElements")
    mySplitter!!.setHonorComponentsMinimumSize(true)

    initSidePanel()

    val left: JPanel = object : JPanel(BorderLayout()) {
      override fun getMinimumSize(): Dimension {
        val original = super.getMinimumSize()
        return Dimension(max(original.width, 100), original.height)
      }
    }

    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(BackAction(myComponent, this.context))
    toolbarGroup.add(ForwardAction(myComponent, this.context))
    val toolbar = ActionManager.getInstance().createActionToolbar("ProjectStructure", toolbarGroup, true)
    toolbar.setTargetComponent(myComponent)
    myToolbarComponent = toolbar.getComponent()
    left.setBackground(UIUtil.SIDE_PANEL_BACKGROUND)
    myToolbarComponent!!.setBackground(UIUtil.SIDE_PANEL_BACKGROUND)
    left.add(myToolbarComponent, BorderLayout.NORTH)
    left.add(mySidePanel, BorderLayout.CENTER)

    mySplitter!!.setFirstComponent(left)
    mySplitter!!.setSecondComponent(myDetails)

    myComponent!!.add(mySplitter, BorderLayout.CENTER)

    this.isUiInitialized = true

    return myComponent
  }

  override fun dispose() {
  }

  private fun initSidePanel() {
    val isDefaultProject = this.project === ProjectManager.getInstance().getDefaultProject()

    mySidePanel = SidePanel(this)
    mySidePanel!!.addSeparator(JavaUiBundle.message("project.settings.title"))
    addProjectConfig()
    if (!isDefaultProject) {
      addModulesConfig()
    }
    addProjectLibrariesConfig()

    if (!isDefaultProject) {
      addFacetsConfig()
      addArtifactsConfig()
    }

    mySidePanel!!.addSeparator(JavaUiBundle.message("project.structure.platform.title"))
    addJdkListConfig()
    addGlobalLibrariesConfig()

    mySidePanel!!.addSeparator("--")
    addErrorPane()
    mySidePanel!!.getList().getAccessibleContext().setAccessibleName(UIBundle.message("project.structure.categories.accessible.name"))
  }

  private fun addArtifactsConfig() {
    addConfigurable(this.artifactsStructureConfigurable, ConfigurableId.ARTIFACTS)
  }

  private fun addConfigurable(configurable: Configurable, configurableId: ConfigurableId) {
    addConfigurable(configurable, isAvailable(configurableId))
  }

  private fun isAvailable(id: ConfigurableId): Boolean {
    for (filter in ProjectStructureConfigurableFilter.EP_NAME.extensions) {
      if (!filter.isAvailable(id, this.project)) {
        return false
      }
    }
    return true
  }

  private fun addFacetsConfig() {
    if (facetStructureConfigurable.isVisible()) {
      addConfigurable(this.facetStructureConfigurable, ConfigurableId.FACETS)
    }
  }

  private fun addJdkListConfig() {
    addConfigurable(this.jdkConfig, ConfigurableId.JDK_LIST)
  }

  private fun addProjectConfig() {
    this.projectConfig = ProjectConfigurable(this.project, this.context, myModuleConfigurator, this.projectJdksModel)
    addConfigurable(this.projectConfig!!, ConfigurableId.PROJECT)
  }

  private fun addProjectLibrariesConfig() {
    addConfigurable(this.projectLibrariesConfigurable, ConfigurableId.PROJECT_LIBRARIES)
  }

  private fun addErrorPane() {
    addConfigurable(ErrorPaneConfigurable(this.project, this.context, Runnable { mySidePanel!!.getList().repaint() }), true)
  }

  private fun addGlobalLibrariesConfig() {
    addConfigurable(this.globalLibrariesConfigurable, ConfigurableId.GLOBAL_LIBRARIES)
  }

  private fun addModulesConfig() {
    addConfigurable(this.modulesConfig, ConfigurableId.MODULES)
  }

  override fun isModified(): Boolean {
    if (projectJdksModel.isModified()) {
      return true
    }
    for (each in myName2Config) {
      if (each.isModified()) return true
    }

    return false
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    if (projectJdksModel.isModified()) {
      projectJdksModel.apply()
    }
    for (each in myName2Config) {
      if (each is BaseStructureConfigurable && each.isModified()) {
        each.checkCanApply()
      }
    }
    val exceptionRef = Ref.create<ConfigurationException>()
    try {
      for (each in myName2Config) {
        if (each.isModified()) {
          each.apply()
        }
      }
    }
    catch (e: ConfigurationException) {
      exceptionRef.set(e)
    }

    if (!exceptionRef.isNull) {
      throw exceptionRef.get()
    }

    myObsoleteLibraryFilesRemover.deleteFiles()
    context.daemonAnalyzer.clearCaches()
    BuildManager.getInstance().scheduleAutoMake()
  }

  override fun reset() {
    context.reset()

    projectJdksModel.reset(this.project)

    var toSelect: Configurable? = null
    for (each in myName2Config) {
      if (myUiState.lastEditedConfigurable != null && myUiState.lastEditedConfigurable == each.getDisplayName()) {
        toSelect = each
      }
      if (each is MasterDetailsComponent) {
        each.setHistory(myHistory)
      }
      each.reset()
    }

    myHistory!!.clear()

    if (toSelect == null && !myName2Config.isEmpty()) {
      toSelect = myName2Config.iterator().next()
    }

    removeSelected()

    navigateTo(if (toSelect != null) createPlaceFor(toSelect) else null, false)

    if (myUiState.proportion > 0) {
      mySplitter!!.setProportion(myUiState.proportion)
    }
  }

  override fun disposeUIResources() {
    if (!this.isUiInitialized) return
    val propertiesComponent = PropertiesComponent.getInstance(this.project)
    propertiesComponent.setValue("project.structure.last.edited", myUiState.lastEditedConfigurable)
    propertiesComponent.setValue("project.structure.proportion", myUiState.proportion.toString())
    propertiesComponent.setValue("project.structure.side.proportion", myUiState.sideProportion.toString())

    myUiState.proportion = mySplitter!!.getProportion()
    saveSideProportion()
    context.getDaemonAnalyzer().stop()
    for (each in myName2Config) {
      each.disposeUIResources()
    }
    context.clear()
    myName2Config.clear()

    myModuleConfigurator.getFacetsConfigurator().clearMaps()
    myHistory!!.clear()

    this.isUiInitialized = false
    myComponent = null
    mySplitter!!.removeAll()
    mySplitter = null
    myToolbarComponent = null
    myDetails!!.removeAll()
    mySidePanel = null
  }

  fun getHistory(): History {
    return myHistory!!
  }

  override fun setHistory(history: History) {
    myHistory = history
  }

  override fun queryPlace(place: Place) {
    place.putPath(CATEGORY, mySelectedConfigurable)
    Place.queryFurther(mySelectedConfigurable, place)
  }

  fun selectProjectGeneralSettings(requestFocus: Boolean): ActionCallback {
    return navigateTo(createProjectConfigurablePlace(), requestFocus)
  }

  fun createProjectConfigurablePlace(): Place {
    return createPlaceFor(this.projectConfig)
  }

  fun select(moduleToSelect: String?, editorNameToSelect: String?, requestFocus: Boolean): ActionCallback {
    var place = createModulesPlace()
    if (moduleToSelect != null) {
      val module: Module = checkNotNull(ModuleManager.getInstance(this.project).findModuleByName(moduleToSelect))
      place = place.putPath(MasterDetailsComponent.TREE_OBJECT, module).putPath(ModuleEditor.SELECTED_EDITOR_NAME, editorNameToSelect)
    }
    return navigateTo(place, requestFocus)
  }

  fun createModulesPlace(): Place {
    return createPlaceFor(this.modulesConfig)
  }

  fun createModulePlace(module: Module): Place {
    return createModulesPlace().putPath(MasterDetailsComponent.TREE_OBJECT, module)
  }

  fun select(facetToSelect: Facet<*>?, requestFocus: Boolean): ActionCallback {
    var place = createModulesPlace()
    if (facetToSelect != null) {
      place = place.putPath(MasterDetailsComponent.TREE_OBJECT, facetToSelect)
    }
    return navigateTo(place, requestFocus)
  }

  fun select(sdk: Sdk, requestFocus: Boolean): ActionCallback {
    val place: Place = createPlaceFor(
      this.jdkConfig
    )
    place.putPath(MasterDetailsComponent.TREE_NAME, sdk.getName())
    return navigateTo(place, requestFocus)
  }

  fun selectGlobalLibraries(requestFocus: Boolean): ActionCallback {
    val place: Place = createPlaceFor(
      this.globalLibrariesConfigurable
    )
    return navigateTo(place, requestFocus)
  }

  fun selectProjectOrGlobalLibrary(library: Library, requestFocus: Boolean): ActionCallback {
    val place = createProjectOrGlobalLibraryPlace(library)
    return navigateTo(place, requestFocus)
  }

  fun createProjectOrGlobalLibraryPlace(library: Library): Place {
    val place: Place = createPlaceFor(getConfigurableFor(library))
    place.putPath(MasterDetailsComponent.TREE_NAME, library.getName())
    return place
  }

  fun select(artifact: Artifact?, requestFocus: Boolean): ActionCallback {
    val place = createArtifactPlace(artifact)
    return navigateTo(place, requestFocus)
  }

  fun createArtifactPlace(artifact: Artifact?): Place {
    val place: Place = createPlaceFor(
      this.artifactsStructureConfigurable
    )
    if (artifact != null) {
      place.putPath(MasterDetailsComponent.TREE_NAME, artifact.getName())
    }
    return place
  }

  fun select(libraryOrderEntry: LibraryOrderEntry, requestFocus: Boolean): ActionCallback? {
    val lib = libraryOrderEntry.getLibrary()
    if (lib == null || lib.getTable() == null) {
      return selectOrderEntry(libraryOrderEntry.getOwnerModule(), libraryOrderEntry)
    }
    val place: Place = createPlaceFor(getConfigurableFor(lib))
    place.putPath(MasterDetailsComponent.TREE_NAME, libraryOrderEntry.getLibraryName())
    return navigateTo(place, requestFocus)
  }

  fun selectOrderEntry(module: Module, orderEntry: OrderEntry?): ActionCallback? {
    return modulesConfig.selectOrderEntry(module, orderEntry)
  }

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
    val toSelect = place!!.getPath(CATEGORY) as Configurable?

    var detailsContent = myDetails!!.getTargetComponent()

    if (mySelectedConfigurable !== toSelect) {
      if (mySelectedConfigurable is BaseStructureConfigurable) {
        (mySelectedConfigurable as BaseStructureConfigurable).onStructureUnselected()
      }
      saveSideProportion()
      removeSelected()

      if (toSelect != null) {
        detailsContent = toSelect.createComponent()
        myDetails!!.setContent(detailsContent)
      }

      mySelectedConfigurable = toSelect
      if (mySelectedConfigurable != null) {
        myUiState.lastEditedConfigurable = mySelectedConfigurable!!.getDisplayName()
      }

      if (toSelect is MasterDetailsComponent) {
        if (myUiState.sideProportion > 0) {
          toSelect.getSplitter().setProportion(myUiState.sideProportion)
        }
        toSelect.setHistory(myHistory)
      }

      if (toSelect is DetailsComponent.Facade) {
        (toSelect as DetailsComponent.Facade).getDetailsComponent()
          .setBannerMinHeight(myToolbarComponent!!.getPreferredSize().height)
      }

      if (toSelect is BaseStructureConfigurable) {
        toSelect.onStructureSelected()
      }
    }


    if (detailsContent != null) {
      var toFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(detailsContent)
      if (toFocus == null) {
        toFocus = detailsContent
      }
      if (requestFocus) {
        myToFocus = toFocus
        UIUtil.requestFocus(toFocus)
      }
    }

    val result = ActionCallback()
    Place.goFurther(toSelect, place, requestFocus).notifyWhenDone(result)

    myDetails!!.revalidate()
    myDetails!!.repaint()

    if (toSelect != null) {
      mySidePanel!!.select(createPlaceFor(toSelect))
    }

    if (!myHistory!!.isNavigatingNow() && mySelectedConfigurable != null) {
      myHistory!!.pushQueryPlace()
    }

    return result
  }

  private fun saveSideProportion() {
    if (mySelectedConfigurable is MasterDetailsComponent) {
      myUiState.sideProportion = (mySelectedConfigurable as MasterDetailsComponent).getSplitter().getProportion()
    }
  }

  private fun removeSelected() {
    myDetails!!.removeAll()
    mySelectedConfigurable = null
    myUiState.lastEditedConfigurable = null

    myDetails!!.add(myEmptySelection, BorderLayout.CENTER)
  }

  fun registerObsoleteLibraryRoots(roots: Collection<VirtualFile>) {
    myObsoleteLibraryFilesRemover.registerObsoleteLibraryRoots(roots)
  }

  private fun addConfigurable(configurable: Configurable, addToSidePanel: Boolean) {
    myName2Config.add(configurable)

    if (addToSidePanel) {
      mySidePanel!!.addPlace(createPlaceFor(configurable), Presentation(configurable.getDisplayName()))
    }
  }

  private inner class MyPanel : JPanel(BorderLayout()), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      sink.set<ProjectStructureConfigurable>(KEY, this@ProjectStructureConfigurable)
      sink.set<History>(History.KEY, getHistory())
    }

    override fun getPreferredSize(): Dimension {
      return JBUI.size(1024, 768)
    }
  }

  fun getConfigurableFor(library: Library): BaseLibrariesConfigurable {
    if (LibraryTablesRegistrar.PROJECT_LEVEL == library.getTable().getTableLevel()) {
      return this.projectLibrariesConfigurable
    }
    else {
      return this.globalLibrariesConfigurable
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return myToFocus
  }

  companion object {
    val KEY: DataKey<ProjectStructureConfigurable> = create<ProjectStructureConfigurable>("ProjectStructureConfiguration")

    @NonNls
    const val CATEGORY: @NonNls String = "category"

    @JvmStatic
    fun getInstance(project: Project): ProjectStructureConfigurable {
      return project.getService(ProjectStructureConfigurable::class.java)
    }

    private fun createPlaceFor(configurable: Configurable?): Place {
      return Place().putPath(CATEGORY, configurable)
    }
  }
}
