// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.LookupElementPresentation.DecoratedTextRange
import com.intellij.codeInsight.lookup.LookupElementPresentation.LookupItemDecoration
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.Companion.MATCHED_FOREGROUND_COLOR
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.Companion.bodyInsets
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.Companion.getGrayedForeground
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.IconDecorator
import com.intellij.openapi.application.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributesEffectsBuilder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.*
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.SimpleTextAttributes.StyleAttributeConstant
import com.intellij.ui.components.JBList
import com.intellij.ui.icons.RowIcon
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.IconUtil.cropIcon
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.FList
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.awt.*
import java.util.function.Supplier
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.concurrent.Volatile
import kotlin.math.max

class LookupCellRenderer(lookup: LookupImpl, editorComponent: JComponent) : ListCellRenderer<LookupElement> {
  private var emptyIcon: Icon = EmptyIcon.ICON_0
  private val normalFont: Font
  private val boldFont: Font
  private val normalMetrics: FontMetrics
  private val boldMetrics: FontMetrics

  private val lookup: LookupImpl

  private val nameComponent: SimpleColoredComponent
  private val tailComponent: SimpleColoredComponent
  private val typeLabel: SimpleColoredComponent
  private val panel: LookupPanel
  private val indexToIsSelected = Int2BooleanOpenHashMap()

  private var maxWidth = -1

  @get:VisibleForTesting
  @Volatile
  var lookupTextWidth: Int = 50
    private set
  private val widthLock = ObjectUtils.sentinel("lookup width lock")
  private val lookupWidthUpdater: () -> Unit
  private val shrinkLookup: Boolean

  private val asyncRendering: AsyncRendering

  private val customizers: MutableList<ItemPresentationCustomizer> = ContainerUtil.createLockFreeCopyOnWriteList()

  private var isSelected = false

  init {
    val scheme = lookup.topLevelEditor.colorsScheme
    normalFont = scheme.getFont(EditorFontType.PLAIN)
    boldFont = scheme.getFont(EditorFontType.BOLD)

    this.lookup = lookup
    nameComponent = MySimpleColoredComponent()
    nameComponent.setOpaque(false)
    nameComponent.setIconTextGap(scale(4))
    nameComponent.setIpad(JBUI.insetsLeft(1))
    nameComponent.setMyBorder(null)

    tailComponent = MySimpleColoredComponent()
    tailComponent.setOpaque(false)
    tailComponent.setIpad(JBInsets.emptyInsets())
    tailComponent.setBorder(JBUI.Borders.emptyRight(10))

    typeLabel = MySimpleColoredComponent()
    typeLabel.setOpaque(false)
    typeLabel.setIpad(JBInsets.emptyInsets())
    typeLabel.setBorder(JBUI.Borders.emptyRight(10))

    panel = LookupPanel()
    panel.add(nameComponent, BorderLayout.WEST)
    panel.add(tailComponent, BorderLayout.CENTER)
    panel.add(typeLabel, BorderLayout.EAST)

    normalMetrics = lookup.topLevelEditor.component.getFontMetrics(normalFont)
    boldMetrics = lookup.topLevelEditor.component.getFontMetrics(boldFont)
    asyncRendering = AsyncRendering(lookup)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      // avoid delay in unit tests
      lookupWidthUpdater = {
        ApplicationManager.getApplication().invokeLater({ updateLookupWidthFromVisibleItems() }, lookup.project.disposed)
      }
    }
    else {
      val lookupWidthUpdateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      val coroutineContext = Dispatchers.EDT + ModalityState.stateForComponent(editorComponent).asContextElement()
      lookup.coroutineScope.launch {
        lookupWidthUpdateRequests
          .throttle(50)
          .collect {
            withContext(coroutineContext) {
              writeIntentReadAction {
                updateLookupWidthFromVisibleItems()
              }
            }
          }
      }
      lookupWidthUpdater = {
        check(lookupWidthUpdateRequests.tryEmit(Unit))
      }
    }

