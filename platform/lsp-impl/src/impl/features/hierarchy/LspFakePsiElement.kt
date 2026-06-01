package com.intellij.platform.lsp.impl.features.hierarchy

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import org.eclipse.lsp4j.Position

internal class LspFakePsiElement(
  val psiFile: PsiFile,
  val lsp4jPosition: Position,
  private var name: String = "",
) : FakePsiElement() {

  override fun getParent(): PsiElement = psiFile
  override fun getContainingFile(): PsiFile = psiFile
  override fun getName(): String = name
  override fun isValid(): Boolean = true
}