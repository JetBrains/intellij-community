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

import com.intellij.credentialStore.SecureString
import com.intellij.util.element
import com.intellij.util.getOrCreate
import org.jdom.Element

private const val VALUE_ELEMENT_NAME = "Value"

class KdbxEntry(private val element: Element, private val database: KeePassDatabase, internal @Volatile var group: KdbxGroup?) {
  @Volatile var title: String? = element.removeProperty("Title")
    set(value) {
      if (field != value) {
        field = value
        touch()
        database.isDirty = true
      }
    }

  @Volatile var userName: String? = element.removeProperty("UserName")
    set(value) {
      if (field != value) {
        field = value
        touch()
        database.isDirty = true
      }
    }

  @Volatile var password: SecureString? =  element.removeProperty("Password")?.let(::SecureString)
    set(value) {
      if (field != value) {
        field = value
        touch()
        database.isDirty = true
      }
    }

  fun toXml(): Element {
    val element = element.clone()
    element.setProperty("Title", title)
    element.ensureProperty("URL")
    element.setProperty("UserName", userName)
    element.setProperty("Password", password?.get()?.toString())
    element.ensureProperty("Notes")
    return element
  }

  private fun touch() {
    element.getOrCreate("Times").getOrCreate("LastModificationTime").text = formattedNow()
  }
}

private fun Element.ensureProperty(name: String) {
  val property = getPropertyContainer(name, false)
  if (property == null) {
    val container = element("String")
    container.element("Key").addContent(name)
    container.element("Value")
  }
}

private fun Element.getPropertyContainer(name: String, remove: Boolean): Element? {
  val iterator = getChildren("String").iterator()
  for (element in iterator) {
    if (element.getChildText("Key") == name) {
      if (remove) {
        iterator.remove()
      }
      return element
    }
  }
  return null
}

private fun Element.removeProperty(name: String) = getPropertyContainer(name, true)?.getChildText(VALUE_ELEMENT_NAME)

private fun Element.setProperty(name: String, value: String?) {
  var item = getPropertyContainer(name, false)
  if (item == null) {
    item = element("String")
    item.element("Key").addContent(name)
  }

  item.getOrCreate(VALUE_ELEMENT_NAME).text = value
}