    shrinkLookup = Registry.`is`("ide.lookup.shrink")
  }

  companion object {
    private val CUSTOM_NAME_FONT = Key.create<Font>("CustomLookupElementNameFont")
    private val CUSTOM_TAIL_FONT = Key.create<Font>("CustomLookupElementTailFont")
    private val CUSTOM_TYPE_FONT = Key.create<Font>("CustomLookupElementTypeFont")

    @JvmField
    val BACKGROUND_COLOR: Color = JBColor.lazy(
      Supplier {
        EditorColorsUtil.getGlobalOrDefaultColor(Lookup.LOOKUP_COLOR)
        ?: JBColor.namedColor("CompletionPopup.background", JBColor(Color(235, 244, 254), JBColor.background()))
      })

    @JvmField
    val MATCHED_FOREGROUND_COLOR: Color = JBColor.namedColor("CompletionPopup.matchForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED)
    @JvmField
    val SELECTED_BACKGROUND_COLOR: Color = JBColor.namedColor("CompletionPopup.selectionBackground", JBColor(0xc5dffc, 0x113a5c))
    @JvmField
    val SELECTED_NON_FOCUSED_BACKGROUND_COLOR: Color = JBColor.namedColor("CompletionPopup.selectionInactiveBackground",
                                                                          JBColor(0xE0E0E0, 0x515457))
    private val NON_FOCUSED_MASK_COLOR: Color = JBColor.namedColor("CompletionPopup.nonFocusedMask", Gray._0.withAlpha(0))

    @JvmStatic
    fun bodyInsets(): Insets {
      return JBUI.insets("CompletionPopup.Body.insets", JBUI.insets(4))
    }

    @JvmStatic
    fun bodyInsetsWithAdvertiser(): Insets {
      return JBUI.insets("CompletionPopup.BodyWithAdvertiser.insets", JBUI.insets(4, 4, 3, 4))
    }

    @JvmField
    val REGULAR_MATCHED_ATTRIBUTES: SimpleTextAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, MATCHED_FOREGROUND_COLOR)

    @JvmStatic
    fun getGrayedForeground(@Suppress("UNUSED_PARAMETER") isSelected: Boolean): Color = UIUtil.getContextHelpForeground()

    @JvmStatic
    fun getMatchingFragments(prefix: String, name: String): FList<TextRange>? {
      return NameUtil.buildMatcher("*$prefix").build().matchingFragments(name)
    }

    @JvmStatic
    fun augmentIcon(editor: Editor?, icon: Icon?, standard: Icon): Icon {
      @Suppress("NAME_SHADOWING")
      var icon = icon

      @Suppress("NAME_SHADOWING")
      var standard = standard
      if (Registry.`is`("editor.scale.completion.icons")) {
        standard = EditorUtil.scaleIconAccordingEditorFont(standard, editor)
        icon = EditorUtil.scaleIconAccordingEditorFont(icon, editor)
      }
      if (icon == null) {
        return standard
      }

      icon = removeVisibilityIfNeeded(editor, icon, standard)

      if (icon.iconHeight < standard.iconHeight || icon.iconWidth < standard.iconWidth) {
        val layeredIcon = LayeredIcon(2)
        layeredIcon.setIcon(icon, 0, 0, (standard.iconHeight - icon.iconHeight) / 2)
        layeredIcon.setIcon(standard, 1)
        return layeredIcon
      }

      return icon
    }
  }

  override fun getListCellRendererComponent(
    list: JList<out LookupElement>,
    item: LookupElement,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    @Suppress("NAME_SHADOWING")
    var isSelected = isSelected
    val nonFocusedSelection = isSelected && lookup.lookupFocusDegree == LookupFocusDegree.SEMI_FOCUSED
    if (!lookup.isFocused) {
      isSelected = false
    }

    this.isSelected = isSelected
    panel.selectionColor = when {
      nonFocusedSelection -> SELECTED_NON_FOCUSED_BACKGROUND_COLOR
      isSelected -> SELECTED_BACKGROUND_COLOR
      else -> null
    }

    var allowedWidth = list.width - calcSpacing(nameComponent, emptyIcon) - calcSpacing(tailComponent, null) - calcSpacing(typeLabel, null)

    var presentation = asyncRendering.getLastComputed(item)
    for (customizer in customizers) {
      presentation = customizer.customizePresentation(item, presentation)
    }
    if (presentation.icon != null) {
      setIconInsets(nameComponent)
    }

    nameComponent.clear()

    val itemColor = presentation.itemTextForeground
    allowedWidth -= setItemTextLabel(item = item, foreground = itemColor, presentation = presentation, allowedWidth = allowedWidth)

    tailComponent.font = getCustomFont(item = item, bold = false, key = CUSTOM_TAIL_FONT) ?: normalFont
    typeLabel.font = getCustomFont(item = item, bold = false, key = CUSTOM_TYPE_FONT) ?: normalFont
    nameComponent.icon = augmentIcon(editor = lookup.topLevelEditor, icon = presentation.icon, standard = emptyIcon)

    val grayedForeground = getGrayedForeground(isSelected)
    typeLabel.clear()
    if (allowedWidth > 0) {
      allowedWidth -= setTypeTextLabel(
        item = item,
        foreground = grayedForeground,
        presentation = presentation,
        allowedWidth = if (isSelected) getGetOrComputeMaxWidth() else allowedWidth,
        selected = isSelected,
        nonFocusedSelection = nonFocusedSelection,
        normalMetrics = getRealFontMetrics(item = item, bold = false, key = CUSTOM_TYPE_FONT),
      )
    }

    tailComponent.clear()
    if (isSelected || allowedWidth >= 0) {
      setTailTextLabel(
        isSelected = isSelected,
        presentation = presentation,
        foreground = grayedForeground,
        allowedWidth = if (isSelected) getGetOrComputeMaxWidth() else allowedWidth,
        nonFocusedSelection = nonFocusedSelection,
        fontMetrics = getRealFontMetrics(item, false, CUSTOM_TAIL_FONT),
      )
    }

    if (this.indexToIsSelected.containsKey(index)) {
      if (!isSelected && this.indexToIsSelected[index]) {
        panel.setUpdateExtender(true)
      }
    }
    this.indexToIsSelected.put(index, isSelected)

    val w = nameComponent.preferredSize.getWidth() + tailComponent.preferredSize.getWidth() + typeLabel.preferredSize.getWidth()

    val useBoxLayout = isSelected && w > list.width && (list as JBList<*>).expandableItemsHandler.isEnabled
    if (useBoxLayout != panel.layout is BoxLayout) {
      panel.removeAll()
      if (useBoxLayout) {
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.add(nameComponent)
        panel.add(tailComponent)
        panel.add(typeLabel)
      }
      else {
        panel.layout = BorderLayout()
        panel.add(nameComponent, BorderLayout.WEST)
        panel.add(tailComponent, BorderLayout.CENTER)
        panel.add(typeLabel, BorderLayout.EAST)
      }
    }

    AccessibleContextUtil.setCombinedName(panel, nameComponent, "", tailComponent, " - ", typeLabel)
    AccessibleContextUtil.setCombinedDescription(panel, nameComponent, "", tailComponent, " - ", typeLabel)
    return panel
  }

  @Suppress("unused")
  internal fun addPresentationCustomizer(customizer: ItemPresentationCustomizer) {
    customizers.add(customizer)
    // need to make sure we've got at least enough space for the customizations alone
    updateIconWidth(EmptyIcon.ICON_0)
  }

  private fun getGetOrComputeMaxWidth(): Int {
    if (maxWidth < 0) {
      val p = lookup.component.locationOnScreen
      val rectangle = ScreenUtil.getScreenRectangle(p)
      maxWidth = rectangle.x + rectangle.width - p.x - 111
    }
    return maxWidth
  }

  private fun setTailTextLabel(
    isSelected: Boolean, presentation: LookupElementPresentation,
    foreground: Color,
    allowedWidth: Int,
    nonFocusedSelection: Boolean,
    fontMetrics: FontMetrics,
  ) {
    @Suppress("NAME_SHADOWING")
    var allowedWidth = allowedWidth
    val style = getStyle(presentation.isStrikeout, false, false)

    for (fragment in presentation.tailFragments) {
      if (allowedWidth < 0) {
        return
      }

      val trimmed = trimLabelText(fragment.text, allowedWidth, fontMetrics)
      @StyleAttributeConstant val fragmentStyle = if (fragment.isItalic) style or SimpleTextAttributes.STYLE_ITALIC else style
      val baseAttributes = SimpleTextAttributes(fragmentStyle, getTailTextColor(isSelected, fragment, foreground, nonFocusedSelection))
      tailComponent.append(trimmed, baseAttributes)
      allowedWidth -= getStringWidth(trimmed, fontMetrics)
    }
    renderItemNameDecoration(tailComponent, presentation.itemTailDecorations)
  }

  private fun trimLabelText(text: String?, maxWidth: Int, metrics: FontMetrics): @NlsSafe String {
    if (text.isNullOrEmpty()) {
      return ""
    }

    val strWidth = getStringWidth(text, metrics)
    if (strWidth <= maxWidth || isSelected) {
      return text
    }

    if (getStringWidth(ELLIPSIS, metrics) > maxWidth) {
      return ""
    }

    val insIndex = ObjectUtils.binarySearch(0, text.length) { mid ->
      val candidate = text.substring(0, mid) + ELLIPSIS
      val width = getStringWidth(candidate, metrics)
      if (width <= maxWidth) -1 else 1
    }
    val i = max(0.0, (-insIndex - 2).toDouble()).toInt()

    return text.substring(0, i) + ELLIPSIS
  }

  private fun setItemTextLabel(item: LookupElement, foreground: Color, presentation: LookupElementPresentation, allowedWidth: Int): Int {
    val bold = presentation.isItemTextBold

    val customItemFont = getCustomFont(item, bold, CUSTOM_NAME_FONT)
    nameComponent.font = customItemFont ?: if (bold) boldFont else normalFont
    val style = getStyle(
      strikeout = presentation.isStrikeout,
      underlined = presentation.isItemTextUnderlined,
      italic = presentation.isItemTextItalic,
    )

    val metrics = getRealFontMetrics(item, bold, CUSTOM_NAME_FONT)
    val name = trimLabelText(presentation.itemText, allowedWidth, metrics)
    val used = getStringWidth(name, metrics)

    renderItemName(
      item = item,
      foreground = foreground,
      style = style,
      name = name,
      nameComponent = nameComponent,
      itemNameDecorations = presentation.itemNameDecorations,
    )
    return used
  }

  private fun getRealFontMetrics(item: LookupElement, bold: Boolean, key: Key<Font>): FontMetrics {
    val customFont = getCustomFont(item, bold, key)
    if (customFont != null) {
      return lookup.topLevelEditor.component.getFontMetrics(customFont)
    }

    return if (bold) boldMetrics else normalMetrics
  }

  private fun renderItemName(
    item: LookupElement,
    foreground: Color,
    @StyleAttributeConstant style: Int,
    name: @Nls String,
    nameComponent: SimpleColoredComponent,
    itemNameDecorations: List<DecoratedTextRange>,
  ) {
    val base = SimpleTextAttributes(style, foreground)

    val prefix = if (item is EmptyLookupItem) "" else lookup.itemPattern(item)
    if (prefix.isNotEmpty()) {
      val ranges = getMatchingFragments(prefix, name)
      if (ranges != null) {
        val highlighted = SimpleTextAttributes(style, MATCHED_FOREGROUND_COLOR)
        SpeedSearchUtil.appendColoredFragments(nameComponent, name, ranges, base, highlighted)
        renderItemNameDecoration(nameComponent, itemNameDecorations)
        return
      }
    }
    nameComponent.append(name, base)
    renderItemNameDecoration(nameComponent, itemNameDecorations)
  }

  private fun setTypeTextLabel(
    item: LookupElement,
    foreground: Color,
    presentation: LookupElementPresentation,
    allowedWidth: Int,
    selected: Boolean, nonFocusedSelection: Boolean, normalMetrics: FontMetrics,
  ): Int {
    val givenText = presentation.typeText
    val labelText = trimLabelText(if (givenText.isNullOrEmpty()) "" else " $givenText", allowedWidth, normalMetrics)

    var used = getStringWidth(labelText, normalMetrics)

    presentation.typeIcon?.let {
      typeLabel.icon = it
      used += it.iconWidth
    }

    typeLabel.append(labelText)
    typeLabel.foreground = getTypeTextColor(item, foreground, presentation, selected, nonFocusedSelection)
    typeLabel.isIconOnTheRight = presentation.isTypeIconRightAligned
    return used
  }

  private fun getFontAbleToDisplay(sampleString: String?): Font? {
    if (sampleString == null) {
      return null
    }

    // assume a single font can display all chars
    val fonts = HashSet<Font>()
    val fontPreferences = lookup.fontPreferences
    for (element in sampleString) {
      fonts.add(ComplementaryFontsRegistry.getFontAbleToDisplay(element.code, Font.PLAIN, fontPreferences, null).font)
    }

    eachFont@ for (font in fonts) {
      if (font == normalFont) {
        continue
      }

      for (element in sampleString) {
        if (!font.canDisplay(element)) {
          continue@eachFont
        }
      }
      return font
    }
    return null
  }

  /**
   * Update lookup width due to visible in lookup items
   */
  private fun updateLookupWidthFromVisibleItems() {
    val visibleItems = lookup.visibleItems

    var maxWidth = if (shrinkLookup) 0 else lookupTextWidth
    for (item in visibleItems) {
      val presentation = asyncRendering.getLastComputed(item)

      item.putUserData(CUSTOM_NAME_FONT, getFontAbleToDisplay(presentation.itemText))
      item.putUserData(CUSTOM_TAIL_FONT, getFontAbleToDisplay(presentation.tailText))
      item.putUserData(CUSTOM_TYPE_FONT, getFontAbleToDisplay(presentation.typeText))

      val itemWidth = updateMaximumWidth(presentation, item)
      if (itemWidth > maxWidth) {
        maxWidth = itemWidth
      }
    }

    synchronized(widthLock) {
      if (shrinkLookup || maxWidth > lookupTextWidth) {
        lookupTextWidth = maxWidth
        lookup.requestResize()
        lookup.refreshUi(false, false)
      }
    }
  }

  fun scheduleUpdateLookupWidthFromVisibleItems() {
    lookupWidthUpdater()
  }

  fun itemAdded(element: LookupElement, fastPresentation: LookupElementPresentation) {
    updateIconWidth(fastPresentation.icon)
    scheduleUpdateLookupWidthFromVisibleItems()
    AsyncRendering.rememberPresentation(element, fastPresentation)

    updateItemPresentation(element)
  }

  fun updateItemPresentation(element: LookupElement) {
    element.expensiveRenderer?.let {
      @Suppress("UNCHECKED_CAST")
      asyncRendering.scheduleRendering(element = element, renderer = it as LookupElementRenderer<LookupElement>)
    }
  }

  fun refreshUi() {
    // Something has changed, possibly the customizers are affected, make sure the icon area is still large enough.
    updateIconWidth(EmptyIcon.ICON_0)
  }

  private fun updateIconWidth(baseIcon: Icon?) {
    var icon = baseIcon ?: return
    if (icon is DeferredIcon) {
      icon = icon.baseIcon
    }
    icon = removeVisibilityIfNeeded(lookup.editor, icon, emptyIcon)
    icon = EmptyIcon.create(icon)
    for (customizer in customizers) {
      icon = customizer.customizeEmptyIcon(icon)
    }
    if (icon.iconWidth > emptyIcon.iconWidth || icon.iconHeight > emptyIcon.iconHeight) {
      emptyIcon = EmptyIcon.create(
        max(icon.iconWidth.toDouble(), emptyIcon.iconWidth.toDouble()).toInt(),
        max(icon.iconHeight.toDouble(), emptyIcon.iconHeight.toDouble()).toInt())
      setIconInsets(nameComponent)
    }
  }

  private fun updateMaximumWidth(p: LookupElementPresentation, item: LookupElement): Int {
    updateIconWidth(p.icon)
    return calculateWidth(p, getRealFontMetrics(item, false, CUSTOM_NAME_FONT), getRealFontMetrics(item, true, CUSTOM_NAME_FONT)) +
           calcSpacing(tailComponent, null) + calcSpacing(typeLabel, null)
  }

  val textIndent: Int
    get() = panel.insets.left + nameComponent.ipad.left + emptyIcon.iconWidth + nameComponent.iconTextGap

  private class MySimpleColoredComponent : SimpleColoredComponent() {
    init {
      setFocusBorderAroundIcon(true)
    }
  }

  private inner class LookupPanel : SelectablePanel() {
    @JvmField
    var myUpdateExtender: Boolean = false

    init {
      layout = BorderLayout()
      background = BACKGROUND_COLOR
      if (isNewUI()) {
        val bodyInsets = bodyInsets()
        border = EmptyBorder(selectionInsets())
        @Suppress("UseDPIAwareInsets")
        selectionInsets = Insets(0, bodyInsets.left, 0, bodyInsets.right)
        selectionArc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
      }
    }

    fun setUpdateExtender(updateExtender: Boolean) {
      myUpdateExtender = updateExtender
    }

    override fun getPreferredSize(): Dimension = UIUtil.updateListRowHeight(super.getPreferredSize())

    override fun paint(g: Graphics) {
      @Suppress("NAME_SHADOWING")
      var g = g
      super.paint(g)
      if (NON_FOCUSED_MASK_COLOR.alpha > 0 && !lookup.isFocused && lookup.isCompletion) {
        g = g.create()
        try {
          g.color = NON_FOCUSED_MASK_COLOR
          g.fillRect(0, 0, width, height)
        }
        finally {
          g.dispose()
        }
      }
    }
  }

  /**
   * Allows updating element's presentation during completion session.
   *
   *
   * Be careful; the lookup won't be resized according to the changes inside [.customizePresentation]
   * except for the customization of the icon size, which needs to be properly implemented in
   * [.customizeEmptyIcon] for the completion popup to be aligned properly with
   * the text in the editor.
   */
  @ApiStatus.Internal
  interface ItemPresentationCustomizer {
    /**
     * Invoked from EDT thread every time lookup element is preparing to be shown. Must be very fast.
     *
     * @return presentation to show
     */
    fun customizePresentation(
      item: LookupElement,
      presentation: LookupElementPresentation,
    ): LookupElementPresentation

    /**
     * Invoked to compute the size of the icon area to ensure proper popup alignment.
     *
     *
     * Should mimic what [.customizePresentation] does
     * to the presentation's icon as close as far as the icon size is concerned.
     *
     * @param emptyIcon the empty icon, possibly already customized by the previous customizers
     * @return the modified empty icon, or `emptyIcon` if the size doesn't need to be changed
     */
    fun customizeEmptyIcon(emptyIcon: Icon): Icon
  }

  /**
   * Allows extending the original icon
   */
  interface IconDecorator : Icon {
    /**
     * Returns the original icon
     */
    val delegate: Icon?

    /**
     * Returns a new decorator with `icon` instead of the original icon
     */
    fun withDelegate(icon: Icon?): IconDecorator
  }
}

