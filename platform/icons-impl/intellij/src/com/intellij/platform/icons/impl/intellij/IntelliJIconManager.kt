// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconIdentifier
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.impl.DefaultDeferredIconDescriptor
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.DeferredIconResolver
import com.intellij.platform.icons.impl.DeferredIconResolverService
import com.intellij.platform.icons.impl.iconLayer
import com.intellij.platform.icons.impl.intellij.custom.CustomLegacyIconSerializer
import com.intellij.platform.icons.impl.intellij.design.IntelliJIconDesigner
import com.intellij.platform.icons.impl.intellij.rendering.IntelliJIconRendererManager
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.swing.ScalableSwingIcon
import com.intellij.ui.AnimatedIcon
import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.BroadcastDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import javax.swing.Icon

class IntelliJIconManager : DefaultIconManager() {
  private val resolverService: IntelliJDeferredIconResolverService by lazy {
    service<IntelliJDeferredIconResolverService>()
  }

  override fun getResolverService(): DeferredIconResolverService = resolverService

  override fun createDeferredIconResolver(
    id: IconIdentifier,
    ref: WeakReference<DefaultDeferredIconDescriptor>,
    evaluator: (suspend () -> IconDescriptor)?
  ): DeferredIconResolver {
    if (evaluator == null) {
      throw NotImplementedError("Remote Icon evaluation is not supported")
    }
    else {
      return super.createDeferredIconResolver(id, ref, evaluator)
    }
  }

  override fun iconDescriptor(designer: IconDesigner.() -> Unit): IconDescriptor {
    val ijIconDesigner = IntelliJIconDesigner()
    ijIconDesigner.designer()
    return ijIconDesigner.build()
  }

  override fun createSwingIcon(iconDescriptor: IconDescriptor, scale: IconScale): ScalableSwingIcon {
    return SwingIcon(iconDescriptor, scale)
  }

  /**
   * Adds [swingIcon] as a layer with [modifier]. A [SwingIcon] adds its descriptor, with its scale and its dark state (see
   * [SwingIcon.stateModifier]). Without a [modifier], they are the modifier of the one layer. Else the icon is nested
   * in the layer of [modifier], so the dark state of the icon stays inside and wins over a dark state that [modifier]
   * forces, and [modifier] applies to the whole scaled icon.
   */
  override fun addSwingLayer(designer: IconDesigner, swingIcon: Icon, modifier: IconModifier) {
    if (swingIcon is SwingIcon) {
      val state = swingIcon.stateModifier() ?: return designer.icon(swingIcon.iconDescriptor, modifier)
      if (modifier == IconModifier) return designer.icon(swingIcon.iconDescriptor, state)
      return designer.icon(swingIcon.toDescriptor(), modifier)
    }
    if (designer !is IntelliJIconDesigner) {
      error("Only IntelliJIconDesigner can handle swing icons.")
    }
    designer.addSwingLayer(swingIcon, modifier)
  }

  /**
   * The descriptor of [swingIcon]. A [SwingIcon] gives its descriptor with its scale and its dark state, see
   * [SwingIcon.toDescriptor]. Its constructor reads them back, so a round trip gives an equal icon and does not nest the
   * descriptor deeper. An icon without a scale and a dark state gives its descriptor itself: an update that sets the icon
   * of `Presentation.getIcon()` back keeps its descriptor.
   */
  override fun toIconDescriptor(swingIcon: Icon): IconDescriptor {
    if (swingIcon is SwingIcon) return swingIcon.toDescriptor()
    return convertLegacyIcon(swingIcon) ?: iconDescriptor {
      addSwingLayer(
        this,
        swingIcon,
        IconModifier
      )
    }
  }

  private fun convertLegacyIcon(swingIcon: Icon): IconDescriptor? {
    return when (swingIcon) {
        is AnimatedIcon -> {
          swingIcon.extractAsNewIcon()
        }
      else -> null
    }
  }

  override fun markDeferredIconUnused(id: IconIdentifier) {
    // TODO delete unused deferred icons
  }

  override suspend fun sendDeferredNotifications(id: IconIdentifier, result: IconDescriptor) {
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
  fun evaluated(id: IconIdentifier, result: IconDescriptor)

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<DeferredIconListener> = Topic(DeferredIconListener::class.java, BroadcastDirection.NONE)
  }
}
