// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTableProjectViewProvider
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable
import java.nio.file.Path

@ApiStatus.Internal
class SdkTableProjectViewProviderImpl(val project: Project) : SdkTableProjectViewProvider {
  private val descriptor = project.basePath?.toNioPathOrNull()?.getEelDescriptor() ?: LocalEelDescriptor

  override fun getSdkTableView(): ProjectJdkTable {
    val generalTable = ProjectJdkTable.getInstance()
    if (!Registry.`is`("ide.workspace.model.per.environment.model.separation")) {
      return generalTable
    }
    return ProjectJdkTableProjectView(descriptor, generalTable)
  }
}

private class ProjectJdkTableProjectView(val descriptor: EelDescriptor, val delegate: ProjectJdkTable) : ProjectJdkTable() {
  override fun findJdk(name: String): Sdk? {
    return delegate.allJdks.find {
      it.name == name && validateDescriptor(it)
    }
  }

  override fun findJdk(name: String, type: String): Sdk? {
    // sometimes delegate.findJdk can do mutating operations, like in case of ProjectJdkTableImpl
    return allJdks.find { it.name == name && it.sdkType.name == type } ?: delegate.findJdk(name, type)
  }

  override fun getAllJdks(): Array<out Sdk> {
    return delegate.allJdks.filter(::validateDescriptor).toTypedArray()
  }

  private fun validateDescriptor(sdk: Sdk): Boolean {
    val sdkDescriptor = sdk.homePath?.let(Path::of)?.getEelDescriptor()
    return if (sdkDescriptor == null) {
      true
    }
    else {
      sdkDescriptor == this.descriptor
    }
  }

  override fun getSdksOfType(type: SdkTypeId): @Unmodifiable List<Sdk?> {
    return allJdks.filter { it.sdkType == type }
  }

  override fun addJdk(jdk: Sdk) {
    delegate.addJdk(jdk)
  }

  override fun removeJdk(jdk: Sdk) {
    delegate.removeJdk(jdk)
  }

  override fun updateJdk(originalJdk: Sdk, modifiedJdk: Sdk) {
    delegate.updateJdk(originalJdk, modifiedJdk)
  }

  override fun getDefaultSdkType(): SdkTypeId {
    return delegate.defaultSdkType
  }

  override fun getSdkTypeByName(name: String): SdkTypeId {
    return allJdks.find { it.name == name }?.sdkType ?: delegate.getSdkTypeByName(name)
  }

  override fun createSdk(name: String, sdkType: SdkTypeId): Sdk {
    return delegate.createSdk(name, sdkType)
  }

}