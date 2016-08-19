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
package com.intellij.credentialStore.macOs

import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.LOG
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

val isMacOsCredentialStoreSupported: Boolean
  get() = SystemInfo.isMacIntel64 && SystemInfo.isMacOSLeopard

private val LIBRARY by lazy {
  Native.loadLibrary("Security", MacOsKeychainLibrary::class.java) as MacOsKeychainLibrary
}

private const val errSecItemNotFound = -25300
private const val errSecInvalidRecord = -67701

internal class KeyChainCredentialStore(serviceName: String) : CredentialStore {
  private val serviceName = serviceName.toByteArray()

  override fun get(key: String): String? {
    return findGenericPassword(serviceName, key)
  }

  override fun set(key: String, password: ByteArray?) {
    if (password == null) {
      deleteGenericPassword(serviceName, key)
      return
    }

    saveGenericPassword(serviceName, key, password, password.size)
    password.fill(0)
  }
}

fun findGenericPassword(serviceName: ByteArray, accountName: String): String? {
  val accountNameBytes = accountName.toByteArray()
  val passwordSize = IntArray(1)
  val passwordData = arrayOf<Pointer?>(null)
  checkForError("find", LIBRARY.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes.size, accountNameBytes, passwordSize, passwordData))
  val pointer = passwordData[0] ?: return null

  val result = String(pointer.getByteArray(0, passwordSize[0]))
  LIBRARY.SecKeychainItemFreeContent(null, pointer)
  return result
}

fun deleteGenericPassword(serviceName: ByteArray, accountName: String) {
  val itemRef = arrayOf<Pointer?>(null)
  val accountNameBytes = accountName.toByteArray()
  val code = LIBRARY.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes.size, accountNameBytes, null, null, itemRef)
  if (code == errSecItemNotFound || code == errSecInvalidRecord) {
    return
  }

  checkForError("find (for delete)", code)
  val pointer = itemRef.get(0)
  if (pointer != null) {
    checkForError("delete", LIBRARY.SecKeychainItemDelete(pointer))
    LIBRARY.CFRelease(pointer)
  }
}

// https://developer.apple.com/library/mac/documentation/Security/Reference/keychainservices/index.html
// It is very, very important to use CFRelease/SecKeychainItemFreeContent You must do it, otherwise you can get "An invalid record was encountered."
private interface MacOsKeychainLibrary : Library {
  fun SecKeychainAddGenericPassword(keychain: Pointer?, serviceNameLength: Int, serviceName: ByteArray, accountNameLength: Int, accountName: ByteArray, passwordLength: Int, passwordData: ByteArray, itemRef: Pointer? = null): Int

  fun SecKeychainItemModifyContent(/*SecKeychainItemRef*/ itemRef: Pointer, /*SecKeychainAttributeList**/ attrList: Pointer?, length: Int, data: ByteArray): Int

  fun SecKeychainFindGenericPassword(keychainOrArray: Pointer?,
                                            serviceNameLength: Int,
                                            serviceName: ByteArray,
                                            accountNameLength: Int,
                                            accountName: ByteArray,
                                            passwordLength: IntArray? = null,
                                            passwordData: Array<Pointer?>? = null,
                                            itemRef: Array<Pointer?/*SecKeychainItemRef*/>? = null): Int

  fun SecKeychainItemDelete(itemRef: Pointer): Int

  fun /*CFString*/ SecCopyErrorMessageString(status: Int, reserved: Pointer?): Pointer?

  // http://developer.apple.com/library/mac/#documentation/CoreFoundation/Reference/CFStringRef/Reference/reference.html

  fun /*CFIndex*/ CFStringGetLength(/*CFStringRef*/ theString: Pointer): Long

  fun /*UniChar*/ CFStringGetCharacterAtIndex(/*CFStringRef*/ theString: Pointer, /*CFIndex*/ idx: Long): Char

  fun CFRelease(/*CFTypeRef*/ cf: Pointer)

  fun SecKeychainItemFreeContent(/*SecKeychainAttributeList*/attrList: Pointer?, data: Pointer?)
}

fun saveGenericPassword(serviceName: ByteArray, accountName: String, password: ByteArray, passwordSize: Int = password.size) {
  val accountNameBytes = accountName.toByteArray()
  val itemRef = arrayOf<Pointer?>(null)
  val library = LIBRARY
  checkForError("find (for save)", library.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes.size, accountNameBytes, null, null, itemRef))
  val pointer = itemRef[0]
  if (pointer == null) {
    checkForError("save (new)", library.SecKeychainAddGenericPassword(null, serviceName.size, serviceName, accountNameBytes.size, accountNameBytes, passwordSize, password))
  }
  else {
    checkForError("save (update)", library.SecKeychainItemModifyContent(pointer, null, passwordSize, password))
    library.CFRelease(pointer)
  }
}

private fun checkForError(message: String, code: Int) {
  if (code == 0 || code == errSecItemNotFound) {
    return
  }

  val translated = LIBRARY.SecCopyErrorMessageString(code, null)
  val builder = StringBuilder(message).append(": ")
  if (translated == null) {
    builder.append(code)
  }
  else {
    val buf = CharArray(LIBRARY.CFStringGetLength(translated).toInt())
    for (i in 0..buf.size - 1) {
      buf[i] = LIBRARY.CFStringGetCharacterAtIndex(translated, i.toLong())
    }
    LIBRARY.CFRelease(translated)
    builder.append(buf).append(" (").append(code).append(')')
  }
  LOG.error(builder.toString())
}