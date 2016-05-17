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
package com.intellij.testIntegration

import com.intellij.execution.testframework.TestIconMapper
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import javax.swing.Icon

class SelectTestStep(tests: List<RecentTestsPopupEntry>, 
                     private val runner: RecentTestRunner) 
     : BaseListPopupStep<RecentTestsPopupEntry>("Debug Recent Tests", tests) 
{

  override fun getIconFor(value: RecentTestsPopupEntry): Icon? {
    return TestIconMapper.getIcon(value.magnitude)
  }

  override fun getTextFor(value: RecentTestsPopupEntry) = value.presentation
  
  override fun isSpeedSearchEnabled() = true

  override fun onChosen(entry: RecentTestsPopupEntry, finalChoice: Boolean): PopupStep<RecentTestsPopupEntry>? {
    entry.run(runner)
    return null
  }

}
