// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.setToolTipText
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.shortenTextWithEllipsis
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagLayout
import java.awt.KeyboardFocusManager
import java.awt.LayoutManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.accessibility.Accessible
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import org.jetbrains.annotations.Nls

internal class UnifiedPluginsPageView @RequiresEdt constructor(
  private val onSearchChanged: (String) -> Unit,
  private val onSelectionChanged: (List<PluginOccurrenceId>) -> Unit,
  private val onSectionExpansionChanged: (PluginSectionId, Boolean) -> Unit,
  private val rowFactory: PluginRowFactory? = null,
  private val detailsPresenter: PluginDetailsPresenter? = null,
  private val onSectionRetryRequested: (PluginSectionId) -> Unit = {},
  onSearchControl: (UnifiedPluginSearchControlIntent) -> Unit = {},
  private val onBundledCategoryAction: (BundledPluginCategoryGroupState) -> Unit = {},
  private val createBundledCategoryPromotion: (String) -> JComponent? = { null },
) : AutoCloseable {
  private val searchField = SearchTextField(SEARCH_HISTORY_PROPERTY)
  private val searchToolbar = UnifiedPluginsSearchToolbar(onSearchControl)
  private val noResultsText = IdeBundle.message("plugins.configurable.nothing.found")
  private val sectionsPanel = JBPanelWithEmptyText(ListLayout.vertical(SECTION_GAP)).apply {
    emptyText.text = noResultsText
  }
  private val scrollPane = object : JBScrollPane(sectionsPanel) {
    override fun doLayout() {
      super.doLayout()
      val scrollBar = verticalScrollBar
      val topInset = JBUI.scale(SECTION_HEADER_HEIGHT).coerceAtMost(scrollBar.height)
      scrollBar.setBounds(scrollBar.x, scrollBar.y + topInset, scrollBar.width, scrollBar.height - topInset)
    }
  }
  private val stickyHeaderHost = StickyHeaderHost()
  private val scrollContainer = JBLayeredPane()
  private val sectionViews = LinkedHashMap<PluginSectionId, SectionView>()
  private val rowReconciler = rowFactory?.createReconciler { occurrenceId, row ->
    detailsPresenter?.beforeRowRelease(occurrenceId, row)
  }
  private var stickySectionView: SectionView? = null
  private var renderedState: UnifiedPluginsPageState? = null
  private var renderedSearchControls: UnifiedPluginsSearchControlsState? = null
  private var handlingViewportChange = false
  private var rendering = false
  private var updatingSearchField = false
  private val resultsAnnouncementTimer: Timer
  private var lastAnnouncedResults: ResultsAnnouncementSignature? = null

  val component: JComponent
  val searchComponent: JComponent = SearchFieldWithExtension(searchToolbar.component, searchField).apply {
    val maximumWidth = JBUI.scale(SEARCH_COMPONENT_WIDTH)
    preferredSize = Dimension(maximumWidth, preferredSize.height)
    maximumSize = Dimension(maximumWidth, maximumSize.height)
  }

  init {
    require(detailsPresenter == null || rowFactory != null) { "Plugin details require real plugin rows" }
    configureSearchField()

    sectionsPanel.apply {
      background = PluginManagerConfigurable.MAIN_BG_COLOR
    }

    scrollPane.apply {
      border = JBUI.Borders.empty()
      setOverlappingScrollBar(true)
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
      verticalScrollBar.isOpaque = false
      viewport.background = PluginManagerConfigurable.MAIN_BG_COLOR
    }
    scrollPane.viewport.addChangeListener { handleViewportChange() }

    stickyHeaderHost.apply {
      background = PluginManagerConfigurable.MAIN_BG_COLOR
      isVisible = false
    }

    scrollContainer.apply {
      layout = ScrollContainerLayout(scrollPane)
      add(scrollPane, JLayeredPane.DEFAULT_LAYER as Any)
      add(stickyHeaderHost, JLayeredPane.PALETTE_LAYER as Any)
    }

    val listPanel = JPanel(BorderLayout()).apply {
      background = PluginManagerConfigurable.MAIN_BG_COLOR
      border = CustomLineBorder(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR, JBUI.insetsTop(1))
      minimumSize = Dimension(JBUI.scale(PLUGIN_LIST_MIN_WIDTH), 0)
      add(scrollContainer, BorderLayout.CENTER)
    }

    val detailsComponent = detailsPresenter?.component ?: createStaticDetailsComponent()

    component = object : OnePixelSplitter(false, DEFAULT_SPLIT_PROPORTION) {
      override fun createDivider(): Divider {
        return super.createDivider().apply {
          background = PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR
        }
      }
    }.apply {
      accessibleContext.accessibleName = IdeBundle.message("title.plugins")
      lackOfSpaceStrategy = Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE
      firstComponent = listPanel
      secondComponent = detailsComponent
    }
    resultsAnnouncementTimer = Timer(RESULTS_ANNOUNCEMENT_DELAY_MS) { announceRenderedResults() }.apply {
      isRepeats = false
    }
  }

  @RequiresEdt
  fun render(state: UnifiedPluginsPageState) {
    val viewportAnchor = captureViewportAnchor()
    renderedState = state
    rendering = true
    try {
      updateSearchField(state.query.rawQuery)
      updateSearchControls(state.searchControls)
      sectionsPanel.accessibleContext.accessibleName = noResultsText.takeIf { state.sections.isEmpty() }

      val visibleSectionIds = state.sections.mapTo(HashSet(), PluginSectionState::id)
      val removedSectionIds = sectionViews.keys.filter { it !in visibleSectionIds }
      removedSectionIds.forEach { sectionId ->
        val removedSectionView = sectionViews.remove(sectionId) ?: return@forEach
        if (stickySectionView === removedSectionView) {
          setStickySection(null)
        }
        sectionsPanel.remove(removedSectionView.component)
      }

      val orderedSectionViews = ArrayList<SectionView>(state.sections.size)
      for (section in state.sections) {
        val sectionView = sectionViews.getOrPut(section.id) {
          SectionView(
            section.id,
            isRendering = { rendering },
            onSelectionChanged = onSelectionChanged,
            onExpansionChanged = onSectionExpansionChanged,
            onRetryRequested = onSectionRetryRequested,
            onRealizationChanged = ::onSectionRealizationChanged,
            onBundledCategoryAction = onBundledCategoryAction,
            createBundledCategoryPromotion = createBundledCategoryPromotion,
            realRows = rowFactory != null,
          )
        }
        sectionView.render(section, state.query.revision, state.selectedOccurrences, viewportAnchor?.id)
        orderedSectionViews.add(sectionView)
      }
      reconcileSectionOrder(orderedSectionViews)
      reconcileRealRows(state)
    }
    finally {
      rendering = false
    }

    layoutRenderedSections()
    restoreViewportAnchor(viewportAnchor)
    updateStickyHeader()
    sectionsPanel.repaint()
    scheduleResultsAnnouncement()
  }

  @RequiresEdt
  fun requestSearchFocus() {
    IdeFocusManager.getGlobalInstance().requestFocus(searchField, true)
  }

  @RequiresEdt
  fun setSearchQuery(query: String) {
    updateSearchField(query)
  }

  private fun updateSearchField(query: String) {
    if (searchField.text == query) return
    updatingSearchField = true
    try {
      searchField.text = query
    }
    finally {
      updatingSearchField = false
    }
  }

  private fun updateSearchControls(state: UnifiedPluginsSearchControlsState) {
    if (renderedSearchControls == state) return
    renderedSearchControls = state
    searchToolbar.render(state)
  }

  @RequiresEdt
  override fun close() {
    resultsAnnouncementTimer.stop()
    setStickySection(null)
    detailsPresenter?.close()
    rowReconciler?.close()
    rowFactory?.rowsRendered(emptyList())
    sectionViews.values.forEach { it.setRealRows(emptyList()) }
    renderedState = null
  }

  private fun reconcileRealRows(state: UnifiedPluginsPageState) {
    val factory = rowFactory ?: return
    val reconciler = checkNotNull(rowReconciler)
    val specifications = state.sections.flatMap { section ->
      sectionViews[section.id].orEmptyRealizedItems().map { item -> factory.specification(section, item) }
    }
    val bindings = reconciler.reconcile(specifications)
    val bindingsBySection = bindings.groupBy { it.occurrenceId.sectionId }
    sectionViews.values.forEach { sectionView ->
      sectionView.setRealRows(bindingsBySection[sectionView.id].orEmpty())
    }
    factory.rowsRendered(bindings)
    val selectedOccurrences = state.selectedOccurrences.toHashSet()
    bindings.forEach { binding -> binding.row.renderSelection(binding.occurrenceId in selectedOccurrences) }
    renderDetails(state, reconciler)
  }

  private fun renderDetails(state: UnifiedPluginsPageState, reconciler: PluginRowReconciler<PluginRow, Any>) {
    val presenter = detailsPresenter ?: return
    val occurrenceId = state.selectedOccurrence
    val mode = occurrenceId?.let { pluginDetailsMode(it.sectionId) }
               ?: if (state.query.usesMarketplaceSearch()) PluginDetailsMode.MARKETPLACE else PluginDetailsMode.LOCAL
    val selection = state.selectedOccurrences.mapNotNull { selectedOccurrence ->
      reconciler.row(selectedOccurrence)?.let { PluginDetailsSelection(selectedOccurrence, it) }
    }
    presenter.render(mode, selection)
  }

  private fun createStaticDetailsComponent(): JComponent {
    val emptyDetailsLabel = JBLabel(IdeBundle.message("plugins.configurable.details.none.selected")).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    return JPanel(GridBagLayout()).apply {
      background = PluginManagerConfigurable.MAIN_BG_COLOR
      accessibleContext.accessibleName = emptyDetailsLabel.text
      add(emptyDetailsLabel)
    }
  }

  private fun reconcileSectionOrder(orderedSectionViews: List<SectionView>) {
    orderedSectionViews.forEachIndexed { index, sectionView ->
      val sectionComponent = sectionView.component
      if (sectionComponent.parent !== sectionsPanel) {
        sectionsPanel.add(sectionComponent, index)
      }
      else if (sectionsPanel.getComponent(index) !== sectionComponent) {
        sectionsPanel.setComponentZOrder(sectionComponent, index)
      }
      sectionView.setDividerVisible(index > 0)
    }
  }

  private fun captureViewportAnchor(): ViewportAnchor? {
    val viewRect = scrollPane.viewport.viewRect
    if (viewRect.isEmpty) return null

    val occurrence = sectionViews.values
                       .asSequence()
                       .sortedBy { it.component.y }
                       .firstNotNullOfOrNull { it.firstVisibleOccurrence(sectionsPanel, viewRect) }
                     ?: return null
    return ViewportAnchor(occurrence.id, occurrence.bounds.y - viewRect.y)
  }

  private fun layoutRenderedSections() {
    sectionsPanel.revalidate()
    scrollContainer.doLayout()
    scrollPane.doLayout()
    scrollPane.viewport.doLayout()
    sectionsPanel.doLayout()
    sectionViews.values.forEach(SectionView::doLayout)
    stickyHeaderHost.doLayout()
  }

  private fun restoreViewportAnchor(anchor: ViewportAnchor?) {
    if (anchor == null) return
    val bounds = sectionViews[anchor.id.sectionId]?.occurrenceBounds(anchor.id, sectionsPanel) ?: return

    val viewport = scrollPane.viewport
    val maximumY = (sectionsPanel.height - viewport.extentSize.height).coerceAtLeast(0)
    val targetY = (bounds.y - anchor.offsetFromViewport).coerceIn(0, maximumY)
    viewport.viewPosition = Point(viewport.viewPosition.x, targetY)
  }

  private fun handleViewportChange() {
    if (rendering || handlingViewportChange) return

    handlingViewportChange = true
    try {
      val viewRect = scrollPane.viewport.viewRect
      if (sectionViews.values.count { it.realizeVisibleItems(sectionsPanel, viewRect) } > 0) {
        renderedState?.let(::reconcileRealRows)
        layoutRenderedSections()
      }
      updateStickyHeader()
    }
    finally {
      handlingViewportChange = false
    }
  }

  private fun onSectionRealizationChanged() {
    renderedState?.let(::reconcileRealRows)
    layoutRenderedSections()
    updateStickyHeader()
  }

  private fun updateStickyHeader() {
    val viewRect = scrollPane.viewport.viewRect
    if (viewRect.isEmpty || sectionsPanel.width == 0) {
      setStickySection(null)
      return
    }

    val orderedSections = sectionViews.values.sortedBy { it.component.y }
    val stickyIndex = orderedSections.indexOfLast { sectionView ->
      sectionView.headerBounds(sectionsPanel).y <= viewRect.y
    }
    if (stickyIndex < 0) {
      setStickySection(null)
      return
    }

    val stickySection = orderedSections[stickyIndex]
    setStickySection(stickySection)

    val headerBounds = stickySection.headerBounds(sectionsPanel)
    val viewportOrigin = SwingUtilities.convertPoint(scrollPane.viewport, Point(), scrollContainer)
    val headerHeight = stickySection.headerPreferredHeight
    val nextHeaderTop = orderedSections.getOrNull(stickyIndex + 1)
      ?.headerBounds(sectionsPanel)
      ?.let { it.y - viewRect.y + viewportOrigin.y }
    val stickyY = if (nextHeaderTop == null) {
      viewportOrigin.y
    }
    else {
      minOf(viewportOrigin.y, nextHeaderTop - headerHeight)
    }
    stickyHeaderHost.showDivider = stickyY < viewportOrigin.y
    stickyHeaderHost.showGradient = stickyY == viewportOrigin.y &&
                                        stickySection.hasItemsBehindHeader(sectionsPanel, viewRect.y, headerHeight)
    val stickyX = headerBounds.x - viewRect.x + viewportOrigin.x
    val stickyWidth = headerBounds.width
    stickyHeaderHost.updateOverflow(headerHeight)
    val stickyBounds = Rectangle(
      stickyX,
      stickyY,
      stickyWidth,
      headerHeight + JBUI.scale(STICKY_HEADER_GRADIENT_HEIGHT),
    )
    stickyHeaderHost.bounds = stickyBounds
    stickyHeaderHost.doLayout()
    stickySection.doLayout()
    stickyHeaderHost.repaint()
  }

  private fun setStickySection(sectionView: SectionView?) {
    if (stickySectionView === sectionView) return

    stickySectionView?.moveHeaderBack(stickyHeaderHost)
    stickySectionView = sectionView
    if (sectionView == null) {
      stickyHeaderHost.showDivider = false
      stickyHeaderHost.showGradient = false
      stickyHeaderHost.isVisible = false
      stickyHeaderHost.removeAll()
    }
    else {
      sectionView.moveHeaderTo(stickyHeaderHost)
      stickyHeaderHost.isVisible = true
    }
  }

  private fun scheduleResultsAnnouncement() {
    if (AccessibleAnnouncerUtil.isAnnouncingAvailable()) {
      resultsAnnouncementTimer.restart()
    }
  }

  private fun announceRenderedResults() {
    val state = renderedState ?: return
    if (!component.isShowing) return
    val signature = resultsAnnouncementSignature(state)
    if (signature == lastAnnouncedResults) return
    lastAnnouncedResults = signature
    AccessibleAnnouncerUtil.announce(component as Accessible, pluginResultsAnnouncement(state), false)
  }

  private fun resultsAnnouncementSignature(state: UnifiedPluginsPageState): ResultsAnnouncementSignature {
    return ResultsAnnouncementSignature(
      queryRevision = state.query.revision,
      sections = state.sections.map { section ->
        SectionAnnouncementSignature(section.id, section.count, section.status)
      },
    )
  }

  private fun configureSearchField() {
    searchField.textEditor.apply {
      accessibleContext.accessibleName = IdeBundle.message("plugin.manager.search.accessible.name")
      emptyText.text = IdeBundle.message("plugin.manager.search.all.plugins")
      background = PluginManagerConfigurable.SEARCH_BG_COLOR
      document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          if (!updatingSearchField) {
            onSearchChanged(searchField.text)
          }
        }
      })
    }
  }

  private class SectionView(
    val id: PluginSectionId,
    private val isRendering: () -> Boolean,
    private val onSelectionChanged: (List<PluginOccurrenceId>) -> Unit,
    onExpansionChanged: (PluginSectionId, Boolean) -> Unit,
    onRetryRequested: (PluginSectionId) -> Unit,
    private val onRealizationChanged: () -> Unit,
    private val onBundledCategoryAction: (BundledPluginCategoryGroupState) -> Unit,
    private val createBundledCategoryPromotion: (String) -> JComponent?,
    private val realRows: Boolean,
  ) {
    private val model = DefaultListModel<PluginItemState>()
    private val titleLabel = JBLabel()
    private val statusLabel = JBLabel()
    private val titleAndStatus = JPanel(HorizontalLayout(JBUI.scale(TITLE_STATUS_GAP))).apply {
      isOpaque = false
      add(titleLabel)
      add(statusLabel)
      addComponentListener(object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
          updateTitlePresentation()
          doLayout()
        }
      })
    }
    private val loadingIcon = AnimatedIcon.Default()
    private val errorLabel = JBLabel()
    private val retryLink = ActionLink().apply {
      text = IdeBundle.message("plugin.manager.refresh")
      addActionListener { onRetryRequested(id) }
    }
    private val errorPanel = JPanel(HorizontalLayout(JBUI.scale(ERROR_RETRY_GAP))).apply {
      isOpaque = false
      isVisible = false
      border = JBUI.Borders.empty(ERROR_INSET)
      add(errorLabel)
    }
    private val list = JBList(model)
    private val rowsPanel = JPanel(ListLayout.vertical()).apply {
      background = PluginManagerConfigurable.MAIN_BG_COLOR
    }
    private val realRowComponents = LinkedHashMap<PluginOccurrenceId, JComponent>()
    private val categoryHeaderViews = LinkedHashMap<String, CategoryHeaderView>()
    private val categoryPromotionPanels = LinkedHashMap<String, JComponent>()
    private val categoriesWithoutPromotion = HashSet<String>()
    private val unrealizedItemsSpacer = JPanel().apply {
      isFocusable = false
      isOpaque = false
    }
    private val headerSlot = JPanel(BorderLayout()).apply {
      isOpaque = false
    }
    private val header: JComponent
    private var items: List<PluginItemState> = emptyList()
    private var categoryGroups: List<BundledPluginCategoryGroupState> = emptyList()
    private var categoryGroupsByPluginId: Map<PluginId, BundledPluginCategoryGroupState> = emptyMap()
    private var categoryStartIndices: IntArray = IntArray(0)
    private var realizedItemCount = 0
    private var renderedQueryRevision: Long? = null
    private var expanded = false
    private var fullTitle: @Nls String = ""
    private val expansionLink = ActionLink().apply {
      addActionListener {
        onExpansionChanged(id, !expanded)
      }
    }
    private val dividerBorder = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)

    val component: JComponent

    init {
      titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
      statusLabel.foreground = UIUtil.getContextHelpForeground()

      list.apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = JBUI.scale(STATIC_ROW_HEIGHT)
        background = PluginManagerConfigurable.MAIN_BG_COLOR
        cellRenderer = textListCellRenderer { item ->
          item.name ?: item.pluginId.idString
        }
        addListSelectionListener { event ->
          if (!event.valueIsAdjusting && !isRendering()) {
            if (selectedIndex == realizedItemCount - 1 && realizeNextChunk()) {
              onRealizationChanged()
            }
            onSelectionChanged(selectedValue?.let { listOf(PluginOccurrenceId(id, it.pluginId)) }.orEmpty())
          }
        }
      }
      titleLabel.labelFor = list
      if (realRows) {
        titleLabel.labelFor = rowsPanel
      }

      header = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(
          SECTION_HEADER_TOP_INSET,
          SECTION_HEADER_LEFT_INSET,
          SECTION_HEADER_BOTTOM_INSET,
          SECTION_HEADER_RIGHT_INSET,
        )
        add(titleAndStatus, BorderLayout.CENTER)
        add(expansionLink, BorderLayout.EAST)
        val fixedHeight = JBUI.scale(SECTION_HEADER_HEIGHT)
        preferredSize = Dimension(0, fixedHeight)
        minimumSize = Dimension(0, fixedHeight)
        maximumSize = Dimension(Int.MAX_VALUE, fixedHeight)
      }
      headerSlot.add(header)

      component = SectionPanel().apply {
        background = PluginManagerConfigurable.MAIN_BG_COLOR
        add(JPanel(BorderLayout()).apply {
          isOpaque = false
          add(headerSlot, BorderLayout.NORTH)
          add(errorPanel, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        add(if (realRows) rowsPanel else list, BorderLayout.CENTER)
        add(unrealizedItemsSpacer, BorderLayout.SOUTH)
      }
    }

    fun render(
      section: PluginSectionState,
      queryRevision: Long,
      selectedOccurrences: List<PluginOccurrenceId>,
      viewportAnchorOccurrence: PluginOccurrenceId?,
    ) {
      check(section.id == id)

      val title = pluginSectionTitle(section)
      fullTitle = title
      titleLabel.text = title
      titleLabel.accessibleContext.accessibleName = title
      list.accessibleContext.accessibleName = title
      rowsPanel.accessibleContext.accessibleName = title

      val count = section.count
      statusLabel.apply {
        @NlsSafe val countText = when {
          count == null -> ""
          section.exceedsDisplayLimit -> IdeBundle.message(
            "plugins.configurable.count.more",
            PluginSectionState.MAX_DISPLAYED_ITEM_COUNT,
          )
          else -> count.toString()
        }
        text = countText
        icon = if (count == null) loadingIcon else null
        accessibleContext.accessibleName = if (count == null) {
          IdeBundle.message("plugins.configurable.section.loading", title)
        }
        else {
          text
        }
      }
      updateTitlePresentation()
      val error = when (val status = section.status) {
        is PluginSectionStatus.Degraded -> status.error
        is PluginSectionStatus.Failed -> status.error
        is PluginSectionStatus.Loading, PluginSectionStatus.Ready -> null
      }
      errorPanel.isVisible = error != null
      errorLabel.text = error?.message
      if (error?.retryable == true) {
        if (retryLink.parent !== errorPanel) errorPanel.add(retryLink)
      }
      else {
        errorPanel.remove(retryLink)
      }

      val visibleItems = section.visibleItems
      val preserveRealization = expanded &&
                                  section.expanded &&
                                  renderedQueryRevision == queryRevision &&
                                  hasSameRealizedPrefix(visibleItems)
      expanded = section.expanded
      renderedQueryRevision = queryRevision
      expansionLink.apply {
        isVisible = section.canExpand
        val expanded = section.expanded
        text = IdeBundle.message(if (expanded) "plugins.configurable.show.less" else "plugins.configurable.show.more")
        setIcon(if (expanded) AllIcons.General.ChevronUp else AllIcons.General.ChevronDown, true)
      }

      items = visibleItems
      categoryGroups = section.categoryGroups
      updateCategoryIndexes()
      val baseRealizedItemCount = if (section.expanded) {
        if (preserveRealization) realizedItemCount.coerceAtLeast(REALIZATION_CHUNK_SIZE) else REALIZATION_CHUNK_SIZE
      }
      else {
        items.size
      }
      val selectedItemCount = selectedOccurrences.asSequence()
                                .filter { it.sectionId == id }
                                .maxOfOrNull { occurrence -> items.indexOfFirst { it.pluginId == occurrence.pluginId } + 1 }
                              ?: 0
      val navigationItemCount = if (selectedItemCount in 1 until items.size) selectedItemCount + 1 else selectedItemCount
      val anchorItemCount = viewportAnchorOccurrence
                              ?.takeIf { it.sectionId == id }
                              ?.let { occurrence -> items.indexOfFirst { it.pluginId == occurrence.pluginId } + 1 }
                            ?: 0
      val requiredItemCount = maxOf(navigationItemCount, anchorItemCount)
      val targetRealizedItemCount = if (section.expanded) {
        roundUpToChunk(maxOf(baseRealizedItemCount, requiredItemCount))
      }
      else {
        baseRealizedItemCount
      }
      replaceRealizedItems(targetRealizedItemCount.coerceAtMost(items.size))
      list.isFocusable = !realRows && items.isNotEmpty()

      val selectedPluginId = selectedOccurrences.lastOrNull { it.sectionId == id }?.pluginId
      if (!realRows) {
        list.selectedIndex = model.indexOfPlugin(selectedPluginId)
      }

      if (header.parent !== headerSlot) {
        headerSlot.preferredSize = header.preferredSize
      }
    }

    private fun hasSameRealizedPrefix(updatedItems: List<PluginItemState>): Boolean {
      if (realizedItemCount > updatedItems.size) return false
      for (index in 0 until realizedItemCount) {
        if (items[index].pluginId != updatedItems[index].pluginId) return false
      }
      return true
    }

    fun realizeVisibleItems(relativeTo: JComponent, visibleRect: Rectangle): Boolean {
      if (realizedItemCount >= items.size || visibleRect.isEmpty) return false

      val itemsComponent = if (realRows) rowsPanel else list
      val rowHeight = estimatedRowHeight()
      val itemsTop = SwingUtilities.convertPoint(itemsComponent, Point(), relativeTo).y
      val itemsBottom = itemsTop.toLong() + estimatedItemsHeight(items.size, rowHeight)
      if (visibleRect.y.toLong() >= itemsBottom || visibleRect.y + visibleRect.height <= itemsTop) return false

      val visibleItemCount = ((visibleRect.y + visibleRect.height - itemsTop).coerceAtLeast(0) + rowHeight - 1) / rowHeight
      val targetItemCount = roundUpToChunk(visibleItemCount)
        .coerceAtMost(items.size)
      if (targetItemCount <= realizedItemCount) return false

      realizeThrough(targetItemCount)
      return true
    }

    private fun realizeNextChunk(): Boolean {
      if (realizedItemCount >= items.size) return false
      realizeThrough((realizedItemCount + REALIZATION_CHUNK_SIZE).coerceAtMost(items.size))
      return true
    }

    private fun realizeThrough(targetItemCount: Int) {
      if (!realRows) {
        for (index in realizedItemCount until targetItemCount) {
          model.addElement(items[index])
        }
      }
      realizedItemCount = targetItemCount
      updateUnrealizedItemsSpacer()
    }

    private fun replaceRealizedItems(itemCount: Int) {
      if (!realRows) {
        model.removeAllElements()
        for (index in 0 until itemCount) {
          model.addElement(items[index])
        }
      }
      realizedItemCount = itemCount
      updateUnrealizedItemsSpacer()
    }

    private fun updateUnrealizedItemsSpacer() {
      val unrealizedHeaderCount = categoryStartIndices.count { it >= realizedItemCount }
      val unrealizedHeight = ((items.size - realizedItemCount).toLong() * estimatedRowHeight() +
                              unrealizedHeaderCount.toLong() * JBUI.scale(SECTION_HEADER_HEIGHT))
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
      unrealizedItemsSpacer.preferredSize = Dimension(0, unrealizedHeight)
    }

    private fun estimatedItemsHeight(itemCount: Int, rowHeight: Int): Long {
      val headerCount = categoryStartIndices.count { it in 0 until itemCount }
      return itemCount.toLong() * rowHeight + headerCount.toLong() * JBUI.scale(SECTION_HEADER_HEIGHT)
    }

    private fun updateCategoryIndexes() {
      if (categoryGroups.isEmpty() || items.isEmpty()) {
        categoryGroupsByPluginId = emptyMap()
        categoryStartIndices = IntArray(0)
        return
      }

      val groupsByCategory = categoryGroups.associateBy(BundledPluginCategoryGroupState::category)
      val groupIndices = categoryGroups.withIndex().associate { (index, group) -> group.category to index }
      val groupsByPluginId = HashMap<PluginId, BundledPluginCategoryGroupState>(items.size)
      val startIndices = IntArray(categoryGroups.size) { -1 }
      items.forEachIndexed { itemIndex, item ->
        val category = bundledPluginCategory(item.searchCategory)
        val group = groupsByCategory[category] ?: return@forEachIndexed
        groupsByPluginId[item.pluginId] = group
        val groupIndex = groupIndices.getValue(category)
        if (startIndices[groupIndex] < 0) startIndices[groupIndex] = itemIndex
      }
      categoryGroupsByPluginId = groupsByPluginId
      categoryStartIndices = startIndices.filter { it >= 0 }.toIntArray()
    }

    fun doLayout() {
      component.doLayout()
      headerSlot.doLayout()
      header.doLayout()
      updateTitlePresentation()
      titleAndStatus.doLayout()
      rowsPanel.doLayout()
    }

    private fun updateTitlePresentation() {
      if (titleAndStatus.width <= 0) {
        titleLabel.text = fullTitle
        titleLabel.toolTipText = null
        return
      }

      val maxTitleWidth = (titleAndStatus.width - statusLabel.preferredSize.width - JBUI.scale(TITLE_STATUS_GAP)).coerceAtLeast(0)
      val displayedTitle = shortenTextWithEllipsis(
        text = fullTitle,
        minTextSuffixLength = 0,
        maxTextPrefixRatio = 1.0f,
        maxTextWidth = maxTitleWidth,
        getTextWidth = titleLabel.getFontMetrics(titleLabel.font)::stringWidth,
      )
      titleLabel.text = displayedTitle
      titleLabel.setToolTipText(fullTitle.takeIf { displayedTitle != fullTitle }?.let(HtmlChunk::text))
    }

    fun realizedItems(): List<PluginItemState> = items.subList(0, realizedItemCount)

    fun setDividerVisible(visible: Boolean) {
      val desiredBorder = dividerBorder.takeIf { visible }
      if (component.border !== desiredBorder) {
        component.border = desiredBorder
      }
    }

    fun setRealRows(bindings: List<PluginRowBinding<PluginRow>>) {
      if (!realRows) return
      val desiredOccurrences = bindings.mapTo(HashSet(), PluginRowBinding<PluginRow>::occurrenceId)
      realRowComponents.keys.filter { it !in desiredOccurrences }.forEach { occurrenceId ->
        realRowComponents.remove(occurrenceId)
      }
      val displayedCategories = HashSet<String>()
      val desiredComponents = ArrayList<JComponent>(bindings.size + categoryGroups.size)
      bindings.forEach { binding ->
        val group = categoryGroupsByPluginId[binding.occurrenceId.pluginId]
        if (group != null && displayedCategories.add(group.category)) {
          val headerView = categoryHeaderViews.getOrPut(group.category) {
            CategoryHeaderView(onBundledCategoryAction)
          }
          headerView.render(group)
          desiredComponents.add(headerView.component)
          categoryPromotionPanel(group.category)?.let(desiredComponents::add)
        }
        val rowComponent = binding.row.component
        realRowComponents[binding.occurrenceId] = rowComponent
        desiredComponents.add(rowComponent)
      }
      categoryHeaderViews.keys.filter { it !in displayedCategories }.forEach { category ->
        categoryHeaderViews.remove(category)
      }
      reconcileRowComponents(desiredComponents)
      updateUnrealizedItemsSpacer()
    }

    private fun reconcileRowComponents(desiredComponents: List<JComponent>) {
      val currentComponents = rowsPanel.components
      val currentIsDesiredPrefix = currentComponents.size <= desiredComponents.size && currentComponents.indices.all { index ->
        currentComponents[index] === desiredComponents[index]
      }
      if (currentIsDesiredPrefix) {
        for (index in currentComponents.size until desiredComponents.size) {
          rowsPanel.add(desiredComponents[index])
        }
        return
      }

      rowsPanel.removeAll()
      desiredComponents.forEach(rowsPanel::add)
    }

    private fun categoryPromotionPanel(category: String): JComponent? {
      categoryPromotionPanels[category]?.let { return it }
      if (category in categoriesWithoutPromotion) return null
      val panel = createBundledCategoryPromotion(category)
      if (panel == null) {
        categoriesWithoutPromotion.add(category)
      }
      else {
        categoryPromotionPanels[category] = panel
      }
      return panel
    }

    fun headerBounds(relativeTo: JComponent): Rectangle {
      return SwingUtilities.convertRectangle(headerSlot, Rectangle(0, 0, headerSlot.width, headerSlot.height), relativeTo)
    }

    fun hasItemsBehindHeader(relativeTo: JComponent, viewTop: Int, headerHeight: Int): Boolean {
      if (realizedItemCount == 0) return false
      val itemsComponent = if (realRows) rowsPanel else list
      val itemsBounds = SwingUtilities.convertRectangle(
        itemsComponent,
        Rectangle(0, 0, itemsComponent.width, itemsComponent.height),
        relativeTo,
      )
      val headerBottom = viewTop + headerHeight
      return itemsBounds.y < headerBottom && itemsBounds.y + itemsBounds.height > viewTop
    }

    val headerPreferredHeight: Int
      get() = header.preferredSize.height

    fun moveHeaderTo(host: JPanel) {
      if (header.parent === host) return

      val focusOwner = headerFocusOwner()
      headerSlot.preferredSize = header.preferredSize
      headerSlot.remove(header)
      host.removeAll()
      host.add(header, BorderLayout.NORTH)
      headerSlot.revalidate()
      host.revalidate()
      restoreHeaderFocus(focusOwner)
    }

    fun moveHeaderBack(host: JPanel) {
      if (header.parent === headerSlot) return

      val focusOwner = headerFocusOwner()
      host.remove(header)
      headerSlot.add(header)
      headerSlot.preferredSize = null
      host.revalidate()
      headerSlot.revalidate()
      restoreHeaderFocus(focusOwner)
    }

    private fun headerFocusOwner(): Component? {
      return KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        ?.takeIf { focusOwner -> SwingUtilities.isDescendingFrom(focusOwner, header) }
    }

    private fun restoreHeaderFocus(focusOwner: Component?) {
      if (focusOwner != null) {
        IdeFocusManager.getGlobalInstance().requestFocus(focusOwner, false)
      }
    }

    private fun roundUpToChunk(itemCount: Int): Int {
      return ((itemCount + REALIZATION_CHUNK_SIZE - 1) / REALIZATION_CHUNK_SIZE) * REALIZATION_CHUNK_SIZE
    }

    fun firstVisibleOccurrence(relativeTo: JComponent, visibleRect: Rectangle): OccurrenceBounds? {
      if (realRows) {
        return realRowComponents.entries.firstNotNullOfOrNull { (occurrenceId, component) ->
          val bounds = SwingUtilities.convertRectangle(component, Rectangle(0, 0, component.width, component.height), relativeTo)
          bounds.takeIf { it.intersects(visibleRect) }?.let { OccurrenceBounds(occurrenceId, it) }
        }
      }
      if (model.isEmpty) return null

      val listBounds = SwingUtilities.convertRectangle(list, Rectangle(0, 0, list.width, list.height), relativeTo)
      if (!listBounds.intersects(visibleRect)) return null

      val firstVisiblePoint = SwingUtilities.convertPoint(
        relativeTo,
        Point(visibleRect.x, visibleRect.y.coerceAtLeast(listBounds.y)),
        list,
      )
      val index = list.locationToIndex(firstVisiblePoint)
      if (index < 0) return null

      val bounds = list.getCellBounds(index, index) ?: return null
      val relativeBounds = SwingUtilities.convertRectangle(list, bounds, relativeTo)
      if (!relativeBounds.intersects(visibleRect)) return null
      return OccurrenceBounds(PluginOccurrenceId(id, model.get(index).pluginId), relativeBounds)
    }

    fun occurrenceBounds(occurrenceId: PluginOccurrenceId, relativeTo: JComponent): Rectangle? {
      if (occurrenceId.sectionId != id) return null
      if (realRows) {
        val component = realRowComponents[occurrenceId] ?: return null
        return SwingUtilities.convertRectangle(component, Rectangle(0, 0, component.width, component.height), relativeTo)
      }
      val index = model.indexOfPlugin(occurrenceId.pluginId)
      if (index < 0) return null
      val bounds = list.getCellBounds(index, index) ?: return null
      return SwingUtilities.convertRectangle(list, bounds, relativeTo)
    }

    private fun DefaultListModel<PluginItemState>.indexOfPlugin(pluginId: PluginId?): Int {
      if (pluginId == null) return -1
      for (index in 0 until size) {
        if (get(index).pluginId == pluginId) return index
      }
      return -1
    }

    private fun estimatedRowHeight(): Int {
      if (!realRows) return list.fixedCellHeight
      return realRowComponents.values.firstOrNull()?.preferredSize?.height?.takeIf { it > 0 }
             ?: JBUI.scale(REAL_ROW_ESTIMATED_HEIGHT)
    }
  }

  private class CategoryHeaderView(
    private val onAction: (BundledPluginCategoryGroupState) -> Unit,
  ) {
    private var state: BundledPluginCategoryGroupState? = null
    private val titleLabel = JBLabel().apply {
      font = font.deriveFont(Font.PLAIN)
    }
    private val actionLink = ActionLink().apply {
      addActionListener { state?.let(onAction) }
    }
    val component: JComponent = JPanel(BorderLayout()).apply {
      isOpaque = false
      border = JBUI.Borders.empty(
        SECTION_HEADER_TOP_INSET,
        SECTION_HEADER_LEFT_INSET,
        SECTION_HEADER_BOTTOM_INSET,
        SECTION_HEADER_RIGHT_INSET,
      )
      add(titleLabel, BorderLayout.CENTER)
      add(actionLink, BorderLayout.EAST)
      val fixedHeight = JBUI.scale(SECTION_HEADER_HEIGHT)
      preferredSize = Dimension(0, fixedHeight)
      minimumSize = Dimension(0, fixedHeight)
      maximumSize = Dimension(Int.MAX_VALUE, fixedHeight)
    }

    fun render(state: BundledPluginCategoryGroupState) {
      this.state = state
      titleLabel.text = state.category
      component.accessibleContext.accessibleName = state.category
      actionLink.text = IdeBundle.message(
        when (state.action) {
          BundledPluginCategoryAction.EnableAll -> "plugins.configurable.enable.all"
          BundledPluginCategoryAction.DisableAll -> "plugins.configurable.disable.all"
        }
      )
    }
  }

  private class SectionPanel : JPanel(BorderLayout()) {
    override fun getMinimumSize(): Dimension {
      return super.getMinimumSize().apply { height = preferredSize.height }
    }
  }

  private class StickyHeaderHost : JPanel(BorderLayout()) {
    private var headerHeight: Int = 0

    var showDivider: Boolean = false
      set(value) {
        if (field == value) return
        field = value
        repaint()
      }

    var showGradient: Boolean = false
      set(value) {
        if (field == value) return
        field = value
        repaint()
      }

    init {
      isOpaque = false
    }

    fun updateOverflow(headerHeight: Int) {
      if (this.headerHeight == headerHeight) return
      this.headerHeight = headerHeight
      revalidate()
      repaint()
    }

    override fun contains(x: Int, y: Int): Boolean {
      return y < headerHeight && super.contains(x, y)
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      if (headerHeight <= 0) return

      val graphics = g.create() as Graphics2D
      try {
        graphics.color = background
        graphics.fillRect(0, 0, width, headerHeight)
        val gradientHeight = (height - headerHeight).coerceAtLeast(0)
        if (showGradient && gradientHeight > 0) {
          val gradientColor = background
          graphics.paint = GradientPaint(
            0f,
            headerHeight.toFloat(),
            gradientColor,
            0f,
            height.toFloat(),
            ColorUtil.toAlpha(gradientColor, 0),
          )
          graphics.fillRect(0, headerHeight, width, gradientHeight)
        }
      }
      finally {
        graphics.dispose()
      }
    }

    override fun paintChildren(g: Graphics) {
      super.paintChildren(g)
      if (!showDivider) return

      val dividerHeight = JBUI.scale(1)
      val oldColor = g.color
      g.color = JBColor.border()
      g.fillRect(0, headerHeight - dividerHeight, width, dividerHeight)
      g.color = oldColor
    }
  }

  private companion object {
    const val DEFAULT_SPLIT_PROPORTION: Float = 0.45f
    const val ERROR_INSET: Int = 10
    const val ERROR_RETRY_GAP: Int = 8
    const val PLUGIN_LIST_MIN_WIDTH: Int = 280
    const val REALIZATION_CHUNK_SIZE: Int = 100
    const val RESULTS_ANNOUNCEMENT_DELAY_MS: Int = 250
    const val REAL_ROW_ESTIMATED_HEIGHT: Int = 80
    const val SEARCH_COMPONENT_WIDTH: Int = 340
    const val SEARCH_HISTORY_PROPERTY: String = "UnifiedPluginsSearchHistory"
    const val SECTION_GAP: Int = 8
    const val SECTION_HEADER_BOTTOM_INSET: Int = 4
    const val SECTION_HEADER_HEIGHT: Int = 40
    const val SECTION_HEADER_LEFT_INSET: Int = 16
    const val SECTION_HEADER_RIGHT_INSET: Int = 12
    const val SECTION_HEADER_TOP_INSET: Int = 8
    const val STATIC_ROW_HEIGHT: Int = 36
    const val STICKY_HEADER_GRADIENT_HEIGHT: Int = 8
    const val TITLE_STATUS_GAP: Int = 6

  }

  private fun SectionView?.orEmptyRealizedItems(): List<PluginItemState> = this?.realizedItems().orEmpty()

  private data class OccurrenceBounds(
    val id: PluginOccurrenceId,
    val bounds: Rectangle,
  )

  private data class ViewportAnchor(
    val id: PluginOccurrenceId,
    val offsetFromViewport: Int,
  )

  private data class ResultsAnnouncementSignature(
    val queryRevision: Long,
    val sections: List<SectionAnnouncementSignature>,
  )

  private data class SectionAnnouncementSignature(
    val id: PluginSectionId,
    val count: Int?,
    val status: PluginSectionStatus,
  )

  private class ScrollContainerLayout(
    private val scrollPane: JBScrollPane,
  ) : LayoutManager {
    override fun addLayoutComponent(name: String?, component: Component?) = Unit

    override fun removeLayoutComponent(component: Component?) = Unit

    override fun preferredLayoutSize(parent: Container): Dimension = scrollPane.preferredSize

    override fun minimumLayoutSize(parent: Container): Dimension = scrollPane.minimumSize

    override fun layoutContainer(parent: Container) {
      scrollPane.setBounds(0, 0, parent.width, parent.height)
    }
  }
}

internal fun pluginResultsAnnouncement(state: UnifiedPluginsPageState): @Nls String {
  if (state.sections.any { it.status is PluginSectionStatus.Loading }) {
    return IdeBundle.message("plugins.configurable.results.loading")
  }
  val error = state.sections.firstNotNullOfOrNull { section ->
    when (val status = section.status) {
      is PluginSectionStatus.Degraded -> status.error.message
      is PluginSectionStatus.Failed -> status.error.message
      is PluginSectionStatus.Loading, PluginSectionStatus.Ready -> null
    }
  }
  if (error != null) return error

  val resultCount = state.sections.sumOf { it.items.size }
  return IdeBundle.message("plugins.configurable.results.updated", resultCount)
}
