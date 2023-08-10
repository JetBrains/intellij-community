package com.intellij.codeInsight.codeVision

sealed class CodeVisionRelativeOrdering{
  class CodeVisionRelativeOrderingAfter(val id: String) : CodeVisionRelativeOrdering()
  class CodeVisionRelativeOrderingBefore(val id: String) : CodeVisionRelativeOrdering()
  object CodeVisionRelativeOrderingFirst : CodeVisionRelativeOrdering()
  object CodeVisionRelativeOrderingLast : CodeVisionRelativeOrdering()
}
