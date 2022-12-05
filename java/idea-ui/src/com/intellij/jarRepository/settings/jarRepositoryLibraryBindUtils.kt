// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JarRepositoryLibraryBindUtils")

package com.intellij.jarRepository.settings

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.jarRepository.*
import com.intellij.jarRepository.RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryPropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

private val LOG = Logger.getInstance("com.intellij.jarRepository.settings.JarRepositoryLibraryBindUtils")

/**
 * Get the number of libraries of type [RepositoryLibraryType.REPOSITORY_LIBRARY_KIND] in [storage] with
 * [RepositoryLibraryProperties.getJarRepositoryId] in [jarRepositoryIds]
 */
internal fun countBindLibraries(storage: EntityStorage, jarRepositoryIds: Set<String>) =
  getUsagesInLibraryProperties(storage, jarRepositoryIds).size


/**
 * Get the number of libraries of type [RepositoryLibraryType.REPOSITORY_LIBRARY_KIND] in [storage] with
 * [RepositoryLibraryProperties.getJarRepositoryId] is equal to [remoteRepository].id
 */
internal fun countBindLibraries(storage: EntityStorage, remoteRepository: RemoteRepositoryDescription) =
  getUsagesInLibraryProperties(storage, setOf(remoteRepository.id)).size

/**
 * Find all libraries of type [RepositoryLibraryType.REPOSITORY_LIBRARY_KIND] in [builder] storage with
 *  [RepositoryLibraryProperties.getJarRepositoryId] in [fromJarRepositoryIds] and update this id to [toJarRepositoryId]
 */
internal fun updateLibrariesRepositoryId(builder: MutableEntityStorage, fromJarRepositoryIds: Set<String>, toJarRepositoryId: String?) {
  getUsagesInLibraryProperties(builder, fromJarRepositoryIds).forEach { entity ->
    val newXmlTag = try {
      val libraryProperties =  deserializeRepositoryLibraryProperties(entity.propertiesXmlTag!!)
      libraryProperties.jarRepositoryId = toJarRepositoryId
      val element = serialize(libraryProperties)!!.setName(JpsLibraryTableSerializer.PROPERTIES_TAG)
      JDOMUtil.writeElement(element)
    }
    catch (e: Exception) {
      LOG.warnInProduction(e)
      return@forEach
    }

    builder.modifyEntity(entity) {
      propertiesXmlTag = newXmlTag
    }
  }
}

/**
 * Find all libraries of type [REPOSITORY_LIBRARY_KIND] in [builder] storage with
 *  [RepositoryLibraryProperties.getJarRepositoryId] equal to [fromJarRepository].id and update this id to [toJarRepositoryId]
 */
internal fun updateLibrariesRepositoryId(builder: MutableEntityStorage,
                                         fromJarRepository: RemoteRepositoryDescription,
                                         toJarRepositoryId: RemoteRepositoryDescription?) {
  updateLibrariesRepositoryId(builder, setOf(fromJarRepository.id), toJarRepositoryId?.id)
}

private fun getUsagesInLibraryProperties(storage: EntityStorage, jarRepositoryIds: Set<String>): List<LibraryPropertiesEntity> =
  storage.entities(LibraryEntity::class.java).mapNotNull { libraryEntity ->
    val propertiesEntity = libraryEntity.libraryProperties ?: return@mapNotNull null
    if (REPOSITORY_LIBRARY_KIND.kindId != propertiesEntity.libraryType) {
      return@mapNotNull null
    }

    val propertiesXmlTag = propertiesEntity.propertiesXmlTag ?: return@mapNotNull null
    val properties = deserializeRepositoryLibraryProperties(propertiesXmlTag)
    if (properties.jarRepositoryId !in jarRepositoryIds) {
      return@mapNotNull null
    }
    propertiesEntity
  }.toList()


private fun deserializeRepositoryLibraryProperties(xmlTag: String) =
  deserialize<RepositoryLibraryProperties>(JDOMUtil.load(xmlTag))
