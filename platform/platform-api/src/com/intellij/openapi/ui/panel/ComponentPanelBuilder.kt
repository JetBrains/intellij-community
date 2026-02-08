// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.panel

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.EditorTextComponent
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.RelativeFont
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.Border
import javax.swing.event.HyperlinkListener
import javax.swing.plaf.LabelUI

@Deprecated(
  """Provides incorrect spacing between components and out-dated. The functionality is covered by Kotlin UI DSL,
  which should be used instead. ComponentPanelBuilder will be removed after moving Kotlin UI DSL into platform API package""")
open class ComponentPanelBuilder(private val myComponent: JComponent) : GridBagPanelBuilder {

  private var myLabelText: @NlsContexts.Label String? = null
  private var myLabelOnTop = false
  private var myCommentText: @NlsContexts.DetailedDescription String? = null
  private var myCommentIcon: Icon? = null
  private var myHyperlinkListener: HyperlinkListener = BrowserHyperlinkListener.INSTANCE
  private var myCommentBelow = true
  private var myCommentAllowAutoWrapping = true
  private var myHTDescription: @NlsContexts.Tooltip String? = null
  private var myHTLinkText: @NlsContexts.LinkLabel String? = null
  private var myHTAction: Runnable? = null
  private var myTopRightComponent: JComponent? = null
  private var myAnchor = UI.Anchor.Center
  private var myResizeY = false
  private var myResizeX = true
  private var valid = true

  /**
   * Allow resizing component vertically when the panel is resized. Useful when [javax.swing.JTextArea] or
   * [javax.swing.JTextPane] need to be resized along with the dialog window.
   * 
   * @param resize `true` to enable resize, `false` to disable. Default is `false`
   * @return `this`
   */
  open fun resizeY(resize: Boolean): ComponentPanelBuilder {
    myResizeY = resize
    return this
  }

  /**
   * Allow resizing component horizontally when the panel is resized. Useful for
   * limiting [JComboBox] and other resizable component to preferred width.
   * 
   * @param resize `true` to enable resize, `false` to disable. Default is `true`
   * @return `this`
   */
  open fun resizeX(resize: Boolean): ComponentPanelBuilder {
    myResizeX = resize
    return this
  }

  /**
   * @param labelText text for the label.
   * @return `this`
   */
  open fun withLabel(labelText: @NlsContexts.Label String): ComponentPanelBuilder {
    myLabelText = labelText
    return this
  }

  /**
   * Move label on top of the owner component. Default position is on the left of the owner component.
   * 
   * @return `this`
   */
  open fun moveLabelOnTop(): ComponentPanelBuilder {
    myLabelOnTop = true
    valid = StringUtil.isEmpty(myCommentText) || StringUtil.isEmpty(myHTDescription)
    return this
  }

  open fun anchorLabelOn(anchor: UI.Anchor): ComponentPanelBuilder {
    myAnchor = anchor
    return this
  }

  /**
   * @param comment help context styled text written below the owner component.
   * @return `this`
   */
  open fun withComment(@NlsContexts.DetailedDescription comment: @NlsContexts.DetailedDescription String): ComponentPanelBuilder {
    return withComment(comment, true)
  }

  open fun withComment(
    comment: @NlsContexts.DetailedDescription String,
    allowAutoWrapping: Boolean,
  ): ComponentPanelBuilder {
    myCommentText = comment
    myCommentAllowAutoWrapping = allowAutoWrapping
    valid = StringUtil.isEmpty(comment) || StringUtil.isEmpty(myHTDescription)
    return this
  }

  open fun withCommentIcon(icon: Icon): ComponentPanelBuilder {
    myCommentIcon = icon
    return this
  }

  /**
   * Sets the hyperlink listener to be executed on clicking any reference in comment
   * text. Reference is represented by the HTML `<a href>` tags.
   * By default `BrowserHyperlinkListener.INSTANCE` is used which opens
   * a web browser.
   * @param listener new `HyperlinkListener`
   * @return `this`
   */
  open fun withCommentHyperlinkListener(listener: HyperlinkListener): ComponentPanelBuilder {
    myHyperlinkListener = listener
    return this
  }

