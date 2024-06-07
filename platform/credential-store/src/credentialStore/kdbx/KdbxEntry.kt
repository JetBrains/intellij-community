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

import com.intellij.util.text.nullize
import org.jdom.Element

internal class KdbxEntry(internal val entryElement: Element, private val database: KeePassDatabase, @Volatile internal var group: KdbxGroup?) {
  var title: String?
    get() = getProperty(KdbxEntryElementNames.title)
    set(value) {
      setProperty(entryElement, value, KdbxEntryElementNames.title)
    }

  var userName: String?
    get() = getProperty(KdbxEntryElementNames.userName)
    set(value) {
      setProperty(entryElement, value, KdbxEntryElementNames.userName)
    }

  @Synchronized
  private fun getProperty(propertyName: String): String? {
    val valueElement = getPropertyElement(entryElement, propertyName)?.getChild(KdbxEntryElementNames.value)
    if (valueElement == null) {
      return null
    }

    val value = valueElement.text.nullize()
    if (isValueProtected(valueElement)) {
      throw UnsupportedOperationException("$propertyName protection is not supported")
    }
    else {
      return value
    }
  }

  @Synchronized
  private fun setProperty(entryElement: Element, value: String?, propertyName: String): Element? {
    val normalizedValue = value.nullize()
    var propertyElement = getPropertyElement(entryElement, propertyName)
    if (propertyElement == null) {
      if (normalizedValue == null) {
        return null
      }

      propertyElement = createPropertyElement(entryElement, propertyName)
    }

    val valueElement = propertyElement.getOrCreateChild(KdbxEntryElementNames.value)
    if (valueElement.text.nullize() == normalizedValue) {
      return null
    }

    valueElement.text = value
    if (entryElement === this.entryElement) {
      touch()
    }
    return valueElement
  }

  var password: SecureString?
    @Synchronized
    get() {
      val valueElement = getPropertyElement(entryElement, KdbxEntryElementNames.password)?.getChild(KdbxEntryElementNames.value) ?: return null
      val value = valueElement.content.firstOrNull() ?: return null
      if (value is SecureString) {
        return value
      }

      // if value was not originally protected, protect it
      valueElement.setAttribute(KdbxAttributeNames.protected, "True")
      val result = UnsavedProtectedValue(database.protectValue(value.value))
      valueElement.setContent(result)
      return result
    }
    @Synchronized
    set(value) {
      if (value == null) {
        val iterator = entryElement.getChildren(KdbxEntryElementNames.string).iterator()
        for (element in iterator) {
          if (element.getChildText(KdbxEntryElementNames.key) == KdbxEntryElementNames.password) {
            iterator.remove()
            touch()
          }
        }
        return
      }

      val valueElement = getOrCreatePropertyElement(KdbxEntryElementNames.password).getOrCreateChild(KdbxEntryElementNames.value)
      valueElement.setAttribute(KdbxAttributeNames.protected, "True")
      val oldValue = valueElement.content.firstOrNull()
      if (oldValue === value) {
        return
      }

      valueElement.setContent(UnsavedProtectedValue(value as StringProtectedByStreamCipher))
      touch()
    }

  private fun getOrCreatePropertyElement(@Suppress("SameParameterValue") name: String) = getPropertyElement(entryElement, name) ?: createPropertyElement(entryElement, name)

  @Synchronized
  private fun touch() {
    entryElement.getOrCreateChild("Times").getOrCreateChild("LastModificationTime").text = formattedNow()
    database.isDirty = true
  }
}

private fun getPropertyElement(element: Element, name: String): Element? {
  return element.getChildren(KdbxEntryElementNames.string).firstOrNull { it.getChildText(KdbxEntryElementNames.key) == name }
}

private fun createPropertyElement(parentElement: Element, propertyName: String): Element {
  val propertyElement = Element(KdbxEntryElementNames.string)
  propertyElement.addContent(Element(KdbxEntryElementNames.key).setText(propertyName))
  parentElement.addContent(propertyElement)
  return propertyElement
}