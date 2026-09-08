// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.CoroutineSupport.UiDispatcherKind
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupCornerType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.reference.SoftReference
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBFontScaler
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration.Companion.builder
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.SingleEdtTaskScheduler.Companion.createSingleEdtTaskScheduler
import com.intellij.util.ui.ExtendableHTMLViewFactory
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.JBValue.UIInteger
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.lang.ref.WeakReference
import java.net.URL
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View
import kotlin.math.min

/**
 * Standard implementation of the help context tooltip.
 * 
 * <h2>Overview</h2>
 * 
 * UI design requires having tooltips that contain detailed information about UI actions and controls.
 * Embedded context help tooltip functionality is incorporated into this class.
 * 
 * ```kotlin
 * HelpTooltip()
 *   .setPlainTextTitle("Title")
 *   .setShortcut("Shortcut")
 *   .setDescription(HtmlChunk.text("Description"))
 *   .installOn(component)
 * ```
 *
 * <h2>Restrictions and field formats</h2>
 * 
 * If you're creating a tooltip with a shortcut, then title is mandatory otherwise title, description, link are optional.
 * You can optionally set the tooltip relative location using [setLocation].
 * The `Alignment` enum defines fixed relative locations according to the design document (see the link below).
 * More types of relative location will be added as needed, but there won't be a way to choose the location on a pixel basis.
 * 
 * 
 * No HTML tagging is allowed in the shortcut, it is supposed to be a simple text string.
 * 
 * Title and description can be HTML formatted. You can use all possible HTML tagging in description just without enclosing
 * &lt;html&gt; and &lt;/html&gt; tags themselves. In description, it's allowed to have &lt;p/&gt; or &lt;p&gt; tags between paragraphs.
 * Paragraphs will be rendered with the standard (10px) offset from the title, from one another, and from the link.
 * To force the line break in a paragraph, use &lt;br/&gt;. Standard font coloring and styling are also available.
 * 
 * <h2>Timeouts</h2>
 * 
 * 
 * Single line tooltips auto close in 10 seconds, multiline in 30 seconds. You can optionally disable auto closing by
 * setting [setNeverHideOnTimeout] to `true`. By default, tooltips don't close after a timeout on help buttons
 * (those having a round icon with question mark). Before setting this option to true, you should contact designers first.
 * 
 * 
 * System-wide tooltip timeouts are set through the registry:
 * 
 *  * &nbsp;ide.helptooltip.full.dismissDelay - multiline tooltip timeout (default 30 seconds)
 *  * &nbsp;ide.helptooltip.regular.dismissDelay - single line tooltip timeout (default 10 seconds)
 * 
 * 
 * <h2>Avoiding multiple popups</h2>
 * 
 * Some actions may open a popup menu.
 * The current design is that the action's popup menu should take over the help tooltip.
 * This is partly implemented in `AbstractPopup` class to track such cases. But this doesn't always work.
 * If the help tooltip shows up over the component's popup menu, you should make sure you set the master popup for the help tooltip.
 * This will prevent the help tooltip from showing when the popup menu is opened.
 * The best way to do it is to take a source component from an `InputEvent`
 * and pass the source component along with the popup menu reference to [setMasterPopup] static method.
 * 
 * 
 * If you're handling `DumbAware.actionPerformed(AnActionEvent e)`, it has `InputEvent`in `AnActionEvent` which you can use to get the source.
 * 
 * <h2>ContextHelpLabel</h2>
 * 
 * ContextHelpLabel is a convenient `JLabel` which contains a help icon and has a HelpTooltip installed on it.
 * You can create it using one of its static methods and pass title/description/link. This label can also be used in forms.
 * The UI designer will offer to create `private void createUIComponents()` method where you can create the label with a static method.
 */
open class HelpTooltip {
  /** Can contain HTML text  */
  @get:ApiStatus.Internal
  var title: Supplier<@TooltipTitle String>? = null
    private set

  @NlsSafe
  private var shortcut: @NlsSafe String? = null

  /** Can contain HTML text  */
  @get:ApiStatus.Internal
  var description: @NlsContexts.Tooltip String? = null
    private set

  @get:ApiStatus.Internal
  var link: ActionLink? = null
    private set
  private var linkOriginalFontScaler: JBFontScaler? = null
  private var neverHide = false
  private var alignment = Alignment.CURSOR

