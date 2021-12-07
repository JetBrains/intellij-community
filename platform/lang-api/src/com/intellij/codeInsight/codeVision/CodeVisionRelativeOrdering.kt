package com.intellij.codeInsight.codeVision

abstract class CodeVisionRelativeOrdering

class CodeVisionRelativeOrderingAfter(val id: String) : CodeVisionRelativeOrdering()
class CodeVisionRelativeOrderingBefore(val id: String) : CodeVisionRelativeOrdering()
class CodeVisionRelativeOrderingFirst : CodeVisionRelativeOrdering()
class CodeVisionRelativeOrderingLast : CodeVisionRelativeOrdering()
