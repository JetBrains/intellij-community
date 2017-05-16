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

  private val myTabsOut by lazy(LazyThreadSafetyMode.NONE) {
    myTabs.map {
      Pair.create<String, JComponent?>(it.subTitle, null)
    }
  }

  private var mySelectedTab: Tab = myTabs.first()

  init {
    updateDisplayName()
  }

  override fun getTabs() = myTabsOut

  override fun getTitlePrefix() = myTitle

  override fun selectContent(index: Int) {
    mySelectedTab = myTabs[index]
    updateDisplayName()
    myProjectView.viewSelectionChanged()
  }

  fun selectContent(subId: String) {
    selectContent(myTabs.indexOfFirst { it.subId == subId })
  }

  val selectedSubId: String get() = mySelectedTab.subId

  private fun updateDisplayName() {
    displayName = mySelectedTab.let { "$myTitle: ${it.subTitle}" }
  }

  override fun addContent(content: JComponent, name: String, selectTab: Boolean) {}
  override fun removeContent(content: JComponent) {}
  override fun setTitlePrefix(titlePrefix: String) {}
  override fun split() {}
}
