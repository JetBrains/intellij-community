// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.diagnostic.runActivity
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.lang.invoke.MethodHandles
import java.io.IOException
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import javax.swing.plaf.FontUIResource

private val LOG = fileLogger()

@get:Internal
val isLinuxSystemFontWithoutGtkLaf: Boolean
  get() = EarlyAccessRegistryManager.getBoolean("ide.linux.system.font.without.gtk.laf")

/**
 * Whether a start takes the system font that the previous start stored, see [readCachedLinuxSystemFont].
 * The first read of a `gnome.*` desktop property initializes the desktop settings, which takes long without a settings daemon.
 */
@get:Internal
val isLinuxSystemFontCached: Boolean
  get() = System.getProperty("ide.linux.system.font.cache").toBoolean()

/**
 * The system font as `GTKLookAndFeel` resolves it, read from XSETTINGS or from the GTK settings without the look and feel.
 * Returns `null` when neither reports a font. May run on any thread.
 */
@Internal
fun readLinuxSystemFont(): FontUIResource? {
  val settings = readSystemFontSettings() ?: return null
  if (isLinuxSystemFontCached) {
    writeSystemFontCache(settings)
  }
  return applySystemFontSettings(settings)
}

/**
 * The system font that the previous start stored, or `null` without a stored font.
 * The stored dpi becomes the `gnome.Xft/DPI` desktop property, so a later read of it does not initialize the desktop settings.
 * The caller refreshes the stored font with [refreshLinuxSystemFontCache] off the startup path.
 */
@Internal
fun readCachedLinuxSystemFont(): FontUIResource? {
  val settings = readSystemFontCache() ?: return null
  return applySystemFontSettings(settings)
}

/**
 * Reads the live desktop settings and stores them for the next start. The current session keeps the font it started with.
 */
@Internal
fun refreshLinuxSystemFontCache() {
  readSystemFontSettings()?.let(::writeSystemFontCache)
}

private class SystemFontSettings(@JvmField val fontName: String, @JvmField val xftDpi: Int?, @JvmField val xftDpiFromDesktop: Boolean)

private fun readSystemFontSettings(): SystemFontSettings? {
  val toolkit = Toolkit.getDefaultToolkit()
  var fontName = runActivity("desktop properties reading") { toolkit.getDesktopProperty("gnome.Gtk/FontName") as? String }
  var xftDpi = toolkit.getDesktopProperty("gnome.Xft/DPI") as? Int
  val xftDpiFromDesktop = xftDpi != null
  if (fontName == null || xftDpi == null) {
    // no settings daemon: on X11 GTK takes settings.ini, then its own defaults; on Wayland it reads GSettings, so ask GTK there
    val gtkSettings = if (StartupUiUtil.isXToolkit()) readGtkSettingsIni() else readGtkSettings()
    fontName = fontName ?: gtkSettings?.fontName ?: if (StartupUiUtil.isXToolkit()) "Sans 10" else return null
    if (xftDpi == null) {
      // GTK assumes 96 dpi when the Xft.dpi resource is absent
      xftDpi = gtkSettings?.xftDpi ?: if (StartupUiUtil.isXToolkit()) (xrmXftDpi() ?: 96) * 1024 else null
    }
  }
  return SystemFontSettings(fontName = fontName, xftDpi = xftDpi, xftDpiFromDesktop = xftDpiFromDesktop)
}

private fun applySystemFontSettings(settings: SystemFontSettings): FontUIResource {
  val xftDpi = settings.xftDpi
  if (xftDpi != null) {
    if (!settings.xftDpiFromDesktop) {
      // the IDE reads this property to undo the dpi scaling of the label font, see JBUIScale.computeSystemFontData
      setXftDpiDesktopProperty(Toolkit.getDefaultToolkit(), xftDpi)
    }
    if (StartupUiUtil.isXToolkit()) {
      setXftDpi(normalizeDpi(xftDpi))
    }
  }
  return runActivity("system font creation") { parsePangoFont(settings.fontName, xftDpi) }
}

