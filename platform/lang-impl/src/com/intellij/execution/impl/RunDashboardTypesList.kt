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
package com.intellij.execution.impl

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.speedSearch.SpeedSearchUtil
import javax.swing.JComponent
import javax.swing.JList

/**
 * @author Konstantin Aleev
 */
internal class RunDashboardTypesList(private val myProject: Project) {
  private val listModel = CollectionListModel<ConfigurationType>()
  private val list = JBList<ConfigurationType>(listModel)

  val component: JComponent = JBScrollPane(list)
  val selectedTypes: Collection<ConfigurationType>
    get() = list.selectedValuesList
  val allTypes: Collection<ConfigurationType>
    get() = listModel.items

  init {
    val search = ListSpeedSearch(list, { it.displayName })
    search.comparator = SpeedSearchComparator(false)
    list.visibleRowCount = 20

    list.cellRenderer = object : ColoredListCellRenderer<ConfigurationType>() {
      override fun customizeCellRenderer(list: JList<out ConfigurationType>,
                                         value: ConfigurationType?,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        if (value != null) {
          append(value.displayName)
          icon = value.icon
        }

        SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, selected)
      }
    }
  }

  fun updateModel(types: Set<String>, include: Boolean) {
    listModel.removeAll()
    listModel.add(
      RunManagerImpl.getInstanceImpl(myProject).configurationFactoriesWithoutUnknown.filterNot { types.contains(it.id) xor include })
  }
}