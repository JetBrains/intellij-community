// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.xml

import com.intellij.configurationStore.DataWriter
import com.intellij.configurationStore.StateMap
import com.intellij.configurationStore.XmlElementStorage
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.settings.SettingsController
import com.intellij.util.LineSeparator
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.Test

class XmlElementStorageTest {
  @Test
  fun testGetStateSucceeded() {
    val storage = MyXmlElementStorage(Element("root").addContent(Element("component").setAttribute("name", "test").addContent(Element("foo"))))
    val state = storage.getState(
      component = this,
      componentName = "test",
      pluginId = PluginManagerCore.CORE_ID,
      stateClass = Element::class.java,
      mergeInto = null,
      reload = false,
    )
    assertThat(state).isNotNull
    assertThat(state!!.name).isEqualTo("component")
    assertThat(state.getChild("foo")).isNotNull
  }

  @Test
  fun `get state not succeeded`() {
    val storage = MyXmlElementStorage(Element("root"))
    val state = storage.getState(
      component = this,
      componentName = "test",
      PluginManagerCore.CORE_ID,
      Element::class.java,
      mergeInto = null,
      reload = false,
    )
    assertThat(state).isNull()
  }

  @Test
  fun `set state overrides old state`() = runBlocking {
    val storage = MyXmlElementStorage(Element("root").addContent(Element("component").setAttribute("name", "test").addContent(Element("foo"))))
    val newState = Element("component").setAttribute("name", "test").addContent(Element("bar"))
    val externalizationSession = storage.createSaveSessionProducer()!!
    externalizationSession.setState(component = null, componentName = "test", pluginId = PluginManagerCore.CORE_ID, state = newState)
    externalizationSession.createSaveSession()!!.save(events = null)
    assertThat(storage.savedElement).isNotNull
    assertThat(storage.savedElement!!.getChild("component").getChild("bar")).isNotNull
    assertThat(storage.savedElement!!.getChild("component").getChild("foo")).isNull()
  }

  private class MyXmlElementStorage(private val element: Element)
    : XmlElementStorage(fileSpec = "", rootElementName = "root", storageRoamingType = RoamingType.DEFAULT) {
    override val controller: SettingsController?
      get() = null

    var savedElement: Element? = null

    override fun loadLocalData() = element

    override fun createSaveSession(states: StateMap): XmlElementStorageSaveSessionProducer<MyXmlElementStorage> {
      return object : XmlElementStorageSaveSessionProducer<MyXmlElementStorage>(states, this) {
        override fun remove(events: MutableList<VFileEvent>?) {
          savedElement = null
        }

        override fun saveLocally(dataWriter: DataWriter, events: MutableList<VFileEvent>?) {
          savedElement = JDOMUtil.load(dataWriter.toBufferExposingByteArray(LineSeparator.LF).toByteArray().inputStream())
        }
      }
    }
  }
}
