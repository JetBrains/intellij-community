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

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ArrayUtilRt
import com.sun.jna.*
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import gnu.trove.TIntObjectHashMap

val isMacOsCredentialStoreSupported: Boolean
  get() = SystemInfo.isMacIntel64 && SystemInfo.isMacOSLeopard

private val LIBRARY by lazy { Native.loadLibrary("Security", MacOsKeychainLibrary::class.java) }

private const val errSecSuccess = 0
private const val errSecItemNotFound = -25300
private const val errSecInvalidRecord = -67701
// or if Deny clicked on access dialog
private const val errUserNameNotCorrect = -25293
private const val kSecFormatUnknown = 0
private const val kSecAccountItemAttr = (('a'.toInt() shl 8 or 'c'.toInt()) shl 8 or 'c'.toInt()) shl 8 or 't'.toInt()

internal class KeyChainCredentialStore() : CredentialStore {
  override fun get(attributes: CredentialAttributes): Credentials? {
    return findGenericPassword(attributes.serviceName.toByteArray(), attributes.userName)
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    val serviceName = attributes.serviceName.toByteArray()
    if (credentials.isEmpty()) {
      val itemRef = PointerByReference()
      val userName = attributes.userName?.toByteArray()
      val code = LIBRARY.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, userName?.size ?: 0, userName, null, null, itemRef)
      if (code == errSecItemNotFound || code == errSecInvalidRecord) {
        return
      }

      checkForError("find (for delete)", code)
      itemRef.value?.let {
        checkForError("delete", LIBRARY.SecKeychainItemDelete(it))
        LIBRARY.CFRelease(it)
      }
      return
    }

    val userName = (attributes.userName ?: credentials!!.userName)?.toByteArray()
    val searchUserName = if (attributes.serviceName == SERVICE_NAME_PREFIX) userName else null
    val itemRef = PointerByReference()
    val library = LIBRARY
    checkForError("find (for save)", library.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, searchUserName?.size ?: 0, searchUserName, null, null, itemRef))

    val password = if (attributes.isPasswordMemoryOnly || credentials!!.password == null) null else credentials.password!!.toByteArray(false)
    val pointer = itemRef.value
    if (pointer == null) {
      checkForError("save (new)", library.SecKeychainAddGenericPassword(null, serviceName.size, serviceName, userName?.size ?: 0, userName, password?.size ?: 0, password))
    }
    else {
      val attribute = SecKeychainAttribute()
      attribute.tag = kSecAccountItemAttr
      attribute.length = userName?.size ?: 0
      if (userName != null) {
        val userNamePointer = Memory(userName.size.toLong())
        userNamePointer.write(0, userName, 0, userName.size)
        attribute.data = userNamePointer
      }

      val attributeList = SecKeychainAttributeList()
      attributeList.count = 1
      attribute.write()
      attributeList.attr = attribute.pointer
      checkForError("save (update)", library.SecKeychainItemModifyContent(pointer, attributeList, password?.size ?: 0, password ?: ArrayUtilRt.EMPTY_BYTE_ARRAY))
      library.CFRelease(pointer)
    }

    password?.fill(0)
  }
}

fun findGenericPassword(serviceName: ByteArray, accountName: String?): Credentials? {
  val accountNameBytes = accountName?.toByteArray()
  val passwordSize = IntArray(1)
  val passwordRef = PointerByReference()
  val itemRef = PointerByReference()
  checkForError("find", LIBRARY.SecKeychainFindGenericPassword(null, serviceName.size, serviceName, accountNameBytes?.size ?: 0, accountNameBytes, passwordSize, passwordRef, itemRef))

  val pointer = passwordRef.value ?: return null
  val password = OneTimeString(pointer.getByteArray(0, passwordSize.get(0)))
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

// https://developer.apple.com/library/mac/documentation/Security/Reference/keychainservices/index.html
// It is very, very important to use CFRelease/SecKeychainItemFreeContent You must do it, otherwise you can get "An invalid record was encountered."
private interface MacOsKeychainLibrary : Library {
  fun SecKeychainAddGenericPassword(keychain: Pointer?, serviceNameLength: Int, serviceName: ByteArray, accountNameLength: Int, accountName: ByteArray?, passwordLength: Int, passwordData: ByteArray?, itemRef: Pointer? = null): Int

  fun SecKeychainItemModifyContent(itemRef: Pointer, /*SecKeychainAttributeList**/ attrList: Any?, length: Int, data: ByteArray?): Int

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

  if (code == errUserNameNotCorrect || code == -25299 /* The specified item already exists in the keychain */) {
    LOG.warn(builder.toString())
  }
  else {
    LOG.error(builder.toString())
  }
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

  constructor() : super() {
  }

  override fun getFieldOrder() = listOf("count", "attr")
}

internal class SecKeychainAttribute : Structure, Structure.ByReference {
  @JvmField
  var tag = 0
  @JvmField
  var length = 0
  @JvmField
  var data: Pointer? = null

  internal constructor(p: Pointer) : super(p) {
  }

  internal constructor() : super() {
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