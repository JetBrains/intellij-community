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

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter

public class JavaModuleNavigationContributor : ChooseByNameContributorEx, PossiblyDumbAware {
  override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode {
      StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.MODULE_NAMES, processor, scope, filter)
    }
  }

  override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode {
      StubIndex.getInstance().processElements(JavaStubIndexKeys.MODULE_NAMES, name,
                                              parameters.project, parameters.searchScope, parameters.idFilter,
                                              PsiJavaModule::class.java, processor)
    }
  }

  override fun isDumbAware(): Boolean = true
}