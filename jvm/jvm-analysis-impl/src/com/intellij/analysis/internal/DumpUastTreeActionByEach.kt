// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.internal

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.util.IndentedPrintingVisitor

class DumpUastTreeActionByEach : DumpUastTreeAction() {

  override fun buildDump(file: PsiFile): String? = object : IndentedPrintingVisitor({true}){
    override fun render(element: PsiElement): CharSequence? = element.toUElement()?.asLogString()
  }.also { file.accept(it) }.result
}