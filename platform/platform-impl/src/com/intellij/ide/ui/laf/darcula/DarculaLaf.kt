// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.laf.darcula

import com.intellij.ide.ui.laf.createBaseLaF
import com.intellij.ide.ui.laf.createRawDarculaTheme
import com.intellij.ide.ui.laf.initBaseLaF
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.LookAndFeel
import javax.swing.UIDefaults
import javax.swing.plaf.basic.BasicLookAndFeel

/**
 * Do not use.
 *
 * @author Konstantin Bulenkov
 */
open class DarculaLaf : BasicLookAndFeel() {
  private var base: LookAndFeel? = null

  companion object {
    const val NAME: @NlsSafe String = "Darcula"
  }

  override fun getDefaults(): UIDefaults {
    try {
      val defaults = base!!.defaults
      initBaseLaF(defaults)

      createRawDarculaTheme().applyTheme(defaults = defaults)

      defaults.put("ui.theme.is.dark", true)
      return defaults
    }
    catch (e: Exception) {
      thisLogger().error(e)
    }
    return super.getDefaults()
  }

  override fun getName() = getID()

  override fun getID() = NAME

  override fun getDescription() = "IntelliJ Dark Look and Feel"

  override fun isNativeLookAndFeel() = true

  override fun isSupportedLookAndFeel() = true

  override fun initialize() {
    try {
      if (base == null) {
        base = createBaseLaF()
      }
      base!!.initialize()
    }
    catch (e: Throwable) {
      thisLogger().error(e)
    }
  }

  override fun uninitialize() {
    try {
      base?.uninitialize()
    }
    catch (ignore: Exception) {
    }
  }

  override fun loadSystemColors(defaults: UIDefaults, systemColors: Array<String>, useNative: Boolean) {
    try {
      val superMethod = BasicLookAndFeel::class.java.getDeclaredMethod("loadSystemColors",
                                                                       UIDefaults::class.java,
                                                                       Array<String>::class.java,
                                                                       Boolean::class.javaPrimitiveType)
      superMethod.setAccessible(true)
      // invoke method on a base LaF, not on our instance
      superMethod.invoke(base, defaults, systemColors, useNative)
    }
    catch (e: Exception) {
      thisLogger().error(e)
    }
  }

  override fun getDisabledIcon(component: JComponent?, icon: Icon?): Icon? = icon?.let { IconLoader.getDisabledIcon(it) }

  override fun getSupportsWindowDecorations() = true
}