private fun systemFontCacheFile(): Path = PathManager.getSystemDir().resolve("linux-system-font.txt")

private fun readSystemFontCache(): SystemFontSettings? {
  val file = systemFontCacheFile()
  try {
    if (!Files.isRegularFile(file)) {
      return null
    }
    val lines = Files.readAllLines(file)
    val fontName = lines.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return SystemFontSettings(fontName = fontName, xftDpi = lines.getOrNull(1)?.trim()?.toIntOrNull(), xftDpiFromDesktop = false)
  }
  catch (e: IOException) {
    LOG.warn("Cannot read $file", e)
    return null
  }
}

private fun writeSystemFontCache(settings: SystemFontSettings) {
  val file = systemFontCacheFile()
  try {
    Files.createDirectories(file.parent)
    Files.writeString(file, "${settings.fontName}\n${settings.xftDpi ?: ""}\n")
  }
  catch (e: IOException) {
    LOG.warn("Cannot write $file", e)
  }
}

// a port of com.sun.java.swing.plaf.gtk.PangoFonts.lookupFont
@VisibleForTesting
internal fun parsePangoFont(pangoName: String, xftDpi: Int?, uiScaleEnabled: (dpi: Int) -> Boolean = ::isUiScaleEnabled): FontUIResource {
  var family = ""
  var style = Font.PLAIN
  var size = 10
  for (word in pangoName.split(' ', '\t').filter { it.isNotEmpty() }) {
    when {
      word.equals("italic", ignoreCase = true) -> style = style or Font.ITALIC
      word.equals("bold", ignoreCase = true) -> style = style or Font.BOLD
      word[0].isDigit() -> word.toIntOrNull()?.let { size = it }
      else -> family = if (family.isEmpty()) word else "$family $word"
    }
  }

  val pointSize = if (xftDpi == null) {
    size * screenFontScale()
  }
  else {
    var dpi = xftDpi / 1024
    if (dpi == -1 || uiScaleEnabled(dpi)) {
      dpi = 96
    }
    if (dpi < 50) {
      dpi = 50
    }
    dpi * size / 72.0
  }

  val javaFamily = when (family.lowercase()) {
    "sans", "sans-serif" -> "SansSerif"
    "serif" -> "Serif"
    "monospace" -> "Monospaced"
    else -> family
  }
  // the base look and feel needs the name and the size only; the font manager resolves the family on the first use
  val fontSize = pointSize.toFloat().coerceAtLeast(1f)
  return FontUIResource(Font(javaFamily, style, (fontSize + 0.5f).toInt()).deriveFont(fontSize))
}

private fun normalizeDpi(xftDpi: Int): Int {
  var dpi = xftDpi / 1024
  if (dpi == -1) {
    dpi = 96
  }
  return dpi.coerceAtLeast(50)
}

private fun screenFontScale(): Double {
  if (GraphicsEnvironment.isHeadless()) {
    return 1.0
  }
  val gc = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
  return gc.normalizingTransform.scaleY
}

private class GtkSettings(val fontName: String?, val xftDpi: Int?)

private fun readGtkSettingsIni(): GtkSettings? {
  val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotEmpty() }?.let { Path.of(it) }
                   ?: Path.of(System.getProperty("user.home"), ".config")
  val settings = HashMap<String, String>()
  for (file in listOf(Path.of("/etc/gtk-3.0/settings.ini"), configHome.resolve("gtk-3.0/settings.ini"))) {
    try {
      if (Files.isRegularFile(file)) {
        settings.putAll(parseGtkSettingsIni(file.readText()))
      }
    }
    catch (e: IOException) {
      LOG.warn("Cannot read $file", e)
    }
  }
  if (settings.isEmpty()) {
    return null
  }
  return GtkSettings(fontName = settings["gtk-font-name"], xftDpi = settings["gtk-xft-dpi"]?.trim()?.toIntOrNull())
}

