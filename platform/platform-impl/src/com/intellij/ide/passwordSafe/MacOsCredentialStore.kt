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
package com.intellij.ide.passwordSafe

import com.intellij.ide.passwordSafe.macOs.deleteGenericPassword
import com.intellij.ide.passwordSafe.macOs.findGenericPassword
import com.intellij.ide.passwordSafe.macOs.saveGenericPassword
import com.intellij.openapi.diagnostic.catchAndLog
import java.security.MessageDigest

internal class MacOsCredentialStore(serviceName: String) : PasswordStorage {
  private val serviceName = serviceName.toByteArray()

  override fun getPassword(requestor: Class<*>?, key: String): String? {
    val rawKey = getRawKey(key, requestor)
    // try old key - as hash
    @Suppress("CanBeVal")
    var value: String?
    try {
      value = findGenericPassword(serviceName, rawKey)
    }
    catch (e: Throwable) {
      LOG.error(e)
      return null
    }

    if (value == null) {
      LOG.catchAndLog {
        val oldKey = toOldKey(MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray()))
        value = findGenericPassword(serviceName, oldKey)
        if (value != null) {
          LOG.catchAndLog { deleteGenericPassword(serviceName, oldKey) }
          saveGenericPassword(serviceName, key, value!!)
        }
      }
    }
    return value
  }

  override fun setPassword(requestor: Class<*>?, key: String, value: String?) {
    LOG.catchAndLog {
      val rawKey = getRawKey(key, requestor)
      if (value == null) {
        deleteGenericPassword(serviceName, rawKey)
      }
      else {
        saveGenericPassword(serviceName, rawKey, value)
      }
    }
  }
}