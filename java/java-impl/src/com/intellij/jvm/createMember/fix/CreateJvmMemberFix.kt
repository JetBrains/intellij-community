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
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.jvm.JvmClass
import com.intellij.jvm.createMember.CreateJvmMemberFactory
import com.intellij.jvm.createMember.CreateMemberAction
import com.intellij.jvm.createMember.CreateMemberRequest
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.reference.SoftReference

abstract class CreateJvmMemberFix<in T : PsiElement, out R : CreateMemberRequest>(element: T) : BaseIntentionAction() {

  private val myProject: Project = element.project
  private val myPointer: SmartPsiElementPointer<T> = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element)
  private var myData: SoftReference<MyData>? = null

  private inner class MyData(val request: R, val actionMap: Map<JvmClass, List<CreateMemberAction>>)

  protected abstract fun createRequest(element: T): R?
  protected abstract fun collectTargetClasses(element: T): List<JvmClass>

  private fun computeData(): MyData? {
    val element = myPointer.element ?: return null
    val request = createRequest(element) ?: return null
    val targets = collectTargetClasses(element)
    val extensions = CreateJvmMemberFactory.EP_NAME.extensions
    val actions = targets.associate { target ->
      target to extensions.flatMap { ext -> ext.getActions(target, request, element) }
    }.filterValues {
      it.isNotEmpty()
    }
    return if (actions.isNotEmpty()) MyData(request, actions) else null
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    editor ?: return false
    val data = computeData() ?: return false
    text = data.request.title
    myData = SoftReference(data)
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    editor ?: return
    val data = myData?.get() ?: computeData() ?: return
    val step = classStep(data.actionMap)
    if (step != null) {
      JBPopupFactory.getInstance().createListPopup(step).showInBestPositionFor(editor)
    }
  }

  private fun classStep(actionMap: Map<JvmClass, List<CreateMemberAction>>): ListPopupStep<*>? {
    val singleClassActions = actionMap.values.singleOrNull()
    if (singleClassActions == null) {
      return JvmClassMemberActionStep(actionMap) { actions ->
        actionStep(actions)
      }
    }
    else {
      return actionStep(singleClassActions)
    }
  }

  private fun actionStep(actions: List<CreateMemberAction>): ListPopupStep<*>? {
    val singleAction = actions.singleOrNull()
    if (singleAction == null) {
      return JvmMemberActionStep(actions) {
        doAction(it)
      }
    }
    else {
      doAction(singleAction)
      return null
    }
  }

  private fun doAction(action: CreateMemberAction) {
    var member: PsiElement? = null
    CommandProcessor.getInstance().executeCommand(myProject, {
      member = runWriteAction {
        action.renderMember()
      }
    }, null, null)
    (member as? Navigatable)?.navigate(true)
  }

  override fun getFamilyName(): String = QuickFixBundle.message("create.method.from.usage.family")

  override fun startInWriteAction(): Boolean = false
}