  /**
   * Adds a custom (one line) component to the top right location of the main component.
   * Useful for adding control like [com.intellij.ui.components.labels.LinkLabel] or
   * [com.intellij.ui.components.DropDownLink]
   * 
   * @param topRightComponent the component to be added
   * @return `this`
   */
  open fun withTopRightComponent(topRightComponent: JComponent): ComponentPanelBuilder {
    myTopRightComponent = topRightComponent
    valid = StringUtil.isEmpty(myCommentText) || StringUtil.isEmpty(myHTDescription)
    return this
  }

  /**
   * Move comment to the right of the owner component. Default position is below the owner component.
   * 
   * @return `this`
   */
  open fun moveCommentRight(): ComponentPanelBuilder {
    myCommentBelow = false
    return this
  }

  /**
   * Enables the help tooltip icon on the right of the owner component and sets the description text for the tooltip.
   * 
   * @param description help tooltip description.
   * @return `this`
   */
  open fun withTooltip(description: @NlsContexts.Tooltip String): ComponentPanelBuilder {
    myHTDescription = description
    valid = StringUtil.isEmpty(myCommentText) || StringUtil.isEmpty(description)
    return this
  }

  /**
   * Sets optional help tooltip link and link action.
   * 
   * @param linkText help tooltip link text.
   * 
   * @param action help tooltip link action.
   * 
   * @return `this`
   */
  open fun withTooltipLink(linkText: @NlsContexts.LinkLabel String, action: Runnable): ComponentPanelBuilder {
    myHTLinkText = linkText
    myHTAction = action
    return this
  }

