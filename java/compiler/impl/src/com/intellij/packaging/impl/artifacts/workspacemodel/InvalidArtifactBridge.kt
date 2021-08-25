// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packaging.artifacts.ArtifactListener
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.impl.artifacts.InvalidArtifact
import com.intellij.util.EventDispatcher
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactId
import org.jetbrains.annotations.Nls

class InvalidArtifactBridge(
  _artifactId: ArtifactId,
  entityStorage: VersionedEntityStorage,
  project: Project,
  eventDispatcher: EventDispatcher<ArtifactListener>?,

  @Nls(capitalization = Nls.Capitalization.Sentence)
  private val _errorMessage: String,
) : InvalidArtifact, ArtifactBridge(_artifactId, entityStorage, project, eventDispatcher, null) {

  private val rootElement by lazy {
    PackagingElementFactory.getInstance().createArtifactRootElement()
  }

  override fun getErrorMessage(): String = _errorMessage

  override fun isBuildOnMake(): Boolean = false

  override fun getRootElement(): CompositePackagingElement<*> = rootElement

  override fun getOutputPath(): String = ""

  override fun getOutputFile(): VirtualFile? = null

  override fun getOutputFilePath(): String? = null
}