  private var masterPopupOpenCondition: BooleanSupplier? = null

  private var myPopup: JBPopup? = null

  // todo use strict when Editor will be fixed (EditorImpl.logicalPositionToOffset requires read action)
  private val popupAlarm = createSingleEdtTaskScheduler(UiDispatcherKind.RELAX)
  private var isOverPopup = false
  private var isMultiline = false

  /** Owner component captured in [.scheduleShow]; used for screen-relative width detection in the long-text auto-wrap path.  */
  private var popupOwner: WeakReference<Component>? = null
  private var myInitialDelay = -1

  @get:ApiStatus.Internal
  var hideDelay: Int = -1
    private set

  private var myToolTipText: String? = null
  private var initialShowScheduled = false

  @JvmField
  protected var myMouseListener: MouseAdapter = object : MouseAdapter() {}

  /**
   * Location of the HelpTooltip relatively to the owner component.
   */

  enum class Alignment {
    RIGHT {
      override fun getPointFor(owner: Component, popupSize: Dimension, mouseLocation: Point): Point {
        val size = owner.size
        return Point(size.width + scale(5) - X_OFFSET.get(), scale(1) + Y_OFFSET.get())
      }
    },

    LEFT {
      override fun getPointFor(owner: Component, popupSize: Dimension, mouseLocation: Point): Point {
        return Point(-popupSize.width - scale(5) + X_OFFSET.get(), scale(1) + Y_OFFSET.get())
      }
    },

    TOP {
      override fun getPointFor(owner: Component, popupSize: Dimension, mouseLocation: Point): Point {
        return Point(scale(1) + X_OFFSET.get(), -scale(5) - popupSize.height + Y_OFFSET.get())
      }
    },

    BOTTOM {
      override fun getPointFor(owner: Component, popupSize: Dimension, mouseLocation: Point): Point {
        val size = owner.size
        return Point(scale(1) + X_OFFSET.get(), scale(5) + size.height - Y_OFFSET.get())
      }
    },

    HELP_BUTTON {
      override fun getPointFor(owner: Component, popupSize: Dimension, mouseLocation: Point): Point {
        val i = (owner as JComponent).getInsets()
        return Point(X_OFFSET.get() - scale(40), i.top + Y_OFFSET.get() - scale(6) - popupSize.height)
      }
    },

    CURSOR {
      override fun getPointFor(owner: Component, popupSize: Dimension, mouseLocation: Point): Point {
        var location = mouseLocation.location
        location.y += CURSOR_OFFSET.get()

        SwingUtilities.convertPointToScreen(location, owner)
        val r = Rectangle(location, popupSize)
        ScreenUtil.moveToFit(r, ScreenUtil.getScreenRectangle(owner), null, true)
        location = r.location
        SwingUtilities.convertPointFromScreen(location, owner)
        r.location = location

        if (r.contains(mouseLocation)) {
          location.y = mouseLocation.y - r.height - JBUI.scale(5)
        }

        return location
      }
    };

    abstract fun getPointFor(owner: Component, popupSize: Dimension, mouseLocation: Point): Point
  }

  /**
   * Sets tooltip title content.
   *
   *
   * Title is allowed to contain HTML markup. Construct the title using [HtmlChunk].
   * If your title doesn't suppose to contain HTML markup,
   * prefer using [.setPlainTextTitle] to avoid accidental HTML injections.
   *
   *
   * If it's longer than two lines (fitting in 250 pixels each),
   * then the text is automatically stripped to the word boundary and dots are added to the end.
   */
  open fun setTitle(title: HtmlChunk?): HelpTooltip {
    this.title = if (title != null) Supplier { title.toString() } else null
    return this
  }

  /**
   * @see .setTitle
   */
  open fun setTitleSupplier(title: Supplier<HtmlChunk>?): HelpTooltip {
    this.title = if (title != null) Supplier { title.get().toString() } else null
    return this
  }

  /**
   * Sets tooltip title content.
   *
   *
   * The provided title will be properly escaped and rendered as a plain text.
   * If it's longer than two lines (fitting in 250 pixels each),
   * then the text is automatically stripped to the word boundary and dots are added to the end.
   */
  open fun setPlainTextTitle(@TooltipTitle title: @TooltipTitle String?): HelpTooltip {
    this.title = if (title != null) Supplier { StringUtil.escapeXmlEntities(title) } else null
    return this
  }

