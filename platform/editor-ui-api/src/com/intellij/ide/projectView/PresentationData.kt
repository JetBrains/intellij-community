// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView

import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationWithSeparator
import com.intellij.navigation.LocationPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.update.ComparableObject
import com.intellij.util.ui.update.ComparableObjectCheck
import java.awt.Color
import java.awt.Font
import javax.swing.Icon

/**
 * Default implementation of the [ItemPresentation] interface.
 */
open class PresentationData : ColoredItemPresentation, ComparableObject, LocationPresentation, Cloneable {
  var coloredText: MutableList<PresentableNodeDescriptor.ColoredFragment> = ContainerUtil.createLockFreeCopyOnWriteList()
    private set

  var background: Color? = null
  private var icon: Icon? = null

  private var locationString: @NlsSafe String? = null
  private var presentableText: @NlsSafe String? = null

  var tooltip: @NlsContexts.Tooltip String? = null
  private var attributesKey: TextAttributesKey? = null

  var forcedTextForeground: Color? = null

  private var font: Font? = null

  private var separatorAbove = false

  var isChanged: Boolean = false
  private var locationPrefix: @NlsSafe String? = null
  private var locationSuffix: @NlsSafe String? = null

  /**
   * Creates an instance with the specified parameters.
   *
   * @param presentableText the name of the object to be presented in most renderers across the program.
   * @param locationString  the location of the object (for example, the package of a class). The location
   * string is used by some renderers and usually displayed as grayed text next to
   * the item name.
   * @param icon            the icon shown for the node when it is collapsed in a tree or when it is displayed
   * in a non-tree view.
   * @param attributesKey   the attributes for rendering the item text.
   */
  constructor(
    presentableText: @NlsSafe String?,
    locationString: @NlsSafe String?,
    icon: Icon?,
    attributesKey: TextAttributesKey?
  ) {
    this.icon = icon
    this.locationString = locationString
    this.presentableText = presentableText
    this.attributesKey = attributesKey
  }

  /**
   * Creates an instance with no parameters specified.
   */
  constructor()

  override fun getIcon(open: Boolean): Icon? = icon

  override fun getLocationString(): String? = locationString

  override fun getPresentableText(): String? = presentableText

  fun setIcon(icon: Icon?) {
    this.icon = icon
  }

  /**
   * Sets the location of the object (for example, the package of a class). The location
   * string is used by some renderers and usually displayed as grayed text next to the item name.
   *
   * @param locationString the location of the object.
   */
  fun setLocationString(locationString: @NlsSafe String?) {
    this.locationString = locationString
  }

  /**
   * Sets the name of the object to be presented in most renderers across the program.
   *
   * @param presentableText the name of the object.
   */
  fun setPresentableText(presentableText: @NlsSafe String?) {
    this.presentableText = presentableText
  }

  /**
   * Copies the presentation parameters from the specified presentation instance.
   *
   * @param presentation the instance to copy the parameters from.
   */
  fun updateFrom(presentation: ItemPresentation) {
    if (presentation is PresentationData) {
      background = presentation.background
      for (fragment in presentation.coloredText) {
        addText(fragment)
      }
    }
    setIcon(presentation.getIcon(false))
    presentableText = presentation.getPresentableText()
    locationString = presentation.locationString
    if (presentation is ColoredItemPresentation) {
      setAttributesKey(presentation.getTextAttributesKey())
    }
    setSeparatorAbove(presentation is ItemPresentationWithSeparator)
    if (presentation is LocationPresentation) {
      locationPrefix = presentation.getLocationPrefix()
      locationSuffix = presentation.getLocationSuffix()
    }
  }

  fun hasSeparatorAbove(): Boolean = separatorAbove

  fun setSeparatorAbove(b: Boolean) {
    separatorAbove = b
  }

  override fun getTextAttributesKey(): TextAttributesKey? = attributesKey

  /**
   * Sets the attributes for rendering the item text.
   *
   * @param attributesKey the attributes for rendering the item text.
   */
  fun setAttributesKey(attributesKey: TextAttributesKey?) {
    this.attributesKey = attributesKey
  }

  fun addText(coloredFragment: PresentableNodeDescriptor.ColoredFragment) {
    coloredText.add(coloredFragment)
  }

  fun addText(text: @NlsContexts.Label String?, attributes: SimpleTextAttributes?) {
    coloredText.add(PresentableNodeDescriptor.ColoredFragment(text, attributes))
  }

  fun clearText() {
    coloredText.clear()
  }

  open fun clear() {
    background = null
    icon = null
    clearText()
    attributesKey = null
    font = null
    forcedTextForeground = null
    locationString = null
    presentableText = null
    tooltip = null
    isChanged = false
    separatorAbove = false
    locationSuffix = null
    locationPrefix = null
  }

  override fun getEqualityObjects(): Array<Any?> {
    return arrayOf(background, icon, coloredText, attributesKey, font, forcedTextForeground, presentableText,
                   locationString, separatorAbove, locationPrefix, locationSuffix)
  }

  override fun hashCode(): Int {
    return ComparableObjectCheck.hashCode(this, super.hashCode())
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) {
      return true
    }
    if (other == null || other.javaClass != javaClass) {
      return false
    }
    return ComparableObjectCheck.equals(this, other)
  }

  open fun copyFrom(from: PresentationData) {
    if (from === this) {
      return
    }

    background = from.background
    attributesKey = from.attributesKey
    icon = from.icon
    clearText()
    coloredText.addAll(from.coloredText)
    font = from.font
    forcedTextForeground = from.forcedTextForeground
    locationString = from.locationString
    presentableText = from.presentableText
    tooltip = from.tooltip
    separatorAbove = from.separatorAbove
    locationPrefix = from.locationPrefix
    locationSuffix = from.locationSuffix
  }

  public override fun clone(): PresentationData {
    val clone = super.clone() as PresentationData
    clone.coloredText = ContainerUtil.createLockFreeCopyOnWriteList(coloredText)
    return clone
  }

  open fun applyFrom(from: PresentationData) {
    background = background ?: from.background
    attributesKey = attributesKey ?: from.attributesKey
    icon = icon ?: from.icon

    if (coloredText.isEmpty()) {
      coloredText.addAll(from.coloredText)
    }

    font = font ?: from.font
    forcedTextForeground = forcedTextForeground ?: from.forcedTextForeground
    locationString = locationString ?: from.locationString
    presentableText = presentableText ?: from.presentableText
    tooltip = tooltip ?: from.tooltip
    separatorAbove = separatorAbove || from.separatorAbove
    locationPrefix = locationPrefix ?: from.locationPrefix
    locationSuffix = locationSuffix ?: from.locationSuffix
  }

  override fun getLocationPrefix(): String = locationPrefix ?: FontUtil.spaceAndThinSpace()

  override fun getLocationSuffix(): String = locationSuffix ?: ""
}
