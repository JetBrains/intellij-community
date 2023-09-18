// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.data

import javax.swing.Icon

interface ActionsDataProvider {
  enum class popUpPlace {
    MAIN,
    OTHER
  }

  companion object {

    fun prepareMap(service: BaseJbService): Map<popUpPlace, List<Product>> {
      val fresh = service.products()
      val old = service.getOldProducts()

      if(fresh.isEmpty()) {
        return mutableMapOf<popUpPlace, List<Product>>().apply {
          this[popUpPlace.MAIN] = old
        }
      }

      return mutableMapOf<popUpPlace, List<Product>>().apply {
        this[popUpPlace.MAIN] = fresh
        this[popUpPlace.OTHER] = old
      }
    }
  }
  val settingsService
    get() = SettingsService.getInstance()
  fun getProductIcon(productId: String, size: IconProductSize = IconProductSize.SMALL): Icon?
  fun getText(product: Product): String
  val title: String

  fun getAdditionText(product: Product): String?
  val main: List<Product>?
  val other: List<Product>?
}

class JBrActionsDataProvider private constructor(): ActionsDataProvider {
  companion object {
    private val provider = JBrActionsDataProvider()
    fun getInstance() = provider
  }

  private val service = settingsService.getJbService()
  private var map: Map<ActionsDataProvider.popUpPlace, List<Product>?>? = null

  init {
    val service = settingsService.getJbService()
    map = ActionsDataProvider.prepareMap(service)
  }

  override fun getProductIcon(productId: String, size: IconProductSize): Icon? {
    return service.getProductIcon(productId, size)
  }

  override fun getText(product: Product): String {
    return product.name
  }

  override val title: String
    get() = "JetBrains IDEs"

  override fun getAdditionText(product: Product): String? {
    return null
  }

  override val main: List<Product>?
    get() = map?.get(ActionsDataProvider.popUpPlace.MAIN)
  override val other: List<Product>?
    get() = map?.get(ActionsDataProvider.popUpPlace.OTHER)

}

class SyncActionsDataProvider private constructor() : ActionsDataProvider {
  companion object {
    private val provider = SyncActionsDataProvider()
    fun getInstance() = provider
  }

  private val service = settingsService.getSyncService()
  private var map: Map<ActionsDataProvider.popUpPlace, List<Product>?>? = null

  init {
    val service = settingsService.getJbService()
    map = ActionsDataProvider.prepareMap(service)
  }

  private fun updateSyncMap() {
    val service = settingsService.getSyncService()
    if (service.syncState != SyncService.SYNC_STATE.LOGGED) {
      map = null
      return
    }

    service.getMainProduct()?.let {
      map = mutableMapOf<ActionsDataProvider.popUpPlace, List<Product>>().apply {
        this[ActionsDataProvider.popUpPlace.MAIN] = listOf(it)
        val products = service.products()
        if(products.isNotEmpty()) {
          this[ActionsDataProvider.popUpPlace.OTHER] = products
        }
      }
      return
    }

    map = ActionsDataProvider.prepareMap(service)
  }

  override fun getProductIcon(productId: String, size: IconProductSize): Icon? {
    return service.getProductIcon(productId, size)
  }

  override fun getText(product: Product): String {
    return "${product.name} Setting Sync"
  }

  override fun getAdditionText(product: Product): String? {
    return null
  }

  override val title: String
    get() = "Setting Sync"

  override val main: List<Product>?
    get() {
      if(map == null) {
        updateSyncMap()
      }
      return map?.get(ActionsDataProvider.popUpPlace.MAIN)
    }
  override val other: List<Product>?
    get() {
      if(map == null) {
        updateSyncMap()
      }
      return map?.get(ActionsDataProvider.popUpPlace.OTHER)
    }

}

class ExtActionsDataProvider private constructor() : ActionsDataProvider {
  companion object {
    private val provider = ExtActionsDataProvider()
    fun getInstance() = provider
  }
  private val service = settingsService.getExternalService()

  override fun getProductIcon(productId: String, size: IconProductSize): Icon? {
    return service.getProductIcon(productId, size)
  }

  override fun getText(product: Product): String {
    return product.name
  }

  override fun getAdditionText(product: Product): String? {
    return null
  }

  override val title: String
    get() = ""
  override val main: List<Product>?
    get() = service.products()
  override val other: List<Product>?
    get() = null

}