  /**
   * @see .setPlainTextTitle
   */
  open fun setPlainTextTitle(title: Supplier<@TooltipTitle String>): HelpTooltip {
    this.title = Supplier { StringUtil.escapeXmlEntities(title.get()) }
    return this
  }

  @Deprecated("use {@link #setTitle(HtmlChunk)} or {@link #setPlainTextTitle(String)} instead to avoid accidental HTML injections.")
  open fun setTitle(@TooltipTitle title: @TooltipTitle String?): HelpTooltip {
    this.title = if (title != null) Supplier { title } else null
    return this
  }

  @Deprecated("use {@link #setTitleSupplier(Supplier)} or {@link #setPlainTextTitle(Supplier)} instead to avoid accidental HTML injections.")
  open fun setTitle(title: Supplier<String>?): HelpTooltip {
    this.title = title
    return this
  }

  /**
   * Sets text for the shortcut placeholder.
   *
   * @param shortcut text for shortcut.
   * @return `this`
   */
  open fun setShortcut(@NlsSafe shortcut: @NlsSafe String?): HelpTooltip {
    this.shortcut = shortcut
    return this
  }

  open fun setShortcut(shortcut: Shortcut?): HelpTooltip {
    this.shortcut = if (shortcut == null) null else getShortcutText(shortcut)
    return this
  }

  /**
   * Set HelpTooltip initial delay. A tooltip is show after component's mouse entering plus initial delay.
   * @param delay - non negative value for initial delay
   * @return `this`
   * @throws IllegalArgumentException if delay is less than zero
   */
  open fun setInitialDelay(delay: Int): HelpTooltip {
    require(delay >= 0) { "Negative delay is not allowed" }

    myInitialDelay = delay
    return this
  }

  /**
   * Set HelpTooltip hide delay. Tooltip is hidden after component's mouse exit plus hide delay.
   * @param delay - non negative value for hide delay
   * @return `this`
   * @throws IllegalArgumentException if delay is less than zero
   */
  open fun setHideDelay(delay: Int): HelpTooltip {
    require(delay >= 0) { "Negative delay is not allowed" }

    this.hideDelay = delay
    return this
  }

  /**
   * Sets tooltip description content.
   *
   *
   * Description is allowed to contain HTML markup. Construct the description using [HtmlChunk].
   * If your description doesn't suppose to contain HTML markup,
   * prefer using [HtmlChunk.text] to avoid accidental HTML injections.
   */
  open fun setDescription(description: HtmlChunk?): HelpTooltip {
    this.description = description?.toString()
    return this
  }

  @Deprecated("use {@link #setDescription(HtmlChunk)} instead to avoid accidental HTML injections.")
  open fun setDescription(@NlsContexts.Tooltip description: @NlsContexts.Tooltip String?): HelpTooltip {
    this.description = description
    return this
  }

  /**
   * Enables a link in the tooltip below description and sets action for it.
   *
   * @param linkText text to show in the link.
   * @param linkAction action to execute when a link is clicked.
   * @return `this`
   */
  open fun setLink(@NlsContexts.LinkLabel linkText: @NlsContexts.LinkLabel String, linkAction: Runnable): HelpTooltip {
    return setLink(linkText, linkAction, false)
  }

  /**
   * Enables a link in the tooltip below description and sets action for it.
   *
   * @param linkText text to show in the link.
   * @param linkAction action to execute when a link is clicked.
   * @param external whether the link is "external" or not
   * @return `this`
   */
  open fun setLink(
    @NlsContexts.LinkLabel linkText: @NlsContexts.LinkLabel String,
    linkAction: Runnable,
    external: Boolean,
  ): HelpTooltip {
    link = object : MyActionLink(linkText, linkAction, external) {
      override fun hidePopup() {
        this@HelpTooltip.hidePopup(true)
      }
    }
    linkOriginalFontScaler = JBFontScaler(link!!.getFont())
    return this
  }

