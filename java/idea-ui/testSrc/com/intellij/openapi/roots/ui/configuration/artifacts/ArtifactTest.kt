// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext
import com.intellij.openapi.util.Disposer
import com.intellij.packaging.artifacts.ArtifactListener
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.artifacts.ArtifactType
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.elements.PackagingElementOutputKind
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ui.EmptyIcon
import java.util.function.Supplier
import javax.swing.Icon

class ArtifactTest : HeavyPlatformTestCase() {
  fun `test rename`() {
    runWithRegisteredExtension(MockArtifactTypeForRename(), ArtifactType.EP_NAME) {
      ArtifactManager.getInstance(project).addArtifact("X", MockArtifactTypeForRename(), null)
      val artifact = ArtifactManager.getInstance(project).addArtifact("unnamed", MockArtifactTypeForRename(), null)
      val context = ArtifactsStructureConfigurableContextImpl(StructureConfigurableContext(project, ModulesConfigurator(project,
                                                                                                                        ProjectStructureConfigurable.getInstance(
                                                                                                                          project))),
                                                              project,
                                                              ArtifactEditorSettings(),
                                                              object : ArtifactListener {})
      disposeOnTearDown(Disposable { context.disposeUIResources() })
      val configurable = ArtifactConfigurable(artifact,
                                              context, Runnable { })
      configurable.displayName = "X"

      var artifacts = context.actualModifiableModel!!.artifacts.map { it.name }
      assertContainsElements(artifacts, "unnamed")

      configurable.displayName = "X2"
      artifacts = context.actualModifiableModel!!.artifacts.map { it.name }
      assertContainsElements(artifacts, "X")
      assertContainsElements(artifacts, "X2")
    }
  }
}

private class MockArtifactTypeForRename : ArtifactType("mock", Supplier { "Mock" }) {
  companion object {
    fun getInstance() = EP_NAME.findExtension(MockArtifactTypeForRename::class.java)!!
  }

  override fun getIcon(): Icon = EmptyIcon.ICON_16

  override fun getDefaultPathFor(kind: PackagingElementOutputKind): String? = ""

  override fun createRootElement(artifactName: String): CompositePackagingElement<*> {
    return PackagingElementFactory.getInstance().createArtifactRootElement()
  }
}

private inline fun <T> runWithRegisteredExtension(extension: T, extensionPoint: ExtensionPointName<T>, action: () -> Unit) {
  val disposable = Disposer.newDisposable()
  registerExtension(extension, extensionPoint, disposable)
  try {
    action()
  }
  finally {
    Disposer.dispose(disposable)
  }
}

private fun <T> registerExtension(type: T, extensionPointName: ExtensionPointName<T>, disposable: Disposable) {
  val artifactTypeDisposable = Disposer.newDisposable()
  Disposer.register(disposable, Disposable {
    runWriteAction {
      Disposer.dispose(artifactTypeDisposable)
    }
  })
  extensionPointName.point.registerExtension(type, artifactTypeDisposable)
}