  override fun createPanel(): JPanel {
    val panel: JPanel
    if (UIUtil.getDeprecatedBackground() == null) {
      panel = NonOpaquePanel(GridBagLayout())
    }
    else {
      panel = JPanel(GridBagLayout())
      UIUtil.applyDeprecatedBackground(panel)
      UIUtil.applyDeprecatedBackground(myComponent)
    }
    val gc = GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                null, 0, 0)
    addToPanel(panel, gc, false)
    return panel
  }

  override fun constrainsValid(): Boolean {
    return valid
  }

  override fun gridWidth(): Int {
    return if (myCommentBelow) 2 else if (myResizeX) 4 else 3
  }

  override fun addToPanel(panel: JPanel, gc: GridBagConstraints, splitColumns: Boolean) {
    if (constrainsValid()) {
      ComponentPanelImpl(splitColumns).addToPanel(panel, gc)
    }
  }

  private fun getCommentBorder(): Border {
    if (StringUtil.isNotEmpty(myCommentText)) {
      return JBEmptyBorder(computeCommentInsets(myComponent, myCommentBelow))
    }
    else {
      return JBUI.Borders.empty()
    }
  }

  private open class CommentLabel(@NlsContexts.Label text: @NlsContexts.Label String) : JBLabel(text) {

    init {
      setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND)
    }

    override fun setUI(ui: LabelUI?) {
      super.setUI(ui)
      setFont(getCommentFont(getFont()))
    }
  }

  private inner class ComponentPanelImpl(private val splitColumns: Boolean) : ComponentPanel() {

    private val label: JLabel
    private val comment: JLabel

    init {
      if ((StringUtil.isNotEmpty(myLabelText))) {
        label = JLabel()
        LabeledComponent.TextWithMnemonic.fromTextWithMnemonic(myLabelText).setToLabel(label)
        label.setLabelFor(myComponent)
      }
      else {
        label = JLabel("")
      }

      comment = createCommentComponent(
        {
          object : CommentLabel("") {
            override fun createHyperlinkListener(): HyperlinkListener {
              return myHyperlinkListener
            }
          }
        }, myCommentText, myCommentBelow, MAX_COMMENT_WIDTH, myCommentAllowAutoWrapping)

      if (myCommentIcon != null) {
        comment.setIcon(myCommentIcon)
      }

      comment.setBorder(getCommentBorder())
    }

    override fun getCommentText(): String? {
      return myCommentText
    }

    override fun setCommentText(commentText: String?) {
      if (!StringUtil.equals(myCommentText, commentText)) {
        myCommentText = commentText
        setCommentTextImpl(commentText)
      }
    }

    open fun setCommentTextImpl(commentText: String?) {
      setCommentText(comment, commentText, myCommentBelow, MAX_COMMENT_WIDTH)
    }

    open fun addToPanel(panel: JPanel, gc: GridBagConstraints) {
      gc.gridx = 0
      gc.gridwidth = 1
      gc.weightx = 0.0
      gc.anchor = GridBagConstraints.LINE_START

      if (StringUtil.isNotEmpty(myLabelText)) {
        if (myLabelOnTop || myTopRightComponent != null) {
          gc.insets = JBUI.insetsBottom(4)
          gc.gridx = 1

          val topPanel = JPanel()
          topPanel.setLayout(BoxLayout(topPanel, BoxLayout.X_AXIS))
          if (myLabelOnTop) {
            topPanel.add(label)
          }

          if (myTopRightComponent != null) {
            topPanel.add(Box.Filler(JBUI.size(UIUtil.DEFAULT_HGAP, 0),
                                    JBUI.size(UIUtil.DEFAULT_HGAP, 0),
                                    JBUI.size(Int.MAX_VALUE)))
            topPanel.add(myTopRightComponent)
          }

          panel.add(topPanel, gc)
          gc.gridy++
        }

        if (!myLabelOnTop) {
          gc.gridx = 0
          when (myAnchor) {
            UI.Anchor.Top -> {
              gc.anchor = GridBagConstraints.PAGE_START
              gc.insets = JBUI.insets(4, 0, 0, 8)
            }
            UI.Anchor.Center -> {
              gc.anchor = GridBagConstraints.LINE_START
              gc.insets = JBUI.insetsRight(8)
            }
            UI.Anchor.Bottom -> {
              gc.anchor = GridBagConstraints.PAGE_END
              gc.insets = JBUI.insets(0, 0, 4, 8)
            }
          }
          panel.add(label, gc)
        }
      }

      gc.gridx += if (myLabelOnTop) 0 else 1
      gc.weightx = 1.0
      gc.insets = JBInsets.emptyInsets()
      gc.fill = if (myResizeY) GridBagConstraints.BOTH else if (myResizeX) GridBagConstraints.HORIZONTAL else GridBagConstraints.NONE
      gc.weighty = if (myResizeY) 1.0 else 0.0

      if (splitColumns) {
        panel.add(myComponent, gc)
      }

      if (StringUtil.isNotEmpty(myHTDescription) || !myCommentBelow) {
        val componentPanel = JPanel()
        componentPanel.setLayout(BoxLayout(componentPanel, BoxLayout.X_AXIS))

        if (!splitColumns) {
          componentPanel.add(myComponent)
        }

        if (StringUtil.isNotEmpty(myHTDescription)) {
          val lbl = if (StringUtil.isNotEmpty(myHTLinkText) && myHTAction != null) ContextHelpLabel.createWithLink(null, myHTDescription!!,
                                                                                                                   myHTLinkText!!,
                                                                                                                   myHTAction!!)
          else ContextHelpLabel.create(myHTDescription!!)
          JBUI.Borders.emptyLeft(7).wrap<ContextHelpLabel?>(lbl)
          componentPanel.add(lbl)

          ComponentValidator.getInstance(myComponent).ifPresent(Consumer { _ ->
            val iconLabel = JLabel()
            JBUI.Borders.emptyLeft(7).wrap<JLabel?>(iconLabel)
            iconLabel.setVisible(false)
            componentPanel.add(iconLabel)

            iconLabel.addMouseListener(object : MouseAdapter() {
              override fun mouseEntered(e: MouseEvent) {
                myComponent.dispatchEvent(convertMouseEvent(e))
                e.consume()
              }

              override fun mouseExited(e: MouseEvent) {
                myComponent.dispatchEvent(convertMouseEvent(e))
                e.consume()
              }
            })
            myComponent.addPropertyChangeListener("JComponent.outline", PropertyChangeListener { evt: PropertyChangeEvent? ->
              if (evt!!.getNewValue() == null) {
                iconLabel.setVisible(false)
                lbl.setVisible(true)
              }
              else if ("warning" == evt.getNewValue()) {
                iconLabel.setIcon(AllIcons.General.BalloonWarning)
                iconLabel.setVisible(true)
                lbl.setVisible(false)
              }
              else if ("error" == evt.getNewValue()) {
                iconLabel.setIcon(AllIcons.General.BalloonError)
                iconLabel.setVisible(true)
                lbl.setVisible(false)
              }
              componentPanel.revalidate()
              componentPanel.repaint()
            })
          })

          panel.add(componentPanel, gc)
        }
        else if (!myCommentBelow) {
          if (splitColumns) {
            gc.gridx++
            gc.weightx = 0.0
            gc.fill = GridBagConstraints.NONE
            gc.weighty = 0.0
            panel.add(comment, gc)
          }
          else {
            comment.setBorder(getCommentBorder())
            componentPanel.add(comment)
            panel.add(componentPanel, gc)
          }
        }
      }
      else if (!splitColumns) {
        panel.add(myComponent, gc)
      }

      if (!splitColumns && !myResizeX) {
        gc.gridx++
        gc.weightx = 1.0
        gc.fill = GridBagConstraints.REMAINDER
        panel.add(JPanel(), gc)
      }

      gc.fill = GridBagConstraints.HORIZONTAL
      gc.weighty = 0.0
      if (myCommentBelow) {
        gc.gridx = 1
        gc.gridy++
        gc.weightx = 0.0
        gc.anchor = GridBagConstraints.NORTHWEST
        gc.insets = JBInsets.emptyInsets()

        comment.setBorder(getCommentBorder())
        panel.add(comment, gc)

        if (!myResizeX) {
          gc.gridx++
          gc.weightx = 1.0
          gc.fill = GridBagConstraints.REMAINDER
          panel.add(JPanel(), gc)
        }
      }

      myComponent.putClientProperty(DECORATED_PANEL_PROPERTY, this)
      gc.gridy++
    }

    fun convertMouseEvent(e: MouseEvent): MouseEvent {
      val p = e.getPoint()
      SwingUtilities.convertPoint(e.getComponent(), p, myComponent)
      return MouseEvent(myComponent, e.getID(), e.getWhen(), e.getModifiers(),
                        p.x, p.y, e.getXOnScreen(), e.getYOnScreen(),
                        e.getClickCount(), e.isPopupTrigger(), e.getButton())
    }
  }

  companion object {

    const val MAX_COMMENT_WIDTH: Int = 70

    @JvmStatic
    fun computeCommentInsets(component: JComponent, commentBelow: Boolean): Insets {
      val isMacDefault = UIUtil.isUnderDefaultMacTheme()
      val isWin10 = UIUtil.isUnderWin10LookAndFeel()

      if (commentBelow) {
        var top = 8
        var left = 2
        var bottom = 0

        if (component is JRadioButton || component is JCheckBox) {
          bottom = if (isWin10) 10 else if (isMacDefault) 8 else 9
          if (component is JCheckBox) {
            left = UIUtil.getCheckBoxTextHorizontalOffset(component) // the value returned from this method is already scaled

            return Insets(0, left, JBUIScale.scale(bottom), 0)
          }
          else {
            left = if (isMacDefault) 26 else if (isWin10) 17 else 23
          }
        }
        else if (component is JTextField || component is EditorTextComponent ||
                 component is JComboBox<*> || component is ComponentWithBrowseButton<*>
        ) {
          top = if (isWin10) 3 else 4
          left = if (isWin10) 2 else if (isMacDefault) 5 else 4
          bottom = if (isWin10) 10 else if (isMacDefault) 8 else 9
        }
        else if (component is JButton) {
          top = if (isWin10) 2 else 4
          left = if (isWin10) 2 else if (isMacDefault) 5 else 4
          bottom = 0
        }

        return JBUI.insets(top, left, bottom, 0)
      }
      else {
        var left = 14

        if (component is JRadioButton || component is JCheckBox) {
          left = if (isMacDefault) 8 else 13
        }
        else if (component is JTextField || component is EditorTextComponent ||
                 component is JComboBox<*> || component is ComponentWithBrowseButton<*>
        ) {
          left = if (isMacDefault) 13 else 14
        }
        return JBUI.insetsLeft(left)
      }
    }

    @JvmStatic
    fun createCommentComponent(
      commentText: @NlsContexts.DetailedDescription String?,
      isCommentBelow: Boolean,
    ): JLabel {
      return createCommentComponent(commentText, isCommentBelow, MAX_COMMENT_WIDTH, true)
    }

    @JvmStatic
    fun createCommentComponent(
      commentText: @NlsContexts.DetailedDescription String?,
      isCommentBelow: Boolean,
      maxLineLength: Int,
    ): JLabel {
      return createCommentComponent(commentText, isCommentBelow, maxLineLength, true)
    }

    @JvmStatic
    fun createCommentComponent(
      commentText: @NlsContexts.DetailedDescription String?,
      isCommentBelow: Boolean,
      maxLineLength: Int,
      allowAutoWrapping: Boolean,
    ): JLabel {
      return createCommentComponent(Supplier { CommentLabel("") }, commentText, isCommentBelow, maxLineLength, allowAutoWrapping)
    }

    private fun createCommentComponent(
      labelSupplier: Supplier<out JBLabel?>,
      commentText: @NlsContexts.DetailedDescription String?,
      isCommentBelow: Boolean,
      maxLineLength: Int,
      allowAutoWrapping: Boolean,
    ): JLabel {
      // todo why our JBLabel cannot render html if render panel without frame (test only)
      val isCopyable = SystemProperties.getBooleanProperty("idea.ui.comment.copyable", true)
      val component: JLabel = labelSupplier.get().setCopyable(isCopyable).setAllowAutoWrapping(allowAutoWrapping)

      component.setVerticalTextPosition(SwingConstants.TOP)
      component.setFocusable(false)

      if (isCopyable) {
        setCommentText(component, commentText, isCommentBelow, maxLineLength)
      }
      else {
        component.setText(commentText)
      }
      return component
    }

    @JvmStatic
    fun createNonWrappingCommentComponent(@NlsContexts.DetailedDescription commentText: @NlsContexts.DetailedDescription String): JLabel {
      return CommentLabel(commentText)
    }

    @JvmStatic
    fun getCommentFont(font: Font?): Font {
      if (isNewUI()) {
        return JBFont.medium()
      }
      else {
        return RelativeFont.NORMAL.fromResource("ContextHelp.fontSizeOffset", -2).derive(font)
      }
    }

    private fun setCommentText(
      component: JLabel,
      commentText: @NlsContexts.DetailedDescription String?,
      isCommentBelow: Boolean,
      maxLineLength: Int,
    ) {
      if (commentText != null) {
        @NonNls val css: String = "<head><style type=\"text/css\">\n" +
                                  "a, a:link {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED) + ";}\n" +
                                  "a:visited {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.VISITED) + ";}\n" +
                                  "a:hover {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.HOVERED) + ";}\n" +
                                  "a:active {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.PRESSED) + ";}\n" +
                                  //"body {background-color:#" + ColorUtil.toHex(JBColor.YELLOW) + ";}\n" + // Left for visual debugging
                                  "</style>\n</head>"
        var text = HtmlChunk.raw(commentText)
        if (maxLineLength > 0 && commentText.length > maxLineLength && isCommentBelow) {
          val width = component.getFontMetrics(component.getFont()).stringWidth(commentText.substring(0, maxLineLength))
          text = text.wrapWith(HtmlChunk.div().attr("width", width))
        }
        else {
          text = text.wrapWith(HtmlChunk.div())
        }
        component.setText(HtmlBuilder()
                            .append(HtmlChunk.raw(css))
                            .append(text.wrapWith("body"))
                            .wrapWith("html")
                            .toString())
      }
    }
  }
}