  /**
   * Enables a link in the tooltip below description and sets `BrowserUtil.browse` action for it.
   * It's then painted with a small arrow button.
   *
   * @param linkLabel text to show in the link.
   * @param url URL to browse.
   * @return `this`
   */
  open fun setBrowserLink(@NlsContexts.LinkLabel linkLabel: @NlsContexts.LinkLabel String, url: URL): HelpTooltip {
    link = BrowserLink(linkLabel, url.toExternalForm())
    link!!.setHorizontalTextPosition(SwingConstants.LEFT)
    linkOriginalFontScaler = JBFontScaler(link!!.getFont())
    return this
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val tooltip = other as HelpTooltip
    return neverHide == tooltip.neverHide && (if (title == null)
      tooltip.title == null
    else
      tooltip.title != null && title!!.get() == tooltip.title!!.get()) &&
           shortcut == tooltip.shortcut &&
           description == tooltip.description &&
           linksEqual(link, tooltip.link) && alignment === tooltip.alignment &&
           masterPopupOpenCondition == tooltip.masterPopupOpenCondition
  }

  /**
   * Toggles whether to hide tooltip automatically on timeout. For default behavior just don't call this method.
   *
   * @param neverHide `true` don't hide, `false` otherwise.
   * @return `this`
   */
  open fun setNeverHideOnTimeout(neverHide: Boolean): HelpTooltip {
    this.neverHide = neverHide
    return this
  }

  /**
   * Sets location of the tooltip relatively to the owner component.
   *
   * @param alignment is relative location
   * @return `this`
   */
  open fun setLocation(alignment: Alignment): HelpTooltip {
    this.alignment = alignment
    return this
  }

  /**
   * Installs the tooltip after the configuration has been completed on the specified owner component.
   *
   * @param component is the owner component for the tooltip.
   */
  open fun installOn(component: JComponent) {
    val installed = component.getClientProperty(TOOLTIP_PROPERTY) as? HelpTooltip

    if (installed == null) {
      installImpl(component)
    }
    else if (!equals(installed)) {
      installed.hideAndDispose(component)
      installImpl(component)
    }
  }

  private fun installImpl(component: JComponent) {
    neverHide = neverHide || UIUtil.isHelpButton(component)

    createMouseListeners()

    component.putClientProperty(TOOLTIP_PROPERTY, this)
    installMouseListeners(component)
  }

  protected open fun shouldForceHiding(): Boolean {
    return link == null
  }

  protected fun createMouseListeners() {
    myMouseListener = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        if (myPopup != null && !myPopup!!.isDisposed()) {
          myPopup!!.cancel()
        }
        initialShowScheduled = true
        var delay = myInitialDelay
        if (delay == -1) {
          delay = intValue("ide.tooltip.initialReshowDelay", 500)
        }
        scheduleShow(e, delay)
      }

      override fun mouseExited(e: MouseEvent?) {
        var delay: Int = hideDelay
        if (delay == -1) {
          delay = intValue("ide.tooltip.initialDelay.highlighter", 150)
        }
        scheduleHide(shouldForceHiding(), delay)
      }

