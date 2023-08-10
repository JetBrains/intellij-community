// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkTableBridgeImpl
import com.intellij.workspaceModel.ide.legacyBridge.sdk.GlobalSdkTableBridge
import com.intellij.workspaceModel.ide.legacyBridge.sdk.SdkTableImplementationDelegate
import org.jetbrains.annotations.TestOnly

open class ProjectJdkTableImpl: ProjectJdkTable() {

  private val delegate: SdkTableImplementationDelegate

  private val cachedProjectJdks: MutableMap<String, Sdk> = HashMap()

  init {
    val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
    if (!GlobalSdkTableBridge.isEnabled()) {
      componentManager.registerService(SdkTableImplementationDelegate::class.java, LegacyProjectJdkTableDelegate::class.java,
                                       ComponentManagerImpl.fakeCorePluginDescriptor, false)
    } else {
      componentManager.registerService(SdkTableImplementationDelegate::class.java, SdkTableBridgeImpl::class.java,
                                       ComponentManagerImpl.fakeCorePluginDescriptor, false)
    }
    delegate = SdkTableImplementationDelegate.getInstance()
  }


  override fun findJdk(name: String): Sdk? = delegate.findSdkByName(name)

  override fun findJdk(name: String, type: String): Sdk? {
    var sdk = findJdk(name)
    if (sdk != null) return sdk

    val uniqueName = "$type.$name"
    sdk = cachedProjectJdks[uniqueName]
    if (sdk != null) return sdk

    val sdkPath = System.getProperty("jdk.$name")
    if (sdkPath == null) return null

    val sdkType = SdkType.findByName(type)
    if (sdkType != null && sdkType.isValidSdkHome(sdkPath)) {
      val createdSdk = delegate.createSdk(name, sdkType, sdkPath)
      sdkType.setupSdkPaths(createdSdk)
      cachedProjectJdks[uniqueName] = createdSdk
      return createdSdk
    }
    return null
  }

  override fun getAllJdks(): Array<Sdk> = delegate.getAllSdks().toTypedArray()

  override fun getSdksOfType(type: SdkTypeId): List<Sdk> = delegate.getAllSdks().filter { it.sdkType.name == type.name }

  override fun addJdk(sdk: Sdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    delegate.addNewSdk(sdk)
    ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkAdded(sdk)
  }

  override fun removeJdk(sdk: Sdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkRemoved(sdk)
    delegate.removeSdk(sdk)
    if (sdk is Disposable) {
      Disposer.dispose(sdk)
    }
  }

  override fun updateJdk(originalSdk: Sdk, modifiedSdk: Sdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val previousName: String = originalSdk.getName()
    val newName: String = originalSdk.getName()

    delegate.updateSdk(originalSdk, modifiedSdk)

    if (previousName != newName) {
      // fire changes because after renaming JDK its name may match the associated jdk name of modules/project
      ApplicationManager.getApplication().getMessageBus().syncPublisher<Listener>(JDK_TABLE_TOPIC).jdkNameChanged(originalSdk, previousName)
    }
  }

  override fun createSdk(name: String, sdkType: SdkTypeId): Sdk = delegate.createSdk(name, sdkType, null)

  @TestOnly
  override fun saveOnDisk(): Unit = delegate.saveOnDisk()

  override fun getDefaultSdkType(): SdkTypeId = UnknownSdkType.getInstance("")

  override fun getSdkTypeByName(sdkTypeName: String): SdkTypeId {
    return SdkType.getAllTypeList().firstOrNull { it.name == sdkTypeName } ?: UnknownSdkType.getInstance(sdkTypeName)
  }
}
