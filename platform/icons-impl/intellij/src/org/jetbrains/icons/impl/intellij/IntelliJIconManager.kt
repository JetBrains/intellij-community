// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.BroadcastDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconIdentifier
import org.jetbrains.icons.ImageResourceLocation
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.impl.DefaultDeferredIcon
import org.jetbrains.icons.impl.DefaultIconManager
import org.jetbrains.icons.impl.DeferredIconResolver
import org.jetbrains.icons.impl.intellij.custom.CustomIconLayerRegistration
import org.jetbrains.icons.impl.intellij.custom.CustomLegacyIconSerializer
import org.jetbrains.icons.impl.intellij.design.IntelliJIconDesigner
import org.jetbrains.icons.impl.intellij.rendering.SwingIcon
import java.lang.ref.WeakReference
import kotlin.getValue

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

  override fun toSwingIcon(icon: Icon): javax.swing.Icon {
    return SwingIcon(icon)
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
    CustomIconLayerRegistration.registerSerializersTo(this)
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
  }

  companion object {
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