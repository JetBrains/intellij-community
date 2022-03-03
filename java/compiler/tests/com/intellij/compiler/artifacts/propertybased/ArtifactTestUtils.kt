// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.artifacts.propertybased

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.impl.artifacts.workspacemodel.toElement
import com.intellij.packaging.impl.elements.ArtifactRootElementImpl
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.PackagingElementEntity
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnBuilder
import org.junit.Assert

internal fun artifact(project: Project, name: String): Artifact {
  val bridgeArtifact = runReadAction { ArtifactManager.getInstance(project).findArtifact(name) }
  Assert.assertNotNull(bridgeArtifact)
  return bridgeArtifact!!
}

internal fun artifactEntity(project: Project, name: String): ArtifactEntity {
  val artifactEntities = WorkspaceModel.getInstance(project).entityStorage.current.entities(ArtifactEntity::class.java)
  val artifactEntity = artifactEntities.find { it.name == name }
  Assert.assertNotNull(artifactEntity)
  return artifactEntity!!
}

internal fun assertTreesEquals(project: Project, left: PackagingElement<*>, right: PackagingElementEntity) {
  val rightElement = right.toElement(project, VersionedEntityStorageOnBuilder(WorkspaceEntityStorageBuilder.create()))

  assertElementsEquals(left, rightElement)
}

internal fun assertElementsEquals(left: PackagingElement<*>, right: PackagingElement<*>) {
  if (left !is ArtifactRootElementImpl || right !is ArtifactRootElementImpl) {
    if (!left.isEqualTo(right)) {
      Assert.fail("Elements are not equals. $left <-> $right")
    }
  }

  if (left is CompositePackagingElement<*> && right is CompositePackagingElement<*>) {
    val leftChildren = left.children
    val rightChildren = right.children
    if (leftChildren.size != rightChildren.size) {
      Assert.fail("Elements have different amount of children. Left: ${leftChildren} != right: ${rightChildren}")
    }

    if (leftChildren.size != left.nonWorkspaceModelChildren.size) {
      Assert.fail("Element has different amount of children in store and internally.")
    }

    if (rightChildren.size != right.nonWorkspaceModelChildren.size) {
      Assert.fail("Element has different amount of children in store and internally.")
    }

    for (i in leftChildren.indices) {
      assertElementsEquals(leftChildren[i], rightChildren[i])
    }
  }
}

