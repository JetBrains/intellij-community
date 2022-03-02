// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.refactoring.IntroduceVariableUtil
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.ui.layout.*
import java.awt.event.MouseEvent
import java.util.function.Supplier

class IntroduceVariablePopupComponent(private val editor: Editor,
                                      private val project: Project,
                                      private val cantChangeFinal: Boolean,
                                      private val canBeVarType: Boolean,
                                      private val commandName: @NlsContexts.Command String?,
                                      private val variableSupplier: Supplier<PsiVariable?>) {

  fun createPopupPanel(): DialogPanel {
    return panel {
      val variable = variableSupplier.get()
      if (!cantChangeFinal && variable != null) {
        row {
          val finalListener = FinalListener(editor)
          checkBox(JavaRefactoringBundle.message("declare.final"),
                   JavaVariableInplaceIntroducer.createFinals(variable.containingFile)) { _, cb ->
            WriteCommandAction.writeCommandAction(project).withName(commandName).withGroupId(commandName).run<RuntimeException> {
              PsiDocumentManager.getInstance(project).commitDocument(editor.document)
              val newVar = variableSupplier.get()
              if (newVar != null) {
                finalListener.perform(cb.isSelected, newVar)
              }
            }
          }
        }
      }
      if (canBeVarType) {
        row {
          checkBox(JavaRefactoringBundle.message("declare.var.type"),
                   IntroduceVariableBase.createVarType()) { _, cb ->
            WriteCommandAction.writeCommandAction(project).withName(commandName).withGroupId(commandName).run<RuntimeException> {
              val newVar = variableSupplier.get()
              if (newVar != null) {
                var typeElement = newVar.typeElement
                LOG.assertTrue(typeElement != null)
                if (cb.isSelected) {
                  IntroduceVariableUtil.expandDiamondsAndReplaceExplicitTypeWithVar(typeElement,
                                                                                    newVar)
                }
                else {
                  typeElement = PsiTypesUtil.replaceWithExplicitType(typeElement)
                  if (typeElement != null) { //simplify as it was before `var`
                    IntroduceVariableBase.simplifyVariableInitializer(newVar.initializer, typeElement.type)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  fun logStatisticsOnShowCallback(): (MouseEvent?) -> Unit {
    return { event: MouseEvent? -> logStatisticsOnShow(event) }
  }

  fun logStatisticsOnShow(event: MouseEvent?) {
    val variable = variableSupplier.get() ?: return
    IntroduceVariableUsagesCollector.settingsOnShow.log(project,
                                                        EventFields.InputEvent.with(FusInputEvent(event, null)),
                                                        IntroduceVariableUsagesCollector.varType.with(IntroduceVariableBase.createVarType()),
                                                        IntroduceVariableUsagesCollector.finalState.with(JavaVariableInplaceIntroducer.createFinals(variable.containingFile)))
  }

  fun logStatisticsOnHideCallback(): () -> Unit {
    return {
      logStatisticsOnHide(variableSupplier.get())
    }
  }

  private fun logStatisticsOnHide(psiVariable: PsiVariable?) {
    val file = psiVariable?.containingFile ?: return
    val oldFinal = JavaVariableInplaceIntroducer.createFinals(file)
    val oldVarType = IntroduceVariableBase.createVarType()
    if (!cantChangeFinal) {
      JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = psiVariable.hasModifierProperty(PsiModifier.FINAL)
    }
    if (canBeVarType) {
      val typeElement = psiVariable.typeElement
      if (typeElement != null) {
        JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE = typeElement.isInferredType
      }
    }
    val newFinals = JavaVariableInplaceIntroducer.createFinals(file)
    val newVarType = IntroduceVariableBase.createVarType()
    IntroduceVariableUsagesCollector.settingsChanged.log(project,
                                                         IntroduceVariableUsagesCollector.changed.with(
                                                           newFinals != oldFinal || newVarType != oldVarType))
    IntroduceVariableUsagesCollector.settingsOnPerform.log(project,
                                                           IntroduceVariableUsagesCollector.varType.with(newVarType),
                                                           IntroduceVariableUsagesCollector.finalState.with(newFinals))
  }

  companion object {
    private val LOG = Logger.getInstance(IntroduceVariablePopupComponent::class.java)
  }
}