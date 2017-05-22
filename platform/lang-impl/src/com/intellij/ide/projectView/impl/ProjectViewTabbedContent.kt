/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.projectView.impl

import com.intellij.openapi.util.Pair
import com.intellij.ui.content.TabbedContent
import com.intellij.ui.content.impl.ContentImpl
import javax.swing.JComponent

internal class ProjectViewTabbedContent(
  private val myProjectView: ProjectViewImpl,
  private val myTitle: String,
  subIds: Array<String>,
  subTitleProvider: (String) -> String
) : ContentImpl(myProjectView.component, null, false), TabbedContent {

  private data class Tab(val subId: String, val subTitle: String)

  private val myTabs: List<Tab> = subIds.map {
    Tab(it, subTitleProvider(it))
  }

  private var mySelectedIndex: Int = -1
  private val selectedTab: Tab? get() = myTabs.getOrNull(mySelectedIndex)
  val selectedSubId: String? get() = selectedTab?.subId

  private val myTabsOut by lazy(LazyThreadSafetyMode.NONE) {
    myTabs.map {
      Pair.create<String, JComponent?>(it.subTitle, null)
    }
  }

  init {
    doSelectContent(-1)
  }

  override fun getTabs() = myTabsOut

  override fun getTitlePrefix() = myTitle

  override fun getSelectedIndex() = mySelectedIndex

  override fun selectContent(index: Int) {
    doSelectContent(index)
    myProjectView.viewSelectionChanged()
  }

  fun selectContent(subId: String) {
    selectContent(myTabs.indexOfFirst { it.subId == subId })
  }

  private fun doSelectContent(index: Int) {
    mySelectedIndex = index
    updateDisplayName()
  }

  private fun updateDisplayName() {
    displayName = selectedTab?.let { "$myTitle: ${it.subTitle}" } ?: myTitle
  }

  override fun addContent(content: JComponent, name: String, selectTab: Boolean) {}
  override fun removeContent(content: JComponent) {}
  override fun setTitlePrefix(titlePrefix: String) {}
  override fun split() {}
}
