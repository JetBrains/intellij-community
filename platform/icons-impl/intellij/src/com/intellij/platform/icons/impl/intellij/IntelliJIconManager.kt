// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.IconIdentifier
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.impl.DefaultDeferredIcon
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.DefaultLayeredIcon
import com.intellij.platform.icons.impl.DeferredIconResolver
import com.intellij.platform.icons.impl.iconLayer
import com.intellij.platform.icons.impl.intellij.custom.CustomLegacyIconSerializer
import com.intellij.platform.icons.impl.intellij.design.IntelliJIconDesigner
import com.intellij.platform.icons.impl.intellij.rendering.IntelliJIconRendererManager
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.scale.IconScale
import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.BroadcastDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference

class IntelliJIconManager : DefaultIconManager() {
  override val resolverService: IntelliJDeferredIconResolverService by lazy {
    service<IntelliJDeferredIconResolverService>()
  }

  override fun generateDeferredIconIdentifier(id: String?, classLoader: ClassLoader?): IconIdentifier {
    if (classLoader != null) {
      val (pluginId, moduleId) = getPluginAndModuleId(classLoader)
      return ModuleIconIdentifier(pluginId, moduleId, super.generateDeferredIconIdentifier(id, classLoader))
    }
    return super.generateDeferredIconIdentifier(id, classLoader)
  }

  override fun createDeferredIconResolver(
    id: IconIdentifier,
    ref: WeakReference<DefaultDeferredIcon>,
    evaluator: (suspend () -> Icon)?
  ): DeferredIconResolver {
    if (evaluator == null) {
      throw NotImplementedError("Remote Icon evaluation is not supported")
    }
    else {
      return super.createDeferredIconResolver(id, ref, evaluator)
    }
  }

  override fun icon(designer: IconDesigner.() -> Unit): Icon {
    val ijIconDesigner = IntelliJIconDesigner()
    ijIconDesigner.designer()
    return ijIconDesigner.build()
  }

  override fun toSwingIcon(icon: Icon, scale: IconScale): javax.swing.Icon {
    // If the icon is converted using toNewIcon(), we just return the legacy icon to avoid
    // any further resource allocation
    if (icon is DefaultLayeredIcon && icon.layers.size == 1) {
      val firstLayer = icon.layers.firstOrNull()
      if (firstLayer is SwingIconLayer && firstLayer.modifier == IconModifier) {
        return firstLayer.legacyIcon
      }
    }
    return SwingIcon(icon, scale)
  }

  override fun addSwingLayer(designer: IconDesigner, swingIcon: javax.swing.Icon, modifier: IconModifier) {
    if (swingIcon is SwingIcon) {
      return designer.icon(swingIcon.icon, modifier)
    }
    if (designer !is IntelliJIconDesigner) {
      error("Only IntelliJIconDesigner can handle swing icons.")
    }
    designer.addSwingLayer(swingIcon, modifier)
  }

  override fun toNewIcon(swingIcon: javax.swing.Icon, modifier: IconModifier): Icon {
    if (swingIcon is SwingIcon && modifier == IconModifier) return swingIcon.icon
    return icon {
      addSwingLayer(this, swingIcon, modifier)
    }
  }

  override fun markDeferredIconUnused(id: IconIdentifier) {
    // TODO delete unused deferred icons
  }

  override suspend fun sendDeferredNotifications(id: IconIdentifier, result: Icon) {
    val deferredIconListener = ApplicationManager.getApplication().messageBus.syncPublisher(DeferredIconListener.TOPIC)
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      deferredIconListener.evaluated(id, result)
    }
  }

  override fun SerializersModuleBuilder.buildCustomSerializers() {
    CustomLegacyIconSerializer.registerSerializersTo(this)
    polymorphic(
      ImageResourceLocation::class,
      ModuleImageResourceLocation::class,
      ModuleImageResourceLocation.serializer()
    )
    polymorphic(
      IconIdentifier::class,
      ModuleIconIdentifier::class,
      ModuleIconIdentifier.serializer()
    )
    iconLayer(
      SwingIconLayer::class,
      SwingIconLayer.serializer()
    )
  }

  companion object {
    fun activate() {
      com.intellij.platform.icons.IconManager.activate(IntelliJIconManager())
      IconRendererManager.activate(IntelliJIconRendererManager())
    }

    internal fun getPluginAndModuleId(classLoader: ClassLoader): Pair<String, String?> {
      if (classLoader is PluginAwareClassLoader) {
        return classLoader.pluginId.idString to classLoader.moduleId
      }
      else {
        return "com.intellij" to null
      }
    }
  }
}

@ApiStatus.Internal
interface DeferredIconListener {
  fun evaluated(id: IconIdentifier, result: Icon)

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<DeferredIconListener> = Topic(DeferredIconListener::class.java, BroadcastDirection.NONE)
  }
}