// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.java.workspace.entities.CompositePackagingElementEntity
import com.intellij.java.workspace.entities.PackagingElementEntity
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider
import com.intellij.packaging.artifacts.ArtifactType
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingElementType
import com.intellij.packaging.impl.artifacts.InvalidArtifactType
import kotlinx.coroutines.CoroutineScope

internal class DynamicArtifactExtensionsLoaderBridge(private val artifactManager: ArtifactManagerBridge) {
  fun installListeners(coroutineScope: CoroutineScope) {
    ArtifactType.EP_NAME.point.addExtensionPointListener(coroutineScope, false, object : ExtensionPointListener<ArtifactType> {
      override fun extensionAdded(extension: ArtifactType, pluginDescriptor: PluginDescriptor) {
        runWriteAction {
          artifactManager.dropMappings { it.artifactType == extension.id }
        }
      }

      override fun extensionRemoved(extension: ArtifactType, pluginDescriptor: PluginDescriptor) {
        runWriteAction {
          artifactManager.dropMappings { it.artifactType == extension.id }
        }
      }
    })

    PackagingElementType.EP_NAME.point.addExtensionPointListener(coroutineScope, false, object : ExtensionPointListener<PackagingElementType<out PackagingElement<*>>> {
      override fun extensionAdded(extension: PackagingElementType<out PackagingElement<*>>, pluginDescriptor: PluginDescriptor) {
        runWriteAction {
          artifactManager.dropMappings { it.artifactType == InvalidArtifactType.getInstance().id }
        }
      }

      override fun extensionRemoved(extension: PackagingElementType<out PackagingElement<*>>, pluginDescriptor: PluginDescriptor) {

        runWriteAction {
          artifactManager.dropMappings { artifactEntity ->
            fun shouldDrop(element: PackagingElementEntity): Boolean {
              if (element.sameTypeWith(extension)) return true
              if (element is CompositePackagingElementEntity) {
                return element.children.any { shouldDrop(it) }
              }
              return false
            }

            return@dropMappings shouldDrop(artifactEntity.rootElement!!)
          }
        }
      }
    })

    ArtifactPropertiesProvider.EP_NAME.point.addExtensionPointListener(coroutineScope, false, object : ExtensionPointListener<ArtifactPropertiesProvider> {
      override fun extensionAdded(extension: ArtifactPropertiesProvider, pluginDescriptor: PluginDescriptor) {
        runWriteAction {
          artifactManager.dropMappings { entity -> entity.customProperties.any { it.providerType == extension.id } }
        }
      }

      override fun extensionRemoved(extension: ArtifactPropertiesProvider, pluginDescriptor: PluginDescriptor) {
        runWriteAction {
          artifactManager.dropMappings { entity -> entity.customProperties.any { it.providerType == extension.id } }
        }
      }
    })
  }
}
