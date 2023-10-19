package com.intellij.jvm.analysis.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.uast.UElement

internal fun UElement.toSmartPsiElementPointer() : SmartPsiElementPointer<PsiElement>? = sourcePsi?.let {
  SmartPointerManager.createPointer(it)
}