      override fun mouseMoved(e: MouseEvent) {
        if (!initialShowScheduled) {
          scheduleShow(e, intValue("ide.tooltip.reshowDelay"))
        }
      }
    }
  }

  protected open fun createIsOverTipMouseListener(): MouseListener {
    return object : OverTipMouseListener() {}
  }

  protected open inner class OverTipMouseListener protected constructor() :
    MouseAdapter() {

    protected open fun doEnter() {
      isOverPopup = true
    }

    protected open fun doExit() {
      isOverPopup = false
      hidePopup(false)
    }

    override fun mouseEntered(e: MouseEvent?) {
      doEnter()
    }

    override fun mouseExited(e: MouseEvent) {
      if (link == null || !link!!.bounds.contains(e.getPoint())) {
        doExit()
      }
    }
  }

  @ApiStatus.Internal
  open fun createTipPanel(): JPanel {
    isMultiline = false

    val tipPanel = JPanel()
    tipPanel.setLayout(VerticalLayout(JBUI.getInt("HelpTooltip.verticalGap", 4)))
    tipPanel.setBackground(UIUtil.getToolTipBackground())

    val currentTitle = if (title != null) title!!.get() else null
    val hasTitle = Strings.isNotEmpty(currentTitle)
    val hasDescription = Strings.isNotEmpty(description)

    if (hasTitle) {
      tipPanel.add(createTitleComponent(hasDescription), VerticalLayout.TOP)
    }

    if (hasDescription) {
      @Nls val pa: Array<String> = description!!.split(PARAGRAPH_SPLITTER.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      isMultiline = pa.size > 1
      for (p in pa) {
        if (!p.isEmpty()) {
          tipPanel.add(Paragraph(p, hasTitle), VerticalLayout.TOP)
        }
      }
    }

    if (!hasTitle && Strings.isNotEmpty(shortcut)) {
      val shortcutLabel = JLabel(shortcut)
      shortcutLabel.setFont(deriveDescriptionFont(shortcutLabel.getFont(), false))
      shortcutLabel.setForeground(JBUI.CurrentTheme.Tooltip.shortcutForeground())

      tipPanel.add(shortcutLabel, VerticalLayout.TOP)
    }

    if (link != null && linkOriginalFontScaler != null) {
      link!!.setForeground(LINK_COLOR)
      link!!.setFont(deriveDescriptionFont(linkOriginalFontScaler!!.scaledFont(), hasTitle))
      tipPanel.add(link!!, VerticalLayout.TOP)
    }

    isMultiline = isMultiline || Strings.isNotEmpty(description) && (Strings.isNotEmpty(currentTitle) || link != null)
    tipPanel.setBorder(textBorder(isMultiline))

    return tipPanel
  }

  private fun createTitleComponent(hasDescription: Boolean): JComponent {
    val popupOwner = if (this.popupOwner == null) null else this.popupOwner!!.get()
    val singleLineTitle = Header(hasDescription)
    if (popupOwner != null && popupOwner.isShowing()) {
      val screen = ScreenUtil.getScreenRectangle(popupOwner)
      val maxWidth = (screen.width * 0.9).toInt()
      val maxHeight = (screen.height * 0.9).toInt()
      if (singleLineTitle.getPreferredSize().width > maxWidth) {
        isMultiline = true
        return createLongHtmlTextTitle(this.htmlTitle, singleLineTitle.getFont(), maxWidth, maxHeight)
      }
    }
    return singleLineTitle
  }

  private fun installMouseListeners(owner: JComponent) {
    owner.addMouseListener(myMouseListener)
    owner.addMouseMotionListener(myMouseListener)
  }

  private fun uninstallMouseListeners(owner: JComponent) {
    owner.removeMouseListener(myMouseListener)
    owner.removeMouseMotionListener(myMouseListener)
  }

  private fun hideAndDispose(owner: JComponent) {
    hidePopup(true)
    uninstallMouseListeners(owner)
    masterPopupOpenCondition = null
    owner.putClientProperty(TOOLTIP_PROPERTY, null)
  }

  private fun scheduleShow(e: MouseEvent, delay: Int) {
    popupAlarm.cancel()

    if (isTooltipDisabled(e.component)) {
      return
    }
    if (ScreenReader.isActive()) {
      // disable HelpTooltip in screen reader mode
      return
    }

    popupAlarm.request(delay.toLong(), Runnable {
      initialShowScheduled = false
      if (masterPopupOpenCondition != null && !masterPopupOpenCondition!!.asBoolean) {
        return@Runnable
      }

      val owner = e.component
      popupOwner = WeakReference(owner)
      val text = if (owner is JComponent) owner.getToolTipText(e) else null
      if (myPopup != null && !myPopup!!.isDisposed()) {
        if (Strings.isEmpty(text) && Strings.isEmpty(myToolTipText)) {
          // do nothing if a tooltip becomes empty
          return@Runnable
        }
        if (text == myToolTipText) {
          // do nothing if a tooltip is not changed
          return@Runnable
        }
        // cancel the previous popup before showing a new one
        myPopup!!.cancel()
      }

      myToolTipText = text
      val tipPanel: JComponent = createTipPanel()
      tipPanel.addMouseListener(createIsOverTipMouseListener())
      val popupBuilder: ComponentPopupBuilder = initPopupBuilder(tipPanel)
      myPopup = popupBuilder.createPopup()
      myPopup!!.show(
        RelativePoint(
          owner,
          alignment.getPointFor(owner, tipPanel.getPreferredSize(), e.getPoint())
        )
      )

      if (!neverHide) {
        val dismissDelay =
          intValue(if (isMultiline) "ide.helptooltip.full.dismissDelay" else "ide.helptooltip.regular.dismissDelay")
        scheduleHide(true, dismissDelay)
      }
    })
  }

  private fun scheduleHide(force: Boolean, delay: Int) {
    popupAlarm.cancelAndRequest(delay.toLong(), Runnable { hidePopup(force) })
  }

  protected open fun hidePopup(force: Boolean) {
    initialShowScheduled = false
    popupAlarm.cancel()

    if (myPopup != null && (!isOverPopup || force)) {
      if (myPopup!!.isVisible()) {
        myPopup!!.cancel()
      }
      myPopup = null
      myToolTipText = null
      popupOwner = null
    }
  }

  private open class BoundWidthLabel : JLabel() {
    fun setSizeForWidth(width: Float) {
      var width = width
      if (width > MAX_WIDTH.get()) {
        val v = getClientProperty(BasicHTML.propertyKey) as? View
        if (v != null) {
          width = 0.0f
          for (row in getRows(v)) {
            val rWidth = row.getPreferredSpan(View.X_AXIS)
            if (width < rWidth) {
              width = rWidth
            }
          }

          v.setSize(width, v.getPreferredSpan(View.Y_AXIS))
        }
      }
    }

    companion object {
      private fun getRows(root: View): MutableCollection<View> {
        val rows: MutableCollection<View> = ArrayList()
        visit(root, rows)
        return rows
      }

      private fun visit(v: View, result: MutableCollection<in View>) {
        val cname = v.javaClass.getCanonicalName()
        if (cname != null && cname.contains("ParagraphView.Row")) {
          result.add(v)
        }

        for (i in 0..<v.viewCount) {
          visit(v.getView(i), result)
        }
      }
    }
  }

  @ApiStatus.Internal
  fun fromSameWindowAs(component: Component): Boolean {
    if (myPopup != null && !myPopup!!.isDisposed()) {
      val popupWindow = SwingUtilities.getWindowAncestor(myPopup!!.getContent())
      return component === popupWindow || SwingUtilities.getWindowAncestor(component) === popupWindow
    }
    return false
  }

  private inner class Header(obeyWidth: Boolean) : BoundWidthLabel() {
    init {
      setFont(deriveHeaderFont(getFont()))
      setForeground(UIUtil.getToolTipForeground())

      val currentTitle: String = nonNullTitle
      if (obeyWidth || currentTitle.length > MAX_WIDTH.get()) {
        val v = BasicHTML.createHTMLView(
          this,
          String.format(
            "<html>%s%s</html>",
            currentTitle,
            shortcutAsHTML
          )
        )
        val width = v.getPreferredSpan(View.X_AXIS)
        isMultiline = isMultiline || width > MAX_WIDTH.get()
        val div = if (width > MAX_WIDTH.get()) HtmlChunk.div().attr("width", MAX_WIDTH.get()) else HtmlChunk.div()
        setText(
          div.children(
            HtmlChunk.raw(currentTitle),
            HtmlChunk.raw(shortcutAsHTML),
          ).wrapWith(HtmlChunk.html())
            .toString()
        )
        setSizeForWidth(width)
      }
      else {
        setText(htmlTitle)
      }
    }
  }

  @get:TooltipTitle
  private val htmlTitle: @TooltipTitle String
    get() {
      val currentTitle = this.nonNullTitle
      return if (BasicHTML.isHTMLString(currentTitle)) currentTitle
      else HtmlChunk.div()
        .addRaw(currentTitle).addRaw(this.shortcutAsHTML).wrapWith(HtmlChunk.html()).toString()
    }

  @get:NlsSafe
  private val shortcutAsHTML: @NlsSafe String
    get() = getShortcutAsHtml(shortcut)

  @get:TooltipTitle
  private val nonNullTitle: @TooltipTitle String
    get() = title?.get() ?: ""

  private inner class Paragraph(
    @NlsContexts.Tooltip
    text: String,
    hasTitle: Boolean,
  ) : BoundWidthLabel() {
    init {
      setForeground(if (hasTitle) INFO_COLOR else UIUtil.getToolTipForeground())
      setFont(deriveDescriptionFont(getFont(), hasTitle))

      val v = BasicHTML.createHTMLView(this, HtmlChunk.raw(text).wrapWith(HtmlChunk.html()).toString())
      val width = v.getPreferredSpan(View.X_AXIS)
      isMultiline = isMultiline || width > MAX_WIDTH.get()
      val div = if (width > MAX_WIDTH.get()) HtmlChunk.div().attr("width", MAX_WIDTH.get()) else HtmlChunk.div()
      setText(div.addRaw(text).wrapWith(HtmlChunk.html()).toString())

      setSizeForWidth(width)
    }
  }

  private abstract class MyActionLink(
    @NlsContexts.LinkLabel text: @NlsContexts.LinkLabel String,
    val linkAction: Runnable,
    val external: Boolean,
  ) : ActionLink() {
    init {
      setText(text)
      addActionListener(ActionListener {
        hidePopup()
        linkAction.run()
      })
      if (external) {
        setExternalLinkIcon()
      }
    }

    protected abstract fun hidePopup()
  }

  companion object {
    private val INFO_COLOR: Color = JBColor.namedColor("ToolTip.infoForeground", JBUI.CurrentTheme.ContextHelp.FOREGROUND)
    private val LINK_COLOR: Color = JBColor.namedColor("ToolTip.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED)

    private val MAX_WIDTH: JBValue = UIInteger("HelpTooltip.maxWidth", 250)
    private val X_OFFSET: JBValue = UIInteger("HelpTooltip.xOffset", 0)
    private val Y_OFFSET: JBValue = UIInteger("HelpTooltip.yOffset", 0)
    private val HEADER_FONT_SIZE_DELTA: JBValue = UIInteger("HelpTooltip.fontSizeDelta", 0)
    private val DESCRIPTION_FONT_SIZE_DELTA: JBValue = UIInteger("HelpTooltip.descriptionSizeDelta", 0)
    private val CURSOR_OFFSET: JBValue = UIInteger("HelpTooltip.mouseCursorOffset", 20)

    private const val PARAGRAPH_SPLITTER = "<p/?>"
    private const val TOOLTIP_PROPERTY = "JComponent.helpTooltip"
    private const val TOOLTIP_DISABLED_PROPERTY = "JComponent.helpTooltipDisabled"

    @ApiStatus.Internal
    @JvmStatic
    fun initPopupBuilder(tipPanel: JComponent): ComponentPopupBuilder {
      return JBPopupFactory.getInstance()
        .createComponentPopupBuilder(tipPanel, null)
        .setShowBorder(UIManager.getBoolean("ToolTip.paintBorder"))
        .setBorderColor(JBUI.CurrentTheme.Tooltip.borderColor())
        .setShowShadow(true)
        .addUserData(PopupCornerType.RoundedTooltip)
    }

    private fun createLongHtmlTextTitle(
      @TooltipTitle htmlTitle: @TooltipTitle String,
      font: Font,
      maxWidth: Int,
      maxHeight: Int,
    ): JBHtmlPane {
      val htmlPane: JBHtmlPane = configureHtmlPane(
        JBHtmlPane(
          JBHtmlPaneStyleConfiguration(),
          builder()
            .extensions(ExtendableHTMLViewFactory.Extensions.WORD_WRAP)
            .build()
        )
      )
      htmlPane.setText(htmlTitle)
      htmlPane.setFont(font)
      htmlPane.size = Dimension(maxWidth, maxHeight)
      val wrapped = htmlPane.getPreferredSize()
      htmlPane.preferredSize = Dimension(
        min(wrapped.width, maxWidth),
        min(wrapped.height, maxHeight),
      )
      htmlPane.maximumSize = Dimension(maxWidth, maxHeight)
      return htmlPane
    }

    private fun configureHtmlPane(htmlPane: JBHtmlPane): JBHtmlPane {
      htmlPane.isEditable = false
      htmlPane.setOpaque(false)
      htmlPane.setBorder(JBUI.Borders.empty())
      htmlPane.setMargin(JBUI.emptyInsets())
      htmlPane.setForeground(UIUtil.getToolTipForeground())
      htmlPane.setBackground(UIUtil.getToolTipBackground())
      htmlPane.setFocusable(false)
      return htmlPane
    }

    @JvmStatic
    fun getTooltipFor(owner: JComponent): HelpTooltip? {
      return owner.getClientProperty(TOOLTIP_PROPERTY) as? HelpTooltip
    }

    /**
     * Hides and disposes the tooltip possibly installed on the mentioned component. Disposing means
     * unregistering all `HelpTooltip` specific listeners installed on the component.
     * If there is no tooltip installed on the component, nothing happens.
     *
     * @param owner a possible `HelpTooltip` owner.
     */
    @JvmStatic
    fun dispose(owner: Component) {
      if (owner is JComponent) {
        val instance = owner.getClientProperty(TOOLTIP_PROPERTY) as? HelpTooltip
        instance?.hideAndDispose(owner)
      }
    }

    /**
     * Hides the tooltip possibly installed on the mentioned component without disposing.
     * Listeners are not removed.
     * If there is no tooltip installed on the component, nothing happens.
     *
     * @param owner a possible `HelpTooltip` owner.
     */
    @JvmStatic
    fun hide(owner: Component) {
      if (owner is JComponent) {
        val instance = owner.getClientProperty(TOOLTIP_PROPERTY) as? HelpTooltip
        instance?.hidePopup(true)
      }
    }

    /**
     * Sets master popup for the current `HelpTooltip`. Master popup takes over the help tooltip,
     * so when the master popup is about to be shown, help tooltip hides.
     *
     * @param owner possible owner
     * @param master master popup
     */
    @JvmStatic
    fun setMasterPopup(owner: Component, master: JBPopup?) {
      if (owner is JComponent) {
        val tooltip = owner.getClientProperty(TOOLTIP_PROPERTY) as? HelpTooltip
        if (tooltip != null && tooltip.myPopup !== master) {
          val popupRef = WeakReference(master)
          tooltip.masterPopupOpenCondition = BooleanSupplier {
            val popup = SoftReference.dereference(popupRef)
            popup == null || !popup.isVisible()
          }
        }
      }
    }

    /**
     * Sets master popup open condition supplier for the current `HelpTooltip`.
     * This method is more general than [setMasterPopup] so that
     * it's possible to create master popup condition for any types of popups such as `JPopupMenu`
     *
     * @param owner possible owner
     * @param condition a `BooleanSupplier` for open condition
     */
    @JvmStatic
    fun setMasterPopupOpenCondition(owner: Component, condition: BooleanSupplier?) {
      if (owner is JComponent) {
        val instance = owner.getClientProperty(TOOLTIP_PROPERTY) as? HelpTooltip
        if (instance != null) {
          instance.masterPopupOpenCondition = condition
        }
      }
    }

    @JvmStatic
    fun disableTooltip(source: Component?) {
      if (source is JComponent) {
        source.putClientProperty(TOOLTIP_DISABLED_PROPERTY, true)
      }
    }

    @JvmStatic
    fun enableTooltip(source: Component?) {
      if (source is JComponent) {
        source.putClientProperty(TOOLTIP_DISABLED_PROPERTY, null)
      }
    }

    private fun isTooltipDisabled(component: Component?): Boolean {
      if (component is JComponent) {
        val disabled = component.getClientProperty(TOOLTIP_DISABLED_PROPERTY) as? Boolean
        return disabled == true
      }
      else {
        return false
      }
    }

    private fun textBorder(multiline: Boolean): Border {
      val i = if (multiline)
        JBUI.CurrentTheme.HelpTooltip.defaultTextBorderInsets()
      else
        JBUI.CurrentTheme.HelpTooltip.smallTextBorderInsets()
      return JBEmptyBorder(i)
    }

    private fun deriveHeaderFont(font: Font): Font {
      return font.deriveFont(font.getSize().toFloat() + HEADER_FONT_SIZE_DELTA.get())
    }

    private fun deriveDescriptionFont(font: Font, hasTitle: Boolean): Font {
      return if (hasTitle) font.deriveFont(font.getSize().toFloat() + DESCRIPTION_FONT_SIZE_DELTA.get()) else deriveHeaderFont(font)
    }

    @Contract(pure = true)
    @JvmStatic
    fun getShortcutAsHtml(shortcut: String?): String {
      return if (Strings.isEmpty(shortcut))
        ""
      else String.format(
        "&nbsp;&nbsp;<font color=\"%s\">%s</font>",
        ColorUtil.toHtmlColor(JBUI.CurrentTheme.Tooltip.shortcutForeground()),
        shortcut,
      )
    }

    private fun linksEqual(o1: ActionLink?, o2: ActionLink?): Boolean {
      if (o1 == null || o2 == null) return o1 === o2
      if (o1.javaClass != o2.javaClass) return false
      if (o1.text != o2.text) return false

      if (o1 is MyActionLink && o2 is MyActionLink) {
        return o1.external == o2.external &&  // do not require equals/hashCode for Runnables
               o1.linkAction.javaClass == o2.linkAction.javaClass
      }
      else if (o1 is BrowserLink && o2 is BrowserLink) {
        return o1.url == o2.url
      }
      return o1 === o2
    }
  }
}
