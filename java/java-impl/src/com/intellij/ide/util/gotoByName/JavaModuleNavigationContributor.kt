/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.ArrayUtil

class JavaModuleNavigationContributor : ChooseByNameContributor {
  private val index = JavaModuleNameIndex.getInstance()

  override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<out String> {
    val result = index.getAllKeys(project)
    return if (result.isEmpty()) ArrayUtil.EMPTY_STRING_ARRAY else result.toTypedArray()
  }

  override fun getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array<out NavigationItem> {
    val scope = if (includeNonProjectItems) ProjectScope.getAllScope(project) else ProjectScope.getProjectScope(project)
    val result = index.get(name, project, scope)
    return if (result.isEmpty()) NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY else result.toTypedArray()
  }
}