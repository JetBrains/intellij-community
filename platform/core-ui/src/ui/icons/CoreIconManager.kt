// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.IconLayerProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.findIconUsingDeprecatedImplementation
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.RowIcon
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.util.BitUtil
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Paint
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Supplier
import javax.swing.Icon

@Suppress("DeprecatedCallableAddReplaceWith")
@ApiStatus.Internal
class CoreIconManager : IconManager, CoreAwareIconManager {
  override fun getPlatformIcon(id: PlatformIcons): Icon {
    return when (id) {
      PlatformIcons.Public -> AllIcons.Nodes.Public
      PlatformIcons.Private -> AllIcons.Nodes.C_private
      PlatformIcons.Protected -> AllIcons.Nodes.C_protected
      PlatformIcons.Local -> AllIcons.Nodes.C_plocal
      PlatformIcons.TodoDefault -> AllIcons.General.TodoDefault
      PlatformIcons.TodoQuestion -> AllIcons.General.TodoQuestion
      PlatformIcons.TodoImportant -> AllIcons.General.TodoImportant
      PlatformIcons.NodePlaceholder -> AllIcons.Nodes.NodePlaceholder
      PlatformIcons.WarningDialog -> AllIcons.General.WarningDialog
      PlatformIcons.Copy -> AllIcons.Actions.Copy
      PlatformIcons.Import -> AllIcons.ToolbarDecorator.Import
      PlatformIcons.Export -> AllIcons.ToolbarDecorator.Export
      PlatformIcons.Stub -> AllIcons.Actions.Stub
      PlatformIcons.TestStateRun -> AllIcons.RunConfigurations.TestState.Run
      PlatformIcons.Package -> AllIcons.Nodes.Package
      PlatformIcons.Folder -> AllIcons.Nodes.Folder
      PlatformIcons.IdeaModule -> AllIcons.Nodes.IdeaModule
      PlatformIcons.TextFileType -> AllIcons.FileTypes.Text
      PlatformIcons.ArchiveFileType -> AllIcons.FileTypes.Archive
      PlatformIcons.UnknownFileType -> AllIcons.FileTypes.Unknown
      PlatformIcons.CustomFileType -> AllIcons.FileTypes.Custom
      PlatformIcons.JavaClassFileType -> AllIcons.FileTypes.JavaClass
      PlatformIcons.JspFileType -> AllIcons.FileTypes.Jsp
      PlatformIcons.JavaFileType -> AllIcons.FileTypes.Java
      PlatformIcons.PropertiesFileType -> AllIcons.FileTypes.Properties
      PlatformIcons.JavaModule -> AllIcons.Nodes.JavaModule
      PlatformIcons.Variable -> AllIcons.Nodes.Variable
      PlatformIcons.Field -> AllIcons.Nodes.Field
      PlatformIcons.Method -> AllIcons.Nodes.Method
      PlatformIcons.Class -> AllIcons.Nodes.Class
      PlatformIcons.AbstractClass -> AllIcons.Nodes.AbstractClass
      PlatformIcons.AbstractException -> AllIcons.Nodes.AbstractException
      PlatformIcons.AnonymousClass -> AllIcons.Nodes.AnonymousClass
      PlatformIcons.Enum -> AllIcons.Nodes.Enum
      PlatformIcons.Aspect -> AllIcons.Nodes.Aspect
      PlatformIcons.Annotation -> AllIcons.Nodes.Annotationtype
      PlatformIcons.Function -> AllIcons.Nodes.Function
      PlatformIcons.Interface -> AllIcons.Nodes.Interface
      PlatformIcons.AbstractMethod -> AllIcons.Nodes.AbstractMethod
      PlatformIcons.MethodReference -> AllIcons.Nodes.MethodReference
      PlatformIcons.Property -> AllIcons.Nodes.Property
      PlatformIcons.Parameter -> AllIcons.Nodes.Parameter
      PlatformIcons.Lambda -> AllIcons.Nodes.Lambda
      PlatformIcons.Record -> AllIcons.Nodes.Record
      PlatformIcons.Tag -> AllIcons.Nodes.Tag
      PlatformIcons.ExceptionClass -> AllIcons.Nodes.ExceptionClass
      PlatformIcons.ClassInitializer -> AllIcons.Nodes.ClassInitializer
      PlatformIcons.Plugin -> AllIcons.Nodes.Plugin
      PlatformIcons.PpWeb -> AllIcons.Nodes.PpWeb
      PlatformIcons.StaticMark -> AllIcons.Nodes.StaticMark
      PlatformIcons.FinalMark -> AllIcons.Nodes.FinalMark
      PlatformIcons.TestMark -> AllIcons.RunConfigurations.TestMark
      PlatformIcons.JunitTestMark -> AllIcons.Nodes.JunitTestMark
      PlatformIcons.RunnableMark -> AllIcons.Nodes.RunnableMark
    }
  }

  @Deprecated("Use getIcon(path, classLoader)")
  override fun getIcon(path: String, aClass: Class<*>): Icon {
    return findIconUsingDeprecatedImplementation(originalPath = path,
                                                 classLoader = aClass.classLoader,
                                                 aClass = aClass,
                                                 toolTip = IconDescriptionLoader(path))!!
  }