private const val ELLIPSIS = "\u2026"

   private fun calcSpacing(component: SimpleColoredComponent, icon: Icon?): Int {
     val iPad = component.ipad
     var width = iPad.left + iPad.right
     val myBorder = component.myBorder
     if (myBorder != null) {
       val insets = myBorder.getBorderInsets(component)
       width += insets.left + insets.right
     }
     val insets = component.insets
     if (insets != null) {
       width += insets.left + insets.right
     }
     if (icon != null) {
       width += icon.iconWidth + component.iconTextGap
     }
     return width
   }

   private fun getTypeTextColor(
     item: LookupElement,
     foreground: Color,
     presentation: LookupElementPresentation,
     selected: Boolean,
     nonFocusedSelection: Boolean,
   ): Color {
     if (nonFocusedSelection) {
       return foreground
     }

     return when {
       presentation.isTypeGrayed -> getGrayedForeground(selected)
       item is EmptyLookupItem -> JBColor.foreground()
       else -> foreground
     }
   }

   private fun getTailTextColor(
     isSelected: Boolean,
     fragment: LookupElementPresentation.TextFragment,
     defaultForeground: Color,
     nonFocusedSelection: Boolean,
   ): Color {
     if (nonFocusedSelection) {
       return defaultForeground
     }

     if (fragment.isGrayed) {
       return getGrayedForeground(isSelected)
     }

     if (!isSelected) {
       fragment.foregroundColor?.let {
         return it
       }
     }

     return defaultForeground
   }

