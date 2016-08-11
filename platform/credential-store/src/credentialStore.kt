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
package com.intellij.credentialStore

import com.intellij.openapi.diagnostic.Logger
import java.security.MessageDigest
import java.util.*

internal val LOG = Logger.getInstance(CredentialStore::class.java)

internal interface CredentialStore {
  fun get(key: String): String?

  // passed byte array will be cleared
  fun set(key: String, password: ByteArray?)
}

internal fun getRawKey(key: String, requestor: Class<*>?) = if (requestor == null) key else "${requestor.name}/$key"

internal fun toOldKey(hash: ByteArray) = "old-hashed-key|" + Base64.getEncoder().encodeToString(hash)

internal fun toOldKey(newKey: String) = toOldKey(MessageDigest.getInstance("SHA-256").digest(newKey.toByteArray()))