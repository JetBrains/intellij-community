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

import com.intellij.credentialStore.*
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.*
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import gnu.trove.TIntObjectHashMap

val isMacOsCredentialStoreSupported: Boolean
  get() = SystemInfo.isMacIntel64 && SystemInfo.isMacOSLeopard

private val LIBRARY by lazy {
  Native.loadLibrary("Security", MacOsKeychainLibrary::class.java) as MacOsKeychainLibrary
}

private const val errSecSuccess = 0
private const val errSecItemNotFound = -25300
private const val errSecInvalidRecord = -67701
private const val kSecFormatUnknown = 0
private const val kSecAccountItemAttr = (('a'.toInt() shl 8 or 'c'.toInt()) shl 8 or 'c'.toInt()) shl 8 or 't'.toInt()

internal class KeyChainCredentialStore() : CredentialStore {
  override fun get(attributes: CredentialAttributes): Credentials? {
    return findGenericPassword(attributes.serviceName.toByteArray(), attributes.accountName)
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    if (credentials.isEmpty()) {
      deleteGenericPassword(attributes.serviceName.toByteArray(), attributes.accountName!!)
      return
    }

    val password = credentials!!.password!!.toByteArray()
    saveGenericPassword(attributes.serviceName.toByteArray(), attributes.accountName ?: credentials.user, password, password.size)
    password.fill(0)
  }
}

fun findGenericPassword(serviceName: ByteArray, accountName: String?): Credentials? {
  val accountNameBytes = accountName?.toByteArray()
  val passwordSize = IntArray(1)
  val passwordRef = PointerByReference()
  val itemRef = PointerByReference()
  checkForError("find", LIBRARY.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes?.size ?: 0, accountNameBytes, passwordSize, passwordRef, itemRef))

  val pointer = passwordRef.value ?: return null
  val password = String(pointer.getByteArray(0, passwordSize.get(0)))
  LIBRARY.SecKeychainItemFreeContent(null, pointer)

  var effectiveAccountName = accountName
  if (effectiveAccountName == null) {
    val attributes = PointerByReference()
    checkForError("SecKeychainItemCopyAttributesAndData", LIBRARY.SecKeychainItemCopyAttributesAndData(itemRef.value!!, SecKeychainAttributeInfo(kSecAccountItemAttr), null, attributes, null, null))
    val attributeList = SecKeychainAttributeList(attributes.value)
    try {
      attributeList.read()
      effectiveAccountName = readAttributes(attributeList).get(kSecAccountItemAttr)
    }
    finally {
      LIBRARY.SecKeychainItemFreeAttributesAndData(attributeList, null)
    }
  }
  return Credentials(effectiveAccountName, password)
}

fun deleteGenericPassword(serviceName: ByteArray, accountName: String) {
  val itemRef = PointerByReference()
  val accountNameBytes = accountName.toByteArray()
  val code = LIBRARY.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes.size, accountNameBytes, null, null, itemRef)
  if (code == errSecItemNotFound || code == errSecInvalidRecord) {
    return
  }

  checkForError("find (for delete)", code)
  val pointer = itemRef.value
  if (pointer != null) {
    checkForError("delete", LIBRARY.SecKeychainItemDelete(pointer))
    LIBRARY.CFRelease(pointer)
  }
}

// https://developer.apple.com/library/mac/documentation/Security/Reference/keychainservices/index.html
// It is very, very important to use CFRelease/SecKeychainItemFreeContent You must do it, otherwise you can get "An invalid record was encountered."
private interface MacOsKeychainLibrary : Library {
  fun SecKeychainAddGenericPassword(keychain: Pointer?, serviceNameLength: Int, serviceName: ByteArray, accountNameLength: Int, accountName: ByteArray?, passwordLength: Int, passwordData: ByteArray, itemRef: Pointer? = null): Int

  fun SecKeychainItemModifyContent(itemRef: Pointer, /*SecKeychainAttributeList**/ attrList: Pointer?, length: Int, data: ByteArray): Int

  fun SecKeychainFindGenericPassword(keychainOrArray: Pointer?,
                                     serviceNameLength: Int,
                                     serviceName: ByteArray,
                                     accountNameLength: Int,
                                     accountName: ByteArray?,
                                     passwordLength: IntArray?,
                                     passwordData: PointerByReference?,
                                     itemRef: PointerByReference?): Int

