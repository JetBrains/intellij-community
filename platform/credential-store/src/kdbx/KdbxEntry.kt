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

import org.linguafranca.pwdb.kdbx.dom.DomHelper
import org.w3c.dom.Element

import java.text.ParseException
import java.util.*
import kotlin.reflect.KProperty

class KdbxEntry(val element: Element, internal val database: KeePassDatabase, newElement: Boolean) : AbstractEntry() {
  companion object {
    internal var mandatoryEntryElements: Map<String, DomHelper.ValueCreator> = object : HashMap<String, DomHelper.ValueCreator>() {
      init {
        put(DomHelper.UUID_ELEMENT_NAME, DomHelper.UuidValueCreator())
        put(DomHelper.ICON_ELEMENT_NAME, DomHelper.ConstantValueCreator("2"))
        put(DomHelper.TIMES_ELEMENT_NAME, DomHelper.ConstantValueCreator(""))
        put(DomHelper.LAST_MODIFICATION_TIME_ELEMENT_NAME, DomHelper.DateValueCreator())
        put(DomHelper.CREATION_TIME_ELEMENT_NAME, DomHelper.DateValueCreator())
        put(DomHelper.LAST_ACCESS_TIME_ELEMENT_NAME, DomHelper.DateValueCreator())
        put(DomHelper.EXPIRY_TIME_ELEMENT_NAME, DomHelper.DateValueCreator())
        put(DomHelper.EXPIRES_ELEMENT_NAME, DomHelper.ConstantValueCreator("False"))
        put(DomHelper.USAGE_COUNT_ELEMENT_NAME, DomHelper.ConstantValueCreator("0"))
        put(DomHelper.LOCATION_CHANGED, DomHelper.DateValueCreator())
      }
    }
  }

  init {
    if (newElement) {
      DomHelper.ensureElements(element, mandatoryEntryElements)
      ensureProperty("Notes")
      ensureProperty("Title")
      ensureProperty("URL")
      ensureProperty("UserName")
      ensureProperty("Password")
    }
  }

  override fun getProperty(name: String): String? {
    val property = DomHelper.getElement(String.format(DomHelper.PROPERTY_ELEMENT_FORMAT, name), element, false) ?: return null
    return DomHelper.getElementContent(DomHelper.VALUE_ELEMENT_NAME, property)
  }

  override fun setProperty(name: String, value: String?) {
    var property = DomHelper.getElement(String.format(DomHelper.PROPERTY_ELEMENT_FORMAT, name), element, false)
    if (property == null) {
      property = DomHelper.newElement("String", element)
      DomHelper.setElementContent("Key", property, name)
    }
    DomHelper.setElementContent(DomHelper.VALUE_ELEMENT_NAME, property, value)
    DomHelper.touchElement(DomHelper.LAST_MODIFICATION_TIME_ELEMENT_NAME, element)
    database.isDirty = true
  }

  override val propertyNames: List<String>
    get() {
      val result = ArrayList<String>()
      val list = DomHelper.getElements("String", element)
      for (listElement in list) {
        DomHelper.getElementContent("Key", listElement)?.let {
          result.add(it)
        }
      }
      return result
    }

  private fun ensureProperty(name: String) {
    val property = DomHelper.getElement(String.format(DomHelper.PROPERTY_ELEMENT_FORMAT, name), element, false)
    if (property == null) {
      val container = DomHelper.newElement("String", element)
      DomHelper.setElementContent("Key", container, name)
      DomHelper.getElement("Value", container, true)
    }
  }

  override val parent: KdbxGroup?
    get() = (element.parentNode as Element?)?.let { KdbxGroup(it, database, false) }

  override val uuid: UUID
    get() = DomHelper.uuidFromBase64(DomHelper.getElementContent(DomHelper.UUID_ELEMENT_NAME, element)!!)

  override var userName: String? by PropertyDelegate("UserName")

  override var password: String? by PropertyDelegate("Password")

  override var url: String? by PropertyDelegate("URL")

  override var title: String? by PropertyDelegate("Title")

  override var notes: String? by PropertyDelegate("Notes")

  override var icon: Icon?
    get() = DomIconWrapper(DomHelper.getElement(DomHelper.ICON_ELEMENT_NAME, element, false)!!)
    set(value) {
      DomHelper.getElement(DomHelper.ICON_ELEMENT_NAME, element, true)!!.textContent = value!!.index.toString()
      DomHelper.touchElement(DomHelper.LAST_MODIFICATION_TIME_ELEMENT_NAME, element)
      database.isDirty = true
    }

  override val lastAccessTime: Date
    get() = getTime(DomHelper.LAST_ACCESS_TIME_ELEMENT_NAME)

  private fun getTime(name: String): Date {
    DomHelper.getElementContent(name, element)?.let {
      try {
        DomHelper.dateFormatter.parse(it)
      }
      catch (e: ParseException) {
        return Date(0)
      }
    }
    return Date(0)
  }

  override val creationTime: Date
    get() = getTime(DomHelper.CREATION_TIME_ELEMENT_NAME)

  override val expiryTime: Date
    get() = getTime(DomHelper.EXPIRY_TIME_ELEMENT_NAME)

  override val lastModificationTime: Date
    get() = getTime(DomHelper.LAST_MODIFICATION_TIME_ELEMENT_NAME)

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
}

private class PropertyDelegate(private val name: String) {
  operator fun getValue(thisRef: KdbxEntry, property: KProperty<*>) = thisRef.getProperty(name)

  operator fun setValue(thisRef: KdbxEntry, property: KProperty<*>, value: String?) = thisRef.setProperty(name, value)
}