/**
 * Splits the nameComponent into fragments based on the offsets of the decorated text ranges,
 * then applies the appropriate decorations to each fragment.
 */
private fun renderItemNameDecoration(
  nameComponent: SimpleColoredComponent,
  itemNameDecorations: List<DecoratedTextRange>,
) {
  if (nameComponent.fragmentCount == 0 || itemNameDecorations.isEmpty()) {
    return
  }

  val offsetsToSplit = itemNameDecorations.asSequence()
    .map { it.textRange }
    .flatMap { sequenceOf(it.startOffset, it.endOffset) }
    .sorted()
    .distinct()
    .toList()
  splitSimpleColoredComponentAtLeastByOffsets(nameComponent, offsetsToSplit)

  val editorColorsScheme = EditorColorsManager.getInstance().globalScheme

  val iterator = nameComponent.iterator()
  while (iterator.hasNext()) {
    iterator.next()
    val decorations = itemNameDecorations.asSequence()
      .filter { it.textRange.intersectsStrict(iterator.offset, iterator.endOffset) }
      .map { it.decoration() }
      .toList()

    if (decorations.isEmpty()) {
      continue
    }

    for (decoration in decorations) {
      val newAttributes = iterator.textAttributes.toTextAttributes()
      if (decoration == LookupItemDecoration.ERROR) {
        val color = editorColorsScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).effectColor
        TextAttributesEffectsBuilder.create().coverWith(EffectType.WAVE_UNDERSCORE, color).applyTo(newAttributes)
        iterator.textAttributes = SimpleTextAttributes.fromTextAttributes(newAttributes)
      }

      // must be the last
      if (decoration == LookupItemDecoration.HIGHLIGHT_MATCHED) {
        iterator.textAttributes = SimpleTextAttributes(iterator.textAttributes.style, MATCHED_FOREGROUND_COLOR)
      }
    }
  }
}

