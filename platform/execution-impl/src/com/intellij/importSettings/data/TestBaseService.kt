// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.data

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.util.*
import javax.swing.Icon


class TestBaseService : SettingsService {
  companion object {
    private val LOG = logger<TestBaseService>()

    val IMPORT_SERVICE = "ImportService"
    fun getInstance() = service<TestBaseService>()
  }

  override fun getSyncService(): SyncService {
    return TestSyncService()
  }

  override fun getImportService(): ImportService {
    return TestImportService()
  }

  override fun getExternalService(): ImportExternalService {
    return TestImportExternalService()
  }

  override fun skipImport() {
    if(LOG.isTraceEnabled) {
      LOG.trace("$IMPORT_SERVICE skipImport")
    }
  }
}

class TestImportService : ImportService {
  companion object {

    private val LOG = logger<TestImportService>()

    val main = TestProduct("main1", "IdeaMain1", "версия", Date())
    val main2 = TestProduct("main2", "IdeaMain1", "версия", Date())
    val enemy = TestProduct("main2", "IdeaMain1", "версия", Date())

    val fresh = listOf(TestProduct("1", "Idea111", "версия", Date()),
                             TestProduct("2", "Idea222", "версия", Date()),
                             TestProduct("3", "Idea333", "версия", Date()),
                             TestProduct("4","Idea444", "версия", Date()),
                             TestProduct("5", "Idea555", "версия", Date()),
                             TestProduct("6", "Idea666", "версия", Date()))

    val old = listOf(TestProduct("7", "Idea111", "версия", Date()),
                             TestProduct("8", "Idea222", "версия", Date()),
                             TestProduct("9", "Idea333", "версия", Date()),
                             TestProduct("10", "Idea444", "версия", Date()),
                             TestProduct("11", "Idea555", "версия", Date()),
                             TestProduct("12", "Idea666", "версия", Date()))

    val productList3 = listOf(TestProduct("13",  "Idea111", "версия", Date()),
                              TestProduct("14", "Idea222", "версия", Date()))

    fun getProductIcon(size: IconProductSize): Icon {
      return when(size) {
        IconProductSize.SMALL -> AllIcons.TransferSettings.Resharper
        IconProductSize.MIDDLE -> AllIcons.General.NotificationInfo
        IconProductSize.LARGE -> AllIcons.General.SuccessLogin
      }
    }

  }

  override fun importSettings(productId: String, ids: DataForSave) {
    if(LOG.isTraceEnabled) {
      LOG.trace("${TestBaseService.IMPORT_SERVICE} importSettings")
    }

  }

  override fun getFreshProducts(): List<Product> {
    return productList3 //fresh
  }

  override fun getOldProducts(): List<Product> {
    return old
  }

  override fun getSettings(productId: String): List<BaseSetting> {
    return emptyList()
  }

  override fun getProductIcon(productId: String, size: IconProductSize): Icon {
    return Companion.getProductIcon(size)
  }


}

class TestImportExternalService : ImportExternalService {
  companion object {
    private val LOG = logger<TestImportExternalService>()
  }

  override fun getProducts(): List<Product> {
    return listOf(TestImportService.main2)
  }

  override fun importSettings(productId: String) {
    if(LOG.isTraceEnabled) {
      LOG.trace("${TestBaseService.IMPORT_SERVICE} importSettings")
    }
  }

  override fun getProductIcon(productId: String, size: IconProductSize): Icon {
    return TestImportService.getProductIcon(size)
  }

  override fun getSettings(productId: String): List<BaseSetting> {
    return emptyList()
  }

}

class TestSyncService : SyncService {
  companion object {
    private val LOG = logger<TestSyncService>()
  }

  override val syncState: SYNC_STATE
    get() = SYNC_STATE.GENERAL

  override fun tryToLogin(): String? {
    if(LOG.isTraceEnabled) {
      LOG.trace("${TestBaseService.IMPORT_SERVICE} tryToLogin")
    }
    return null
  }

  override fun syncSettings(productId: String) {
    if(LOG.isTraceEnabled) {
      LOG.trace("${TestBaseService.IMPORT_SERVICE} syncSettings id: '$productId' ")
    }
  }

  override fun importSettings(productId: String) {
    if(LOG.isTraceEnabled) {
      LOG.trace("${TestBaseService.IMPORT_SERVICE} importSettings id: '$productId' ")
    }
  }

  override fun generalSync() {
    if(LOG.isTraceEnabled) {
      LOG.trace("${TestBaseService.IMPORT_SERVICE} generalSync")
    }
  }

  override fun getMainProduct(): Product? {
    return null
  }

  override fun getFreshProducts(): List<Product> {
    return TestImportService.fresh
  }

  override fun getOldProducts(): List<Product> {
    return TestImportService.old
  }

  override fun getSettings(productId: String): List<BaseSetting> {
    return emptyList()
  }

  override fun getProductIcon(productId: String, size: IconProductSize): Icon {
    return TestImportService.getProductIcon(size)
  }

}

class TestProduct(override val id: String,
                  override val name: String,
                  override val version: String,
                  override val lastUsage: Date) : Product {

}