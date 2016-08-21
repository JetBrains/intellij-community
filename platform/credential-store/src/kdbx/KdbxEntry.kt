/*
 * Copyright 2015 Jo Rabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.credentialStore.kdbx

import com.intellij.util.element
import com.intellij.util.getOrCreate
import org.jdom.Element
import java.time.format.DateTimeParseException
import java.util.*
import kotlin.reflect.KProperty

private const val VALUE_ELEMENT_NAME = "Value"

class KdbxEntry(val element: Element, internal val database: KeePassDatabase) {
  fun getProperty(name: String) = getPropertyContainer(name)?.getChildText(VALUE_ELEMENT_NAME)

  fun setProperty(name: String, value: String?) {
    var item = getPropertyContainer(name)
    if (item == null) {
      item = element.element("String")
      item.element("Key").addContent(name)
    }

    item.getOrCreate(VALUE_ELEMENT_NAME).text = value
    touch()
    database.isDirty = true
  }

  internal fun ensureProperty(name: String) {
    val property = getPropertyContainer(name)
    if (property == null) {
      val container = element.element("String")
      container.element("Key").addContent(name)
      container.element("Value")
    }
  }

  val parent: KdbxGroup?
    get() = element.parentElement?.let { KdbxGroup(it, database) }

  val uuid: UUID
    get() = uuidFromBase64(element.getChildText(UUID_ELEMENT_NAME)!!)

  var userName: String? by PropertyDelegate("UserName")

  var password: String? by PropertyDelegate("Password")

  var url: String? by PropertyDelegate("URL")

  var title: String? by PropertyDelegate("Title")

  var notes: String? by PropertyDelegate("Notes")

  var icon: Icon?
    get() = DomIconWrapper(element.getChild(ICON_ELEMENT_NAME)!!)
    set(value) {
      element.getOrCreate(ICON_ELEMENT_NAME).text = value!!.index.toString()
      touch()
      database.isDirty = true
    }

  val lastAccessTime: Date
    get() = getTime(LAST_ACCESS_TIME_ELEMENT_NAME)

  val creationTime: Date
    get() = getTime(CREATION_TIME_ELEMENT_NAME)

  val expiryTime: Date
    get() = getTime(EXPIRY_TIME_ELEMENT_NAME)

  val lastModificationTime: Date
    get() = getTime(LAST_MODIFICATION_TIME_ELEMENT_NAME)

  private fun getTime(name: String): Date {
    element.getChildText(name)?.let {
      try {
        dateFormatter.parse(it)
      }
      catch (e: DateTimeParseException) {
        return Date(0)
      }
    }
    return Date(0)
  }

  private fun getPropertyContainer(name: String): Element? {
    for (element in element.getChildren("String")) {
      if (element.getChildText("Key") == name) {
        return element
      }
    }
    return null
  }

  private fun touch() {
    element.getOrCreate("Times").getOrCreate("LastModificationTime").text = formattedNow()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val that = other as KdbxEntry?
    return element == that!!.element && database == that.database
  }

  override fun hashCode(): Int {
    var result = element.hashCode()
    result = 31 * result + database.hashCode()
    return result
  }

  val path: String
    get() {
      val parent = this.parent
      var result = ""
      if (parent != null) {
        result = parent.path
      }
      return result + title
    }

  override fun toString() = this.path
}

private class PropertyDelegate(private val name: String) {
  operator fun getValue(thisRef: KdbxEntry, property: KProperty<*>) = thisRef.getProperty(name)

  operator fun setValue(thisRef: KdbxEntry, property: KProperty<*>, value: String?) = thisRef.setProperty(name, value)
}