private fun splitSimpleColoredComponentAtLeastByOffsets(component: SimpleColoredComponent, offsets: List<Int>) {
  val iterator = component.iterator()
  iterator.next()
  for (offset in offsets) {
    while (iterator.hasNext() && offset >= iterator.endOffset) {
      iterator.next()
    }
    if (offset > iterator.offset && offset < iterator.endOffset) {
      iterator.split(offset - iterator.offset, iterator.textAttributes)
    }
  }
}

private fun removeVisibilityIfNeeded(editor: Editor?, icon: Icon, standard: Icon): Icon {
  return if (Registry.`is`("ide.completion.show.visibility.icon")) icon else removeVisibility(editor, icon, standard)
}

private fun removeVisibility(editor: Editor?, icon: Icon, standard: Icon): Icon {
  if (icon is IconDecorator) {
    val delegateIcon = icon.delegate
    if (delegateIcon != null) {
      return icon.withDelegate(removeVisibility(editor, delegateIcon, standard))
    }
  }
  else if (icon is RowIcon) {
    if (icon.iconCount >= 1) {
      val firstIcon = icon.getIcon(0)
      if (firstIcon != null) {
        return if (Registry.`is`("editor.scale.completion.icons")) EditorUtil.scaleIconAccordingEditorFont(firstIcon, editor) else firstIcon
      }
    }
  }
  else if (icon.iconWidth > standard.iconWidth || icon.iconHeight > standard.iconHeight) {
    return cropIcon(icon, Rectangle(standard.iconWidth, standard.iconHeight))
  }
  return icon
}

