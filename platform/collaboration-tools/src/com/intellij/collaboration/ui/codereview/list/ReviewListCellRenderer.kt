// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

import com.intellij.collaboration.messages.CollaborationToolsBundle.message
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.createTagLabel
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.avatar.CodeReviewAvatarUtils
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.OverlaidOffsetIconsIcon
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.FontUtil
import com.intellij.util.IconUtil
import com.intellij.util.containers.nullize
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.*
import icons.CollaborationToolsIcons
import icons.DvcsImplIcons
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

@ApiStatus.Internal
data class ReviewListCellUiOptions(
  val bordered: Boolean = true,
)

@ApiStatus.Internal
internal class ReviewListCellRenderer<T>(
  private val presenter: (T) -> ReviewListItemPresentation,
  private val options: ReviewListCellUiOptions = ReviewListCellUiOptions(),
) : ListCellRenderer<T>, SelectablePanel(null) {

  private val toolTipManager
    get() = IdeTooltipManager.getInstance()

  private val titleSpacer = JLabel().apply {
    preferredSize = JBDimension(1, CodeReviewAvatarUtils.expectedIconHeight(Avatar.Sizes.OUTLINED))
  }
  private val unseen = JLabel().apply {
    icon = UnreadDotIcon()
    border = JBEmptyBorder(0, 2, 0, 0)
  }
  private val title = JLabel().apply {
    minimumSize = JBDimension(30, 0)
  }
  private val info = JLabel().apply {
    font = JBFont.create(font, false).let(FontUtil::minusOne)
  }
  private val tags = JLabel()
  private val stateTextModel = SingleValueModel<@Nls String?>(null)
  private val stateLabel = createTagLabel(stateTextModel)
  private val nonMergeable = JLabel()
  private val buildStatus = JLabel()
  private val userGroup1 = JLabel()
  private val userGroup2 = JLabel()
  private val comments = JLabel()

  private var isNewUI by Delegates.observable(false) { _, old, new ->
    if (old != new) updateRendering()
  }

  init {

    val firstLinePanel = JPanel(HorizontalSidesLayout(6)).apply {
      isOpaque = false
      add(unseen, SwingConstants.LEFT as Any)
      add(title, SwingConstants.LEFT as Any)
      add(tags, SwingConstants.LEFT as Any)

      add(titleSpacer, SwingConstants.RIGHT as Any)
      add(stateLabel, SwingConstants.RIGHT as Any)
      add(nonMergeable, SwingConstants.RIGHT as Any)
      add(buildStatus, SwingConstants.RIGHT as Any)
      add(userGroup1, SwingConstants.RIGHT as Any)
      add(userGroup2, SwingConstants.RIGHT as Any)
      add(comments, SwingConstants.RIGHT as Any)
    }

    layout = BorderLayout()
    add(firstLinePanel, BorderLayout.CENTER)
    add(info, BorderLayout.SOUTH)

    UIUtil.forEachComponentInHierarchy(this) {
      it.isFocusable = false
    }
  }

  private fun updateRendering() {
    val hSelectionInsets = if (!options.bordered) 0 else 13
    val hBorder = when {
      !options.bordered -> 6
      isNewUI -> 19
      else -> 13
    }

    if (isNewUI) {
      border = JBUI.Borders.empty(4, hBorder, 5, hBorder)
      selectionArc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
      selectionArcCorners = SelectionArcCorners.ALL
      selectionInsets = JBInsets(0, hSelectionInsets, 0, hSelectionInsets)
    }
    else {
      border = JBUI.Borders.empty(4, hBorder, 5, hBorder)
      selectionArc = 0
      selectionArcCorners = SelectionArcCorners.ALL
      selectionInsets = JBInsets(0)
    }
  }

  override fun getListCellRendererComponent(
    list: JList<out T>,
    value: T?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    isNewUI = ExperimentalUI.isNewUI()
    background = list.background
    selectionColor = ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus())
    val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(isSelected && !ExperimentalUI.isNewUI(), list.hasFocus())

    val presentation = value?.let { presenter(it) } ?: return this

    unseen.apply {
      isVisible = presentation.seen?.not() ?: false
    }
    title.apply {
      text = presentation.title
      foreground = primaryTextColor
      addTruncationListener()
    }

    info.apply {
      val author = presentation.author
      if (author != null) {
        text = message("review.list.info.author",
                       presentation.id,
                       DateFormatUtil.formatPrettyDate(presentation.createdDate),
                       author.getPresentableName())
      }
      else {
        text = message("review.list.info",
                       presentation.id,
                       DateFormatUtil.formatPrettyDate(presentation.createdDate))
      }
      foreground = secondaryTextColor
    }

    val tagGroup = presentation.tagGroup
    tags.apply {
      icon = DvcsImplIcons.BranchLabel
      isVisible = tagGroup != null
    }.also {
      if (tagGroup != null) {
        val tooltip = LazyIdeToolTip(it) {
          createTitledList(tagGroup) { label, tag, _ ->
            label.text = tag.name
            label.foreground = UIUtil.getToolTipForeground()
            val color = tag.color
            if (color != null) {
              //TODO: need a separate untinted icon to color properly
              label.icon = IconUtil.colorize(DvcsImplIcons.BranchLabel, color)
            }
            else {
              label.icon = DvcsImplIcons.BranchLabel
            }
          }
        }
        toolTipManager.setCustomTooltip(it, tooltip)
      }
      else {
        toolTipManager.setCustomTooltip(it, null)
      }
    }

    stateTextModel.value = presentation.state
    stateLabel.isVisible = presentation.state != null

    nonMergeable.apply {
      val status = presentation.mergeableStatus
      icon = status?.icon
      toolTipText = status?.tooltip
      isVisible = status != null
    }

    buildStatus.apply {
      val status = presentation.buildStatus
      icon = status?.icon
      toolTipText = status?.tooltip
      isVisible = status != null
    }

    showUsersIcon(userGroup1, presentation.userGroup1)

    showUsersIcon(userGroup2, presentation.userGroup2)

    comments.apply {
      val counter = presentation.commentsCounter
      icon = CollaborationToolsIcons.Comment
      text = counter?.count.toString()
      toolTipText = counter?.tooltip
      isVisible = counter != null
      border = JBUI.Borders.emptyRight(1)
    }

    updateRendering()

    return this
  }

  private fun <T> createTitledList(collection: NamedCollection<T>, customizer: SimpleListCellRenderer.Customizer<T>): JComponent {
    val title = JLabel().apply {
      font = JBUI.Fonts.smallFont()
      foreground = UIUtil.getContextHelpForeground()
      text = collection.namePlural
      border = JBUI.Borders.empty(0, 10, 4, 0)
    }

    val list = JBList(collection.items).apply {
      isOpaque = false
      cellRenderer = SimpleListCellRenderer.create(customizer)
    }
    return JPanel(BorderLayout()).apply {
      isOpaque = false
      add(title, BorderLayout.NORTH)
      add(list, BorderLayout.CENTER)
    }
  }

  private fun showUsersIcon(label: JLabel, users: NamedCollection<UserPresentation>?) {
    val icons = users?.items?.map { it.avatarIcon }?.take(MAX_PARTICIPANT_ICONS)?.nullize()
    if (icons == null) {
      label.isVisible = false
      label.icon = null
    }
    else {
      label.isVisible = true
      label.icon = OverlaidOffsetIconsIcon(icons)
    }

    if (users != null) {
      val tooltip = LazyIdeToolTip(label) {
        createTitledList(users) { label, user, _ ->
          label.text = user.getPresentableName()
          label.icon = user.avatarIcon
          label.foreground = UIUtil.getToolTipForeground()
        }
      }
      toolTipManager.setCustomTooltip(label, tooltip)
    }
  }

  private fun isLabelTruncated(label: JLabel): Boolean {
    val fm = label.getFontMetrics(label.font)
    val text = label.text
    return fm.stringWidth(text) > label.width
  }

  private fun JLabel.addTruncationListener() {
    val label = this
    this.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        if (isLabelTruncated(label)) {
          label.toolTipText = label.text
        }
        else {
          label.toolTipText = null
        }
      }
    })
  }

  companion object {
    private const val MAX_PARTICIPANT_ICONS = 2

    /**
     * Lays out the components horizontally in two groups - [SwingConstants.LEFT] and [SwingConstants.RIGHT] anchored to the left and right sides respectively.
     * Respects the minimal sizes and does not force the components to grow.
     */
    private class HorizontalSidesLayout(gap: Int) : AbstractLayoutManager() {

      private val gap = JBValue.UIInteger("", max(0, gap))

      private val leftComponents = mutableListOf<Component>()
      private val rightComponents = mutableListOf<Component>()

      override fun addLayoutComponent(comp: Component, constraints: Any?) {
        when (constraints) {
          SwingConstants.RIGHT -> rightComponents.add(comp)
          else -> leftComponents.add(comp)
        }
      }

      override fun addLayoutComponent(name: String?, comp: Component) {
        addLayoutComponent(comp, SwingConstants.LEFT)
      }

      override fun removeLayoutComponent(comp: Component) {
        leftComponents.remove(comp)
        rightComponents.remove(comp)
      }

      override fun minimumLayoutSize(parent: Container): Dimension =
        getSize(leftComponents + rightComponents, Component::getMinimumSize)

      override fun preferredLayoutSize(parent: Container): Dimension =
        getSize(leftComponents + rightComponents, Component::getPreferredSize)

      private fun getSize(components: List<Component>, dimensionGetter: (Component) -> Dimension): Dimension {
        val visibleComponents = components.asSequence().filter(Component::isVisible)
        val dimension = visibleComponents.fold(Dimension()) { acc, component ->
          val size = dimensionGetter(component)
          acc.width += size.width
          acc.height = max(acc.height, size.height)

          acc
        }
        dimension.width += gap.get() * max(0, visibleComponents.count() - 1)
        return dimension
      }

      override fun layoutContainer(parent: Container) {
        val bounds = Rectangle(Point(0, 0), parent.size)
        JBInsets.removeFrom(bounds, parent.insets)
        val height = bounds.height

        val widthDeltaFraction = getWidthDeltaFraction(minimumLayoutSize(parent).width,
                                                       preferredLayoutSize(parent).width,
                                                       bounds.width)

        val leftMinWidth = getSize(leftComponents, Component::getMinimumSize).width
        val leftPrefWidth = getSize(leftComponents, Component::getPreferredSize).width
        val leftWidth = leftMinWidth + ((leftPrefWidth - leftMinWidth) * widthDeltaFraction).toInt()

        val leftWidthDeltaFraction = getWidthDeltaFraction(leftMinWidth, leftPrefWidth, leftWidth)
        layoutGroup(leftComponents, bounds.location, height, leftWidthDeltaFraction)

        val rightMinWidth = getSize(rightComponents, Component::getMinimumSize).width
        val rightPrefWidth = getSize(rightComponents, Component::getPreferredSize).width
        val rightWidth = min(bounds.width - leftWidth - gap.get(), rightPrefWidth)

        val rightX = bounds.x + max(leftWidth + gap.get(), bounds.width - rightWidth)
        val rightWidthDeltaFraction = getWidthDeltaFraction(rightMinWidth, rightPrefWidth, rightWidth)
        layoutGroup(rightComponents, Point(rightX, bounds.y), height, rightWidthDeltaFraction)
      }

      private fun getWidthDeltaFraction(minWidth: Int, prefWidth: Int, currentWidth: Int): Float {
        if (prefWidth <= minWidth) {
          return 0f
        }

        return ((currentWidth - minWidth) / (prefWidth - minWidth).toFloat())
          .coerceAtLeast(0f)
          .coerceAtMost(1f)
      }

      private fun layoutGroup(components: List<Component>, startPoint: Point, height: Int, groupWidthDeltaFraction: Float) {
        var x = startPoint.x
        components.asSequence().filter(Component::isVisible).forEach {
          val minSize = it.minimumSize
          val prefSize = it.preferredSize
          val width = minSize.width + ((prefSize.width - minSize.width) * groupWidthDeltaFraction).toInt()
          val size = Dimension(width, min(prefSize.height, height))

          val y = startPoint.y + (height - size.height) / 2
          val location = Point(x, y)

          it.bounds = Rectangle(location, size)
          x += size.width + gap.get()
        }
      }
    }

    private class LazyIdeToolTip(
      component: JComponent,
      private val tipFactory: () -> JComponent,
    ) : IdeTooltip(component, Point(0, 0), null, component) {

      init {
        isToCenter = true
        layer = Balloon.Layer.top
        preferredPosition = Balloon.Position.atRight
      }

      override fun beforeShow(): Boolean {
        tipComponent = tipFactory()
        return true
      }
    }
  }
}

@ApiStatus.Internal
data object ReviewListCellRendererFactory {
  fun <T> getCellRenderer(presenter: (T) -> ReviewListItemPresentation): ListCellRenderer<T> {
    return ReviewListCellRenderer(presenter)
  }
}