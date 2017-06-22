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

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.jvm.JvmClass
import com.intellij.jvm.createMember.CreateMemberAction
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import javax.swing.Icon

internal class JvmClassMemberActionStep(
  private val actionMap: Map<JvmClass, List<CreateMemberAction>>,
  private val callback: (List<CreateMemberAction>) -> PopupStep<*>?
) : BaseListPopupStep<JvmClass>(QuickFixBundle.message("target.class.chooser.title"), actionMap.keys.toList()) {

  override fun isSpeedSearchEnabled(): Boolean = true

  override fun getIconFor(value: JvmClass): Icon? = value.psiElement?.getIcon(0)

  override fun getTextFor(value: JvmClass): String = value.name ?: "<anonymous>"

  override fun hasSubstep(selectedValue: JvmClass): Boolean = actionMap[selectedValue]!!.size > 1

  override fun onChosen(selectedValue: JvmClass, finalChoice: Boolean): PopupStep<*>? {
    if (!finalChoice) return null
    val actions = actionMap[selectedValue] ?: return null
    return callback(actions)
  }
}