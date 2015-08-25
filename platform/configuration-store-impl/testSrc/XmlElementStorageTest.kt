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
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.Test

class XmlElementStorageTest {
  public Test fun testGetStateSucceeded() {
    val storage = MyXmlElementStorage(tag("root", tag("component", attr("name", "test"), tag("foo"))))
    val state = storage.getState(this, "test", javaClass<Element>())
    TestCase.assertNotNull(state)
    TestCase.assertEquals("component", state.getName())
    TestCase.assertNotNull(state.getChild("foo"))
  }

  public Test fun testGetStateNotSucceeded() {
    val storage = MyXmlElementStorage(tag("root"))
    val state = storage.getState(this, "test", javaClass<Element>())
    assertThat(state).isNull()
  }

  public Test fun `set state overrides old state`() {
    val storage = MyXmlElementStorage(tag("root", tag("component", attr("name", "test"), tag("foo"))))
    val newState = tag("component", attr("name", "test"), tag("bar"))
    val externalizationSession = storage.startExternalization()!!
    externalizationSession.setState(this, "test", newState)
    externalizationSession.createSaveSession()!!.save()
    assertThat(storage.savedElement).isNotNull()
    assertThat(storage.savedElement!!.getChild("component").getChild("bar")).isNotNull()
    assertThat(storage.savedElement!!.getChild("component").getChild("foo")).isNull()
  }

  private class MyXmlElementStorage(private val myElement: Element) : XmlElementStorage("", "root", null, null, null) {
    var savedElement: Element? = null

    override fun loadLocalData() = myElement

    override fun createSaveSession(states: StateMap) = object : XmlElementStorage.XmlElementStorageSaveSession<MyXmlElementStorage>(states, this) {
      override fun saveLocally(element: Element?) {
        savedElement = element?.clone()
      }
    }
  }
}
