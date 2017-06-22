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
package com.intellij.jvm.createMember.fix

import com.intellij.jvm.createMember.CreateMemberAction
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import javax.swing.Icon

internal class JvmMemberActionStep(
  values: List<CreateMemberAction>,
  private val callback: (CreateMemberAction) -> Unit
) : BaseListPopupStep<CreateMemberAction>(null, values) {

  override fun isAutoSelectionEnabled(): Boolean = true

  override fun getIconFor(value: CreateMemberAction): Icon? = value.icon

  override fun getTextFor(value: CreateMemberAction): String = value.title

  override fun onChosen(selectedValue: CreateMemberAction, finalChoice: Boolean): PopupStep<*>? {
    callback(selectedValue)
    return null
  }
}