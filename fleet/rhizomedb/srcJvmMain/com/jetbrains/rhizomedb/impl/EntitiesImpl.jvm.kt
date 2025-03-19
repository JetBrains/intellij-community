// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.EntityType
import fleet.util.logging.logger
import java.io.InputStream

private const val ENTITY_TYPES_PROVIDERS_LIST_PATH = "META-INF/com.jetbrains.rhizomedb.impl.EntityTypeProvider.txt"

fun collectEntityTypeProviders(module: Module): List<EntityTypeProvider> = listOf(
  // TODO: replace with service providers. Hard to do before K2. After K2 have problems with IC (KT-66735), but seems they not interfere
  MetaInfBasedEntityTypeProvider(module.classLoader, module::getResourceAsStream)
)

@Suppress("unused") // API for IJ
fun collectEntityTypeProviders(classLoader: ClassLoader): List<EntityTypeProvider> = listOf(
  MetaInfBasedEntityTypeProvider(classLoader, classLoader::getResourceAsStream)
)

private fun InputStream.metaInfLineSequence() : Sequence<String> =
  bufferedReader()
    .lineSequence()
    .filter(String::isNotBlank)

private class MetaInfBasedEntityTypeProvider(
  val classLoader: ClassLoader,
  val resourceLoader: (String) -> InputStream?,
) : EntityTypeProvider {
  override val entityTypes: List<EntityType<*>> by lazy {
    resourceLoader(ENTITY_TYPES_PROVIDERS_LIST_PATH)?.metaInfLineSequence()?.flatMap { providerClassName ->
      try {
        val providerClass = classLoader.loadClass(providerClassName)
        val provider = providerClass.getField(INSTANCE).get(null) as EntityTypeProvider
        provider.entityTypes
      }
      catch (e : Exception) {
        logger.error(e) { "Couldn't load entity types from $providerClassName" }
        emptyList()
      }
    }?.toList() ?: emptyList()
  }

  private val logger = logger<MetaInfBasedEntityTypeProvider>()
}

private const val INSTANCE = "INSTANCE"
