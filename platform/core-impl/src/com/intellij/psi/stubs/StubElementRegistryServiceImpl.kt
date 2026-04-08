// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TemplateLanguageStubBaseVersion
import com.intellij.util.concurrency.CancellableClearableLazy
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.Collections

@ApiStatus.Internal
open class StubElementRegistryServiceImpl(coroutineScope: CoroutineScope) : CoreStubElementRegistryServiceImpl() {
  private val state = CancellableClearableLazy(::doComputeStateUnderLock)

  init {
    STUB_REGISTRY_EP.addChangeListener(coroutineScope) { state.clear() }
    STUB_DEFINITION_EP.point?.addChangeListener(coroutineScope) { TemplateLanguageStubBaseVersion.dropVersion() }
  }

  protected fun getState(): StubElementRegistryState =
    state.value

  private fun doComputeStateUnderLock(): StubElementRegistryState {
    val factories = mutableMapOf<IElementType, StubElementFactory<*, *>>()
    val lightFactories = mutableMapOf<IElementType, LightStubElementFactory<*, *>>()
    val type2serializerMap = mutableMapOf<IElementType, ObjectStubSerializer<*, *>>()
    val serializer2typeMap = mutableMapOf<ObjectStubSerializer<*, *>, IElementType>()

    val registry = StubRegistryImpl(factories, lightFactories, type2serializerMap, serializer2typeMap)

    STUB_REGISTRY_EP.forEachExtensionSafe {
      ProgressManager.checkCanceled()
      it.register(registry)
    }

    return StubElementRegistryState(
      factories = Collections.unmodifiableMap(factories),
      lightFactories = Collections.unmodifiableMap(lightFactories),
      type2serializerMap = Collections.unmodifiableMap(type2serializerMap),
      serializer2typeMap = Collections.unmodifiableMap(serializer2typeMap),
    )
  }

  fun ensureStateLoaded() {
    getState()
  }

  override fun getStubFactory(type: IElementType): StubElementFactory<*, *>? {
    return super.getStubFactory(type) ?: getState().factories[type]
  }

  override fun getLightStubFactory(type: IElementType): LightStubElementFactory<*, *>? {
    return super.getLightStubFactory(type) ?: getState().lightFactories[type]
  }

  override fun getStubSerializer(type: IElementType): ObjectStubSerializer<*, Stub>? {
    @Suppress("UNCHECKED_CAST")
    return (super.getStubSerializer(type) ?: getState().type2serializerMap[type]) as ObjectStubSerializer<*, Stub>?
  }

  fun getElementTypeBySerializer(serializer: ObjectStubSerializer<*, *>): IElementType? {
    return serializer as? IElementType ?: getState().serializer2typeMap[serializer]
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

  protected class StubElementRegistryState(
    val factories: Map<IElementType, StubElementFactory<*, *>>,
    val lightFactories: Map<IElementType, LightStubElementFactory<*, *>>,
    val type2serializerMap: Map<IElementType, ObjectStubSerializer<*, *>>,
    val serializer2typeMap: Map<ObjectStubSerializer<*, *>, IElementType>,
  )

  companion object {
    @JvmStatic
    fun getInstanceImpl(): StubElementRegistryServiceImpl = StubElementRegistryService.getInstance() as StubElementRegistryServiceImpl
  }
}

@JvmField
internal val STUB_DEFINITION_EP: LanguageExtension<LanguageStubDefinition> = LanguageExtension("com.intellij.languageStubDefinition")

@JvmField
internal val STUB_REGISTRY_EP: ExtensionPointName<StubRegistryExtension> = ExtensionPointName("com.intellij.stubElementRegistryExtension")

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
) : LanguageStubDescriptor {
  override fun toString(): String = "LanguageStubDescriptorImpl(language=$language)"
}