private fun getCustomFont(item: LookupElement, bold: Boolean, key: Key<Font>): Font? {
  val font = item.getUserData(key) ?: return null
  return if (bold) font.deriveFont(Font.BOLD) else font
}

private fun setIconInsets(component: SimpleColoredComponent) {
  component.ipad = JBUI.insetsLeft(6)
}

private fun selectionInsets(): Insets {
  val innerInsets = JBUI.CurrentTheme.CompletionPopup.selectionInnerInsets()
  val bodyInsets = bodyInsets()
  @Suppress("UseDPIAwareInsets")
  return Insets(innerInsets.top, innerInsets.left + bodyInsets.left, innerInsets.bottom, innerInsets.right + bodyInsets.right)
}

private fun calculateWidth(presentation: LookupElementPresentation, normalMetrics: FontMetrics, boldMetrics: FontMetrics): Int {
  var result = if (isNewUI()) {
    val insets = selectionInsets()
    insets.left + insets.right
  }
  else {
    0
  }
  result += getStringWidth(presentation.itemText, if (presentation.isItemTextBold) boldMetrics else normalMetrics)
  result += getStringWidth(presentation.tailText, normalMetrics)

  val typeText = presentation.typeText
  if (!typeText.isNullOrEmpty()) {
    // nice tail-type separation
    result += getStringWidth("W", normalMetrics)
    result += getStringWidth(typeText, normalMetrics)
  }

  // for unforeseen Swing size adjustments
  result += getStringWidth("W", boldMetrics)
  presentation.typeIcon?.let {
    result += it.iconWidth
  }
  return result
}

private fun getStringWidth(text: String?, metrics: FontMetrics): Int = if (text == null) 0 else metrics.stringWidth(text)

@StyleAttributeConstant
private fun getStyle(strikeout: Boolean, underlined: Boolean, italic: Boolean): Int {
  var style = SimpleTextAttributes.STYLE_PLAIN
  if (strikeout) {
    style = style or SimpleTextAttributes.STYLE_STRIKEOUT
  }
  if (underlined) {
    style = style or SimpleTextAttributes.STYLE_UNDERLINE
  }
  if (italic) {
    style = style or SimpleTextAttributes.STYLE_ITALIC
  }
  return style
}