  override fun getIcon(path: String, classLoader: ClassLoader): Icon {
    return findIconUsingNewImplementation(path = path, classLoader = classLoader, toolTip = IconDescriptionLoader(path))!!
  }

  override fun loadRasterizedIcon(path: String, classLoader: ClassLoader, cacheKey: Int, flags: Int): Icon {
    assert(!path.startsWith('/'))
    return CachedImageIcon(
      originalPath = path,
      resolver = createRasterizedImageDataLoader(path = path, classLoader = classLoader, cacheKey = cacheKey, imageFlags = flags),
      toolTip = IconDescriptionLoader(path),
    )
  }

  override fun createEmptyIcon(icon: Icon): Icon = EmptyIcon.create(icon)

  override fun <T> createDeferredIcon(base: Icon?, param: T, iconProducer: (T) -> Icon?): Icon {
    return IconDeferrer.getInstance().defer(base = base, param = param, evaluator = iconProducer)
  }

  override fun registerIconLayer(flagMask: Int, icon: Icon) {
    for (iconLayer in iconLayers) {
      if (iconLayer.flagMask == flagMask) {
        return
      }
    }
    iconLayers.add(IconLayer(flagMask, icon))
  }

  override fun tooltipOnlyIfComposite(icon: Icon): Icon = IconWrapperWithToolTipComposite(icon)

  override fun createRowIcon(iconCount: Int, alignment: com.intellij.ui.icons.RowIcon.Alignment): com.intellij.ui.icons.RowIcon {
    return RowIcon(iconCount, alignment)
  }

  override fun createRowIcon(vararg icons: Icon): com.intellij.ui.icons.RowIcon = RowIcon(*icons)

  override fun createLayeredIcon(instance: Iconable, icon: Icon, flags: Int): RowIcon {
    val layersFromProviders = ArrayList<Icon>()
    for (provider in IconLayerProvider.EP_NAME.extensionList) {
      provider.getLayerIcon(instance, BitUtil.isSet(flags, FLAGS_LOCKED))?.let {
        layersFromProviders.add(it)
      }
    }

    var effectiveIcon: Icon? = icon
    if (flags != 0 || !layersFromProviders.isEmpty()) {
      val result = ArrayList<Icon>()
      for (l in iconLayers) {
        if (BitUtil.isSet(flags, l.flagMask)) {
          result.add(l.icon)
        }
      }
      result.addAll(layersFromProviders)
      val layeredIcon = LayeredIcon(1 + result.size)
      layeredIcon.setIcon(effectiveIcon, 0)
      for (i in result.indices) {
        val icon1 = result[i]
        layeredIcon.setIcon(icon1, i + 1)
      }
      effectiveIcon = layeredIcon
    }

    val baseIcon = RowIcon(2)
    baseIcon.setIcon(effectiveIcon, 0)
    return baseIcon
  }

  override fun createOffsetIcon(icon: Icon): Icon = OffsetIcon(icon)

  override fun colorize(g: Graphics2D, source: Icon, color: Color): Icon = IconUtil.colorize(g, source, color)

  override fun createLayered(vararg icons: Icon): Icon = LayeredIcon(*icons)

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon = IconUtil.getIcon(file, flags, project)

  override fun wakeUpNeo(reason: Any): Runnable = MacUtil.wakeUpNeo(reason)

  override fun withIconBadge(icon: Icon, color: Paint): Icon = BadgeIcon(icon, color)
}

private class IconLayer(@JvmField val flagMask: Int, @JvmField val icon: Icon) {
  init {
    BitUtil.assertOneBitMask(flagMask)
  }
}

private class IconDescriptionLoader(private val path: String) : Supplier<String?> {
  private var result: String? = null
  private var isCalculated = false

  override fun get(): String? {
    if (!isCalculated) {
      result = findIconDescription(path)
      isCalculated = true
    }
    return result
  }
}

private val iconLayers = CopyOnWriteArrayList<IconLayer>()
private const val FLAGS_LOCKED = 0x800

private fun findIconDescription(path: String): String? {
  val pathWithoutExt = path.removeSuffix(".svg")
  val key = "icon." + (if (pathWithoutExt.startsWith('/')) pathWithoutExt.substring(1) else pathWithoutExt).replace('/', '.') + ".tooltip"
  var result: String? = null
  IconDescriptionBundleEP.EP_NAME.processWithPluginDescriptor { ep, descriptor ->
    val classLoader = descriptor.pluginClassLoader ?: CoreIconManager::class.java.classLoader
    val bundle = DynamicBundle.getResourceBundle(classLoader!!, ep.resourceBundle)
    val description = AbstractBundle.messageOrNull(bundle, key)
    if (description != null) {
      result = description
    }
  }
  if (result == null && Registry.`is`("ide.icon.tooltips.trace.missing", false)) {
    logger<CoreIconManager>().info("Icon tooltip requested but not found for $path")
  }
  return result
}