// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Font
import java.awt.Toolkit
import javax.swing.plaf.FontUIResource

@get:Internal
val isWindowsSystemFontWithoutFallbackLaf: Boolean
  get() = EarlyAccessRegistryManager.getBoolean("ide.windows.system.font.without.fallback.laf")

/**
 * The Windows message box font as a plain font, read from the desktop property that the toolkit fills at its creation.
 * A plain font does not resolve its font file, so the font manager stays uninitialized until the look and feel manager
 * applies the fonts with the fallback. Returns `null` when the toolkit reports no font.
 */
@Internal
fun readWindowsSystemFont(): FontUIResource? {
  val font = Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font") as? Font ?: return null
  return FontUIResource(font.name, Font.PLAIN, font.size)
}
