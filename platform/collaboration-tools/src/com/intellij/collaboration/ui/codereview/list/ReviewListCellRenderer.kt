// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

import com.intellij.collaboration.messages.CollaborationToolsBundle.message
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.containers.nullize
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.*
import icons.CollaborationToolsIcons
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class ReviewListCellRenderer<T>(private val presenter: (T) -> ReviewListItemPresentation)
  : ListCellRenderer<T>, SelectablePanel(null) {

  private val toolTipManager
    get() = IdeTooltipManager.getInstance()

  private val title = JLabel().apply {
    minimumSize = JBDimension(30, 0)
  }
  private val info = JLabel().apply {
    val titleFontSize = title.font.size
    font = font.deriveFont(titleFontSize / 13.0f * 12.0f)
  }
  private val tags = JLabel()
  private val state = JLabel().apply {
    border = JBUI.Borders.empty(0, 4)
    foreground = stateForeground
  }
  private val statePanel = StatePanel(state).apply {
    background = stateBackground
  }
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
      add(title, SwingConstants.LEFT as Any)
      add(tags, SwingConstants.LEFT as Any)

      add(statePanel, SwingConstants.RIGHT as Any)
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
    updateRendering()
  }

  private fun updateRendering() {
    if (isNewUI) {
      border = JBUI.Borders.empty(4, 19, 5, 19)
      selectionArc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
      selectionArcCorners = SelectionArcCorners.ALL
      selectionInsets = JBInsets(0, 13, 0, 13)
    }
    else {
      border = JBUI.Borders.empty(4, 13, 5, 13)
      selectionArc = 0
      selectionArcCorners = SelectionArcCorners.ALL
      selectionInsets = JBInsets(0)
    }
  }

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    isNewUI = ExperimentalUI.isNewUI()
    background = list.background
    selectionColor = ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus())
    val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(isSelected && !ExperimentalUI.isNewUI(), list.hasFocus())

    val presentation = presenter(value)

    title.apply {
      text = presentation.title
      foreground = primaryTextColor
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
      icon = CollaborationToolsIcons.Review.Branch
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
              label.icon = IconUtil.colorize(CollaborationToolsIcons.Review.Branch, color)
            }
            else {
              label.icon = CollaborationToolsIcons.Review.Branch
            }
          }
        }
        toolTipManager.setCustomTooltip(it, tooltip)
      }
      else {
        toolTipManager.setCustomTooltip(it, null)
      }
    }

    state.apply {
      font = JBUI.Fonts.smallFont()
      text = presentation.state
      isVisible = presentation.state != null
    }
    statePanel.isVisible = presentation.state != null

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
      icon = CollaborationToolsIcons.Review.Comment
      text = counter?.count.toString()
      toolTipText = counter?.tooltip
      isVisible = counter != null
    }

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
    val icons = users?.items?.map { it.avatarIcon }?.nullize()
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

  companion object {

    // TODO: register metadata provider somehow?
    private val stateForeground = JBColor.namedColor("ReviewList.state.foreground", 0x797979)
    private val stateBackground = JBColor.namedColor("ReviewList.state.background", 0xDFE1E5)

    /**
     * Paints [icons] in a stack - one over the other with a slight offset
     * Assumes that icons all have the same size
     */
    private class OverlaidOffsetIconsIcon(
      private val icons: List<Icon>,
      private val offsetRate: Float = 0.4f
    ) : Icon {

      override fun getIconHeight(): Int = icons.maxOfOrNull { it.iconHeight } ?: 0

      override fun getIconWidth(): Int {
        if (icons.isEmpty()) return 0
        val iconWidth = icons.first().iconWidth
        val width = iconWidth + (icons.size - 1) * iconWidth * offsetRate
        return max(width.roundToInt(), 0)
      }

      override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val bufferImage = ImageUtil.createImage(g, iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
        val bufferGraphics = bufferImage.createGraphics()
        try {
          paintToBuffer(bufferGraphics)
        }
        finally {
          bufferGraphics.dispose()
        }
        StartupUiUtil.drawImage(g, bufferImage, x, y, null)
      }

      private fun paintToBuffer(g: Graphics2D) {
        var rightEdge = iconWidth
        icons.reversed().forEachIndexed { index, icon ->
          val currentX = rightEdge - icon.iconWidth

          // cut out the part of the painted icon slightly bigger then the next one to create a visual gap
          if (index > 0) {
            val g2 = g.create() as Graphics2D
            try {
              val scaleX = 1.1
              val scaleY = 1.1
              // paint a bit higher, so that the cutout is centered
              g2.translate(0, -((iconHeight * scaleY - iconHeight) / 2).roundToInt())
              g2.scale(scaleX, scaleY)
              g2.composite = AlphaComposite.DstOut
              icon.paintIcon(null, g2, currentX, 0)
            }
            finally {
              g2.dispose()
            }
          }

          icon.paintIcon(null, g, currentX, 0)
          rightEdge -= (icon.iconWidth * offsetRate).roundToInt()
        }
      }
    }

    /**
     * Draws a background with rounded corners
     */
    private class StatePanel(stateLabel: JLabel) : JPanel(BorderLayout()) {
      init {
        add(stateLabel, BorderLayout.CENTER)
        isOpaque = false
      }

      override fun paintComponent(g: Graphics) {
        g as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)

        val insets = insets
        val bounds = bounds
        JBInsets.removeFrom(bounds, insets)
        val arc = JBUIScale.scale(6)
        val rect = RoundRectangle2D.Float(0f, 0f,
                                          bounds.width.toFloat(), bounds.height.toFloat(),
                                          arc.toFloat(), arc.toFloat())
        g.color = background
        g.fill(rect)
        super.paintComponent(g)
      }
    }

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

    private class LazyIdeToolTip(component: JComponent,
                                 private val tipFactory: () -> JComponent)
      : IdeTooltip(component, Point(0, 0), null, component) {

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
