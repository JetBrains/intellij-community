// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.artifacts.propertybased

import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingElementType
import com.intellij.packaging.ui.ArtifactEditorContext
import com.intellij.packaging.ui.PackagingElementPresentation
import java.util.function.Supplier

class MyWorkspacePackagingElement(data: String) : PackagingElement<MyWorkspacePackagingElementState>(PackagingElementType.EP_NAME.findExtensionOrFail(MyWorkspacePackagingElementType::class.java)) {

  constructor(): this("")

  private val state: MyWorkspacePackagingElementState = MyWorkspacePackagingElementState(data)

  override fun getState(): MyWorkspacePackagingElementState = state

  override fun loadState(state: MyWorkspacePackagingElementState) {
    this.state.data = state.data
  }

  override fun isEqualTo(element: PackagingElement<*>): Boolean = (element as? MyWorkspacePackagingElement)?.state?.data == state.data

  override fun createPresentation(context: ArtifactEditorContext): PackagingElementPresentation {
    throw UnsupportedOperationException()
  }
}

class MyWorkspacePackagingElementState(var data: String = "")

object MyWorkspacePackagingElementType : PackagingElementType<MyWorkspacePackagingElement>("Custom-element", Supplier { "Custom Element" }) {
  override fun canCreate(context: ArtifactEditorContext, artifact: Artifact): Boolean = true

  override fun chooseAndCreate(context: ArtifactEditorContext,
                               artifact: Artifact,
                               parent: CompositePackagingElement<*>): List<PackagingElement<*>> {
    throw UnsupportedOperationException()
  }

  override fun createEmpty(project: Project): MyWorkspacePackagingElement {
    return MyWorkspacePackagingElement()
  }
}

class MyCompositeWorkspacePackagingElement(data: String, name: String) : CompositePackagingElement<MyCompositeWorkspacePackagingElementState>(PackagingElementType.EP_NAME.findExtensionOrFail(MyCompositeWorkspacePackagingElementType::class.java)) {

  constructor(): this("", "")

  private val state: MyCompositeWorkspacePackagingElementState = MyCompositeWorkspacePackagingElementState(data, name)

  override fun getState(): MyCompositeWorkspacePackagingElementState = state

  override fun loadState(state: MyCompositeWorkspacePackagingElementState) {
    this.state.data = state.data
  }

  override fun isEqualTo(element: PackagingElement<*>): Boolean = (element as? MyCompositeWorkspacePackagingElement)?.state?.data == state.data

  override fun getName(): String = state.name

  override fun rename(newName: String) {
    state.name = newName
  }

  override fun createPresentation(context: ArtifactEditorContext): PackagingElementPresentation {
    throw UnsupportedOperationException()
  }

  override fun toString(): String {
    return "MyCompositeWorkspacePackagingElement(state=$state)"
  }
}

data class MyCompositeWorkspacePackagingElementState(var data: String = "", var name: String = "")

object MyCompositeWorkspacePackagingElementType : PackagingElementType<MyCompositeWorkspacePackagingElement>("Composite-custom-element", Supplier { "Composite Custom Element" }) {
  override fun canCreate(context: ArtifactEditorContext, artifact: Artifact): Boolean = true

  override fun chooseAndCreate(context: ArtifactEditorContext,
                               artifact: Artifact,
                               parent: CompositePackagingElement<*>): List<PackagingElement<*>> {
    throw UnsupportedOperationException()
  }

  override fun createEmpty(project: Project): MyCompositeWorkspacePackagingElement {
    return MyCompositeWorkspacePackagingElement()
  }
}
