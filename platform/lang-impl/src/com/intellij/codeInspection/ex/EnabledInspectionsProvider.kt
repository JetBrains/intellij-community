// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface EnabledInspectionsProvider {
  fun getEnabledTools(psiFile: PsiFile?, includeDoNotShow: Boolean): ToolWrappers

  class ToolWrappers(
    val allLocalWrappers: List<LocalInspectionToolWrapper>,
    val allGlobalSimpleWrappers: List<GlobalInspectionToolWrapper>
  ) {
    val allWrappers: Sequence<InspectionToolWrapper<*, *>> = sequence {
      yieldAll(allLocalWrappers)
      yieldAll(allGlobalSimpleWrappers)
    }

    val localRegularWrappers: List<LocalInspectionToolWrapper>
    val globalSimpleRegularWrappers: List<GlobalInspectionToolWrapper>
    val externalAnnotatorWrappers: List<InspectionToolWrapper<*, *>>

    init {
      val (localExternalWrappers, localRegularWrappers) = allLocalWrappers.partition {
        it.tool is ExternalAnnotatorBatchInspection
      }
      this.localRegularWrappers = localRegularWrappers

      val (globalSimpleExternalWrappers, globalSimpleRegularWrappers) = allGlobalSimpleWrappers.partition {
        it.tool is ExternalAnnotatorBatchInspection
      }
      this.globalSimpleRegularWrappers = globalSimpleRegularWrappers
      externalAnnotatorWrappers = localExternalWrappers + globalSimpleExternalWrappers
    }
  }
}