// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiAnchor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.tree.IElementType
import kotlin.concurrent.Volatile

internal class AnchorElementInfo : SelfElementInfo {
  @Volatile
  private var myStubElementTypeAndId: Long // stubId in the lower 32 bits; stubElementTypeIndex in the high 32 bits packed together for atomicity

  constructor(
    anchor: PsiElement,
    containingFile: PsiFile,
    identikit: Identikit.ByAnchor,
    manager: SmartPointerManagerEx,
  ) : super(ProperTextRange.create(anchor.getTextRange()), identikit, containingFile, false, manager) {
    myStubElementTypeAndId = pack(-1, null)
  }

  // will restore by stub index until file tree get loaded
  constructor(
    anchor: PsiElement,
    containingFile: PsiFileWithStubSupport,
    stubId: Int,
    stubElementType: IElementType,
    manager: SmartPointerManagerEx,
  ) : super(
    null,
    Identikit.fromTypes(anchor.javaClass, stubElementType, LanguageUtil.getRootLanguage(containingFile)),
    containingFile,
    false,
    manager
  ) {
    myStubElementTypeAndId = pack(stubId, stubElementType)
    assert(anchor !is PsiFile) { "FileElementInfo must be used for file: $anchor" }
  }

  private fun pack(stubId: Int, stubElementType: IElementType?): Long {
    val index = stubElementType?.index ?: 0
    assert(index >= 0) { "Unregistered token types not allowed here: $stubElementType" }
    return (stubId.toLong()) or (index.toLong() shl 32)
  }

  private val stubId: Int
    get() = myStubElementTypeAndId.toInt()

  override fun restoreElement(manager: SmartPointerManagerEx): PsiElement? {
    val typeAndId = myStubElementTypeAndId
    val stubId = typeAndId.toInt()
    if (stubId == -1) {
      return super.restoreElement(manager)
    }

    val file = restoreFile(manager) as? PsiFileWithStubSupport ?: return null
    val index = (typeAndId shr 32).toShort()
    val stubElementType = IElementType.find(index)
    return PsiAnchor.restoreFromStubIndex(file, stubId, stubElementType, false)
  }

  override fun pointsToTheSameElementAs(other: SmartPointerElementInfo, manager: SmartPointerManagerEx): Boolean {
    if (other !is AnchorElementInfo) {
      return super.pointsToTheSameElementAs(other, manager)
    }

    if (virtualFile != other.virtualFile) return false

    val packed1 = myStubElementTypeAndId
    val packed2 = other.myStubElementTypeAndId

    if (packed1 != -1L && packed2 != -1L) {
      return packed1 == packed2
    }
    if (packed1 == -1L && packed2 == -1L) {
      return super.pointsToTheSameElementAs(other, manager)
    }

    return ReadAction.computeBlocking(ThrowableComputable {
      Comparing.equal(
        restoreElement(manager),
        other.restoreElement(manager)
      )
    })
  }

  override fun fastenBelt(manager: SmartPointerManagerEx) {
    if (this.stubId != -1) {
      switchToTree(manager)
    }
    super.fastenBelt(manager)
  }

  private fun switchToTree(manager: SmartPointerManagerEx) {
    val element = restoreElement(manager) ?: return
    val tracker = manager.getTracker(virtualFile) ?: return
    tracker.switchStubToAst(this, element)
  }

  fun switchToTreeRange(element: PsiElement) {
    switchToAnchor(element)
    myStubElementTypeAndId = pack(-1, null)
  }

  override fun getRange(manager: SmartPointerManagerEx): Segment? {
    if (this.stubId != -1) {
      switchToTree(manager)
    }
    return super.getRange(manager)
  }

  override fun getPsiRange(manager: SmartPointerManagerEx): TextRange? {
    if (this.stubId != -1) {
      switchToTree(manager)
    }
    return super.getPsiRange(manager)
  }

  override fun toString(): String = super.toString() + ",stubId=" + this.stubId
}
