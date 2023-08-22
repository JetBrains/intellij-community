// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

open class ElementContext(
  open val element: PsiElement,
  val inspectionSettings: BlockingCallInspectionSettings
)

class MethodContext(override val element: PsiMethod,
                    private val myCurrentChecker: BlockingMethodChecker,
                    val checkers: Collection<BlockingMethodChecker>,
                    inspectionSettings: BlockingCallInspectionSettings): ElementContext(element, inspectionSettings) {

  val isMethodNonBlocking: Boolean
    get() {
      for (checker in checkers) {
        if (myCurrentChecker !== checker) {
          if (checker.isMethodNonBlocking(MethodContext(element, checker, checkers, inspectionSettings))) {
            return true
          }
        }
      }
      return false
    }
}