// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.data

import com.intellij.openapi.components.service
import java.util.*
import javax.swing.Icon

interface SettingsService {
  companion object {
    fun getInstance(): SettingsService = service()
  }
  fun getSyncService(): SyncService
  fun getJbService(): JbService
  fun getExternalService(): ExternalService

  fun skipImport()
}


interface SyncService : BaseJbService {
  enum class SYNC_STATE {
    UNLOGGED,
    WAINING_FOR_LOGIN,
    LOGIN_FAILED,
    LOGGED,
    TURNED_OFF,
    NO_SYNC,
    GENERAL
  }

  val syncState: SYNC_STATE
  fun tryToLogin(): String?
  fun syncSettings(productId: String)
  fun getMainProduct(): Product?
  fun importSettings(productId: String)

  fun generalSync()
}

interface ExternalService : BaseService, ConfigurableImport
interface JbService: BaseJbService, ConfigurableImport {
  fun getConfig(): Config
}

interface BaseJbService : BaseService {
  fun getOldProducts(): List<Product>
}

interface BaseService {

  fun products(): List<Product>
  fun getSettings(itemId: String): List<BaseSetting>

  fun getProductIcon(itemId: String, size: IconProductSize = IconProductSize.SMALL): Icon?
}

interface ConfigurableImport {
  fun importSettings(productId: String, data: List<DataForSave>)
}

enum class IconProductSize(val int: Int) {
  SMALL(20),
  MIDDLE(24),
  LARGE(48)
}


interface Product : ImportItem {
  val version: String /*опять не знаю как мы храним версию, пока написала стринг*/
  val lastUsage: Date /* для сеттинг синга дата последнего синка, для локальных версий дата последнего использования
                      нужны понятия типа вчера, сегодня, неделю назад, месяц наза, много мессяцев назад.
                      Есть у нас где-то такое? */
}

interface Config : ImportItem {
  val path: String /* /IntelliJ IDEA Ultimate 2023.2.1 */
}

interface ImportItem {
  val id: String
  val name: String
}


interface BaseSetting {
  val id: String

  val icon: Icon
  val name: String
  val comment: String?
}

interface Configurable : Multiple {
  /* https://www.figma.com/file/7lzmMqhEETFIxMg7E2EYSF/Import-settings-and-Settings-Sync-UX-2507?node-id=1420%3A237610&mode=dev */
}

interface Multiple : BaseSetting {
  /* это список с настройками данного сеттинга. например кеймапа с ключами. плагины. этот интерфейс обозначает только наличие дочерних настроек.
  для этого интерфейса есть расширение Configurable которое применимо, если В ТЕОРИИ эти настройки можно выбирать.
  в теории потому что для того чтобы показалась выпадашка с выбором нужно чтобы самый верхний сервис предоставлял возможность редактирования.
  например ImportService позвозяет выбирать\редактировать, SettingsService - нет. в случае если редактирование невозможно Configurable -ы
  в диалоге будут выглядеть как Multiple
   https://www.figma.com/file/7lzmMqhEETFIxMg7E2EYSF/Import-settings-and-Settings-Sync-UX-2507?node-id=961%3A169735&mode=dev */
  val list: List<List<ChildSetting>>
}

interface ChildSetting {
  val id: String
  val name: String
  val leftComment: String? /* built-in скетч: https://www.figma.com/file/7lzmMqhEETFIxMg7E2EYSF/Import-settings-and-Settings-Sync-UX-2507?node-id=961%3A169853&mode=dev */
  val rightComment: String? /* hotkey скетч https://www.figma.com/file/7lzmMqhEETFIxMg7E2EYSF/Import-settings-and-Settings-Sync-UX-2507?node-id=961%3A169735&mode=dev*/
}

data class DataForSave(val id: String, val childIds: List<String>? = null)