@VisibleForTesting
internal fun parseGtkSettingsIni(text: String): Map<String, String> {
  val result = HashMap<String, String>()
  var inSettings = false
  for (rawLine in text.lineSequence()) {
    val line = rawLine.trim()
    when {
      line.isEmpty() || line.startsWith("#") || line.startsWith(";") -> {}
      line.startsWith("[") -> inSettings = line == "[Settings]"
      inSettings -> {
        val index = line.indexOf('=')
        if (index > 0) {
          result[line.substring(0, index).trim()] = line.substring(index + 1).trim().removeSurrounding("\"")
        }
      }
    }
  }
  return result
}

// the Xft.dpi resource, which the JBR reads once for the device scale
private fun xrmXftDpi(): Int? {
  return try {
    val deviceClass = Class.forName("sun.awt.X11GraphicsDevice")
    val dpi = MethodHandles.privateLookupIn(deviceClass, MethodHandles.lookup()).findStaticGetter(deviceClass, "xrmXftDpi", Int::class.javaPrimitiveType).invoke() as Int
    dpi.takeIf { it > 0 }
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot read Xft.dpi from the X11 graphics device", e)
    null
  }
}

private fun readGtkSettings(): GtkSettings? {
  try {
    val toolkit = Toolkit.getDefaultToolkit()
    val toolkitClass = Class.forName("sun.awt.UNIXToolkit")
    if (!toolkitClass.isInstance(toolkit)) {
      return null
    }
    val lookup = MethodHandles.lookup()
    val loadGtk = MethodHandles.privateLookupIn(toolkitClass, lookup).findVirtual(toolkitClass, "loadGTK", MethodType.methodType(Boolean::class.javaPrimitiveType))
    if (loadGtk.invoke(toolkit) != true) {
      return null
    }

    val engineClass = Class.forName("com.sun.java.swing.plaf.gtk.GTKEngine")
    val settingsClass = engineClass.declaredClasses.first { it.simpleName == "Settings" }
    val engineLookup = MethodHandles.privateLookupIn(engineClass, lookup)
    val engine = engineLookup.findStaticGetter(engineClass, "INSTANCE", engineClass).invoke()
    val getSetting = engineLookup.findVirtual(engineClass, "getSetting", MethodType.methodType(Any::class.java, settingsClass))
    fun setting(name: String): Any? = getSetting.invoke(engine, settingsClass.enumConstants.first { (it as Enum<*>).name == name })
    return GtkSettings(fontName = setting("GTK_FONT_NAME") as? String, xftDpi = setting("GTK_XFT_DPI") as? Int)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot read the GTK settings", e)
    return null
  }
}

// SunGraphicsEnvironment.isUIScaleEnabled(dpi) also decides, once, whether the JRE manages the HiDPI scale on Linux
private fun isUiScaleEnabled(dpi: Int): Boolean {
  return try {
    val method = Class.forName("sun.java2d.SunGraphicsEnvironment").getDeclaredMethod("isUIScaleEnabled", Int::class.javaPrimitiveType)
    method.isAccessible = true
    method.invoke(null, dpi) as Boolean
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot query SunGraphicsEnvironment.isUIScaleEnabled", e)
    false
  }
}

// what GTKLookAndFeel.initialize() does for the device scale of the X11 screens
private fun setXftDpi(dpi: Int) {
  try {
    val deviceClass = Class.forName("sun.awt.X11GraphicsDevice")
    MethodHandles.privateLookupIn(deviceClass, MethodHandles.lookup())
      .findStatic(deviceClass, "setXftDpi", MethodType.methodType(Void.TYPE, Int::class.javaPrimitiveType))
      .invoke(dpi)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot set Xft.dpi on the X11 graphics devices", e)
  }
}

private fun setXftDpiDesktopProperty(toolkit: Toolkit, xftDpi: Int) {
  try {
    val method = Toolkit::class.java.getDeclaredMethod("setDesktopProperty", String::class.java, Any::class.java)
    method.isAccessible = true
    method.invoke(toolkit, "gnome.Xft/DPI", xftDpi)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot set the desktop property gnome.Xft/DPI", e)
  }
}
