// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceParameter

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiParameter
import java.awt.event.MouseEvent
import java.util.function.Supplier

fun onClickCallback(psiParameter: PsiParameter): () -> Unit {
  return {
    psiParameter.navigate(true)
  }
}

fun logStatisticsOnShowCallback(project: Project): (MouseEvent?) -> Unit {
  return { event: MouseEvent? -> logStatisticsOnShow(event, project) }
}

fun logStatisticsOnShow(event: MouseEvent?, project: Project) {
  IntroduceParameterUsagesCollector.settingsOnShow.log(project,
    EventFields.InputEvent.with(FusInputEvent(event, null)))
}

fun logStatisticsOnHideCallback(project : Project, delegate : Supplier<Boolean>): () -> Unit {
  return {
     IntroduceParameterUsagesCollector.settingsOnPerform.log(project, 
       IntroduceParameterUsagesCollector.delegate.with(delegate.get()))
  }
}