/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.configurationStore

import com.intellij.openapi.util.JDOMBuilder.attr
import com.intellij.openapi.util.JDOMBuilder.tag
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.jdom.Element

class XmlElementStorageTest : LightPlatformTestCase() {
  public fun testGetStateSucceeded() {
    val storage = MyXmlElementStorage(tag("root", tag("component", attr("name", "test"), tag("foo"))))
    val state = storage.getState(this, "test", javaClass<Element>(), null)
    TestCase.assertNotNull(state)
    TestCase.assertEquals("component", state.getName())
    TestCase.assertNotNull(state.getChild("foo"))
  }

  public fun testGetStateNotSucceeded() {
    val storage = MyXmlElementStorage(tag("root"))
    val state = storage.getState(this, "test", javaClass<Element>(), null)
    TestCase.assertNull(state)
  }

  public fun testSetStateOverridesOldState() {
    val storage = MyXmlElementStorage(tag("root", tag("component", attr("name", "test"), tag("foo"))))
    val newState = tag("component", attr("name", "test"), tag("bar"))
    val externalizationSession = storage.startExternalization()!!
    externalizationSession.setState(this, "test", newState, null)
    externalizationSession.createSaveSession()!!.save()
    TestCase.assertNotNull(storage.mySavedElement)
    TestCase.assertNotNull(storage.mySavedElement!!.getChild("component").getChild("bar"))
    TestCase.assertNull(storage.mySavedElement!!.getChild("component").getChild("foo"))
  }


  private class MyXmlElementStorage(private val myElement: Element) : XmlElementStorage("", "root", null, null, null) {
    var mySavedElement: Element? = null

    override fun loadLocalData() = myElement

    override fun createSaveSession(storageData: StorageData) = object : XmlElementStorage.XmlElementStorageSaveSession<MyXmlElementStorage>(storageData, this) {
      override fun saveLocally(element: Element?) {
        mySavedElement = element?.clone()
      }
    }
  }
}
