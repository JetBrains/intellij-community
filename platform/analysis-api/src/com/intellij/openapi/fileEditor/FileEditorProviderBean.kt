// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * A declaration of a [FileEditorProvider] in a plugin descriptor.
 *
 * The file editor provider manager reads the attributes without loading [implementation],
 * so a provider that does not match the file is never created.
 */
@ApiStatus.Internal
class FileEditorProviderBean : BaseKeyedLazyInstance<FileEditorProvider> {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<FileEditorProviderBean> = ExtensionPointName("com.intellij.fileEditorProvider")
  }

  @RequiredElement
  @Attribute("implementation")
  lateinit var implementation: String

  /** The id of the extension. The manager finds a provider by it before it creates any instance. */
  @Attribute("id")
  @JvmField
  var id: String? = null

  /** The name of the file type that the provider accepts. The manager skips a file of another type. */
  @Attribute("fileType")
  @JvmField
  var fileType: String? = null

  /** `true` when the provider needs a document. The manager skips a file that cannot have one. */
  @Attribute("isDocumentRequired")
  @JvmField
  var isDocumentRequired: Boolean = false

  /** `true` when the provider may open a file that the user has not trusted. */
  @Attribute("allowedInUntrustedFiles")
  @JvmField
  var allowedInUntrustedFiles: Boolean = false

  @Suppress("unused") // the platform creates the bean by reflection
  constructor()

  /** Registers [provider] without a declaration. The attributes keep their defaults. */
  @TestOnly
  constructor(provider: FileEditorProvider) : super(provider) {
    implementation = provider.javaClass.name
    // a programmatic registration has no declaring plugin, so the bean names the platform, as the extension point does for an object
    pluginDescriptor = DefaultPluginDescriptor(PluginId.getId("com.intellij"), provider.javaClass.classLoader)
  }

  override fun getImplementationClassName(): String = implementation
}
