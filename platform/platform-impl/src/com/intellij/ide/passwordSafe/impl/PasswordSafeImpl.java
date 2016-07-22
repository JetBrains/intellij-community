/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.passwordSafe.impl

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings
import com.intellij.ide.passwordSafe.impl.providers.masterKey.PasswordDatabase
import com.intellij.ide.passwordSafe.impl.providers.memory.MemoryPasswordSafe
import com.intellij.ide.passwordSafe.impl.providers.nil.NilProvider
import com.intellij.ide.passwordSafe.masterKey.*
import com.intellij.ide.passwordSafe.masterKey.FilePasswordSafeProvider
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class PasswordSafeImpl(val settings: PasswordSafeSettings) : PasswordSafe(), SettingsSavingComponent {
  private val myMasterKeyProvider: FilePasswordSafeProvider
  private val myNilProvider: NilProvider

  init {
    //noinspection deprecation
    myMasterKeyProvider = FilePasswordSafeProvider(convertOldDb(ServiceManager.getService<PasswordDatabase>(PasswordDatabase::class.java!!)))
    myNilProvider = NilProvider()
  }

  /**
   * @return get currently selected provider
   */
  private fun provider(): PasswordSafeProvider {
    var p: PasswordSafeProvider? = null
    when (settings.getProviderType()) {
      PasswordSafeSettings.ProviderType.DO_NOT_STORE -> p = myNilProvider
      PasswordSafeSettings.ProviderType.MEMORY_ONLY, PasswordSafeSettings.ProviderType.MASTER_PASSWORD -> p = myMasterKeyProvider
      else -> LOG.error("Unknown provider type: " + settings.getProviderType())
    }
    return p
  }

  public override fun getPassword(project: Project?, requester: Class<*>?, key: String): String? {
    if (settings.getProviderType() == PasswordSafeSettings.ProviderType.MASTER_PASSWORD) {
      var password = memoryProvider.getPassword(project, requester, key)
      if (password == null) {
        password = provider().getPassword(project, requester, key)
        if (password != null) {
          // cache the password in memory as well for easier access during the session
          memoryProvider.storePassword(project, requester, key, password)
        }
      }
      return password
    }
    return provider().getPassword(project, requester, key)
  }

  public override fun removePassword(project: Project?, requester: Class<*>?, key: String) {
    if (settings.getProviderType() == PasswordSafeSettings.ProviderType.MASTER_PASSWORD) {
      memoryProvider.removePassword(project, requester, key)
    }
    provider().removePassword(project, requester, key)
  }

  public override fun storePassword(project: Project?, requestor: Class<*>?, key: String, value: String?) {
    if (settings.getProviderType() == PasswordSafeSettings.ProviderType.MASTER_PASSWORD) {
      memoryProvider.storePassword(project, requestor, key, value)
    }
    provider().storePassword(project, requestor, key, value)
  }

  val masterKeyProvider: PasswordSafeProvider
    get() {
      return myMasterKeyProvider
    }

  val memoryProvider: MemoryPasswordSafe
    get() {
      return myMemoryProvider
    }

  public override fun save() {
    myMasterKeyProvider.save()
  }

  companion object {
    private val LOG = Logger.getInstance(PasswordSafeImpl::class.java!!)
  }
}