  fun  SecKeychainItemCopyAttributesAndData(itemRef: Pointer,
                                            info: SecKeychainAttributeInfo,
                                            itemClass: IntByReference?,
                                            attrList: PointerByReference,
                                            length: IntByReference?,
                                            outData: PointerByReference?): Int

  fun SecKeychainItemFreeAttributesAndData(attrList: SecKeychainAttributeList, data: Pointer?): Int

  fun SecKeychainItemDelete(itemRef: Pointer): Int

  fun /*CFString*/ SecCopyErrorMessageString(status: Int, reserved: Pointer?): Pointer?

  // http://developer.apple.com/library/mac/#documentation/CoreFoundation/Reference/CFStringRef/Reference/reference.html

  fun /*CFIndex*/ CFStringGetLength(/*CFStringRef*/ theString: Pointer): Long

  fun /*UniChar*/ CFStringGetCharacterAtIndex(/*CFStringRef*/ theString: Pointer, /*CFIndex*/ idx: Long): Char

  fun CFRelease(/*CFTypeRef*/ cf: Pointer)

  fun SecKeychainItemFreeContent(/*SecKeychainAttributeList*/attrList: Pointer?, data: Pointer?)
}

internal class SecKeychainAttributeInfo : Structure() {
  @JvmField
  var count: Int = 0
  @JvmField
  var tag: Pointer? = null
  @JvmField
  var format: Pointer? = null

  override fun getFieldOrder() = listOf("count", "tag", "format")
}

fun saveGenericPassword(serviceName: ByteArray, accountName: String?, password: ByteArray, passwordSize: Int = password.size) {
  val accountNameBytes = accountName?.toByteArray()
  val itemRef = PointerByReference()
  val library = LIBRARY
  checkForError("find (for save)", library.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes?.size ?: 0, accountNameBytes, null, null, itemRef))
  val pointer = itemRef.value
  if (pointer == null) {
    checkForError("save (new)", library.SecKeychainAddGenericPassword(null, serviceName.size, serviceName, accountNameBytes?.size ?: 0, accountNameBytes, passwordSize, password))
  }
  else {
    checkForError("save (update)", library.SecKeychainItemModifyContent(pointer, null, passwordSize, password))
    library.CFRelease(pointer)
  }
}

private fun checkForError(message: String, code: Int) {
  if (code == errSecSuccess || code == errSecItemNotFound) {
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

internal fun SecKeychainAttributeInfo(vararg ids: Int): SecKeychainAttributeInfo {
  val info = SecKeychainAttributeInfo()
  val length = ids.size
  info.count = length
  val size = length shl 2
  val tag = Memory((size shl 1).toLong())
  val format = tag.share(size.toLong(), size.toLong())
  info.tag = tag
  info.format = format
  var offset = 0
  for (id in ids) {
    tag.setInt(offset.toLong(), id)
    format.setInt(offset.toLong(), kSecFormatUnknown)
    offset += 4
  }
  return info
}

internal class SecKeychainAttributeList : Structure {
  @JvmField
  var count = 0
  @JvmField
  var attr: Pointer? = null

  constructor(p: Pointer) : super(p) {
  }

  override fun getFieldOrder() = listOf("count", "attr")
}

internal class SecKeychainAttribute : Structure {
  @JvmField
  var tag = 0
  @JvmField
  var length = 0
  @JvmField
  var data: Pointer? = null

  internal constructor(p: Pointer) : super(p) {
  }

  override fun getFieldOrder() = listOf("tag", "length", "data")
}

private fun readAttributes(list: SecKeychainAttributeList): TIntObjectHashMap<String> {
  val map = TIntObjectHashMap<String>()
  val attrList = SecKeychainAttribute(list.attr!!)
  attrList.read()
  @Suppress("UNCHECKED_CAST")
  for (attr in attrList.toArray(list.count) as Array<SecKeychainAttribute>) {
    val data = attr.data ?: continue
    map.put(attr.tag, String(data.getByteArray(0, attr.length)))
  }
  return map
}