// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TemplateLanguageStubBaseVersion.dropVersion

internal class StubElementRegistryServiceImpl : CoreStubElementRegistryServiceImpl(), Disposable.Default {
  @Volatile private lateinit var factories: Map<IElementType, StubElementFactory<*, *>>
  @Volatile private lateinit var lightFactories: Map<IElementType, LightStubElementFactory<*, *>>
  @Volatile private lateinit var type2serializerMap: Map<IElementType, ObjectStubSerializer<*, *>>
  @Volatile private lateinit var serializer2typeMap: Map<ObjectStubSerializer<*, *>, IElementType>

  init {
    STUB_REGISTRY_EP.addChangeListener(Runnable { init() }, this)
    STUB_DEFINITION_EP.point?.addChangeListener(Runnable { onStubDefinitionChange() }, this)
    init()
  }

  private fun init() {
    val factories = mutableMapOf<IElementType, StubElementFactory<*, *>>()
    val lightFactories = mutableMapOf<IElementType, LightStubElementFactory<*, *>>()
    val type2serializerMap = mutableMapOf<IElementType, ObjectStubSerializer<*, *>>()
    val serializer2typeMap = mutableMapOf<ObjectStubSerializer<*, *>, IElementType>()

    val registry = StubRegistryImpl(factories, lightFactories, type2serializerMap, serializer2typeMap)

    STUB_REGISTRY_EP.forEachExtensionSafe {
      it.register(registry)
    }

    synchronized(this) {
      this.factories = factories
      this.lightFactories = lightFactories
      this.type2serializerMap = type2serializerMap
      this.serializer2typeMap = serializer2typeMap
    }
  }

  private fun onStubDefinitionChange() {
    // todo IJPL-562 do we need to drop version when registering LanguageStubDefinition
    dropVersion()
  }

  override fun getStubFactory(type: IElementType): StubElementFactory<*, *>? {
    return super.getStubFactory(type) ?: factories[type]
  }

  override fun getLightStubFactory(type: IElementType): LightStubElementFactory<*, *>? {
    return super.getLightStubFactory(type) ?: lightFactories[type]
  }

  override fun getStubSerializer(type: IElementType): ObjectStubSerializer<*, Stub>? {
    return (super.getStubSerializer(type) ?: type2serializerMap[type]) as ObjectStubSerializer<*, Stub>?
  }

  fun getElementTypeBySerializer(serializer: ObjectStubSerializer<*, *>): IElementType? {
    return serializer as? IElementType ?: serializer2typeMap[serializer]
  }

  override fun getStubDescriptor(language: Language): LanguageStubDescriptor? {
    super.getStubDescriptor(language)?.let { obsoleteDescriptor ->
      return obsoleteDescriptor
    }

    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language) ?: return null

    val fileNodeType = parserDefinition.getFileNodeType()
    val stubDefinition = STUB_DEFINITION_EP.forLanguage(language) ?: return null
    val serializer = getStubSerializer(fileNodeType) ?: return null

    return LanguageStubDescriptorImpl(
      language = language,
      fileElementType = fileNodeType,
      stubDefinition = stubDefinition,
      fileElementSerializer = serializer
    )
  }

  companion object {
    @JvmStatic
    fun getInstanceImpl(): StubElementRegistryServiceImpl = StubElementRegistryService.getInstance() as StubElementRegistryServiceImpl
  }
}

@JvmField
internal val STUB_DEFINITION_EP = LanguageExtension<LanguageStubDefinition>("com.intellij.languageStubDefinition")

@JvmField
internal val STUB_REGISTRY_EP = ExtensionPointName<StubRegistryExtension>("com.intellij.stubElementRegistryExtension")

private class StubRegistryImpl(
  private val factories: MutableMap<IElementType, StubElementFactory<*, *>>,
  private val lightFactories: MutableMap<IElementType, LightStubElementFactory<*, *>>,
  private val type2serializerMap: MutableMap<IElementType, ObjectStubSerializer<*, *>>,
  private val serializer2typeMap: MutableMap<ObjectStubSerializer<*, *>, IElementType>,
) : StubRegistry {
  override fun registerStubFactory(type: IElementType, factory: StubElementFactory<*, *>) {
    if (factory is LightStubElementFactory<*, *>) {
      throw IllegalArgumentException("Light stub element factory must be registered in registerLightSTubFactory")
    }
    factories[type] = factory
  }

  override fun registerLightStubFactory(type: IElementType, factory: LightStubElementFactory<*, *>) {
    lightFactories[type] = factory
    factories[type] = factory
  }

  override fun registerStubSerializer(type: IElementType, serializer: ObjectStubSerializer<*, *>) {
    type2serializerMap[type] = serializer
    serializer2typeMap[serializer] = type
  }
}

private class LanguageStubDescriptorImpl(
  override val language: Language,
  override val fileElementType: IFileElementType,
  override val stubDefinition: LanguageStubDefinition,
  override val fileElementSerializer: ObjectStubSerializer<*, *>,
) : LanguageStubDescriptor
