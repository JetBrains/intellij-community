// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener


internal class PsiEventsTestListener : PsiTreeChangeListener {
  private val myBuffer: StringBuffer = StringBuffer()
  private val logger = thisLogger()

  fun reset() {
    logger.debug("reset")
    myBuffer.setLength(0)
  }

  val eventsString: String
    get() = myBuffer.toString()

  private fun logEvent(message: String){
    myBuffer.append(message)
    logger.debug(Throwable(message))
  }

  override fun beforeChildAddition(event: PsiTreeChangeEvent) {
    logEvent("beforeChildAddition\n")
  }

  override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
    logEvent("beforeChildRemoval\n")
  }

  override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
    logEvent("beforeChildReplacement\n")
  }

  override fun beforeChildMovement(event: PsiTreeChangeEvent) {
    logEvent("beforeChildMovement\n")
  }

  override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
    logEvent("beforeChildrenChange\n")
  }

  override fun beforePropertyChange(event: PsiTreeChangeEvent) {
    logEvent("beforePropertyChange ${event.propertyName}\n")
  }

  override fun childAdded(event: PsiTreeChangeEvent) {
    logEvent("childAdded\n")
  }

  override fun childRemoved(event: PsiTreeChangeEvent) {
    logEvent("childRemoved\n")
  }

  override fun childReplaced(event: PsiTreeChangeEvent) {
    logEvent("childReplaced\n")
  }

  override fun childrenChanged(event: PsiTreeChangeEvent) {
    logEvent("childrenChanged\n")
  }

  override fun childMoved(event: PsiTreeChangeEvent) {
    logEvent("childMoved\n")
  }

  override fun propertyChanged(event: PsiTreeChangeEvent) {
    logEvent("propertyChanged ${event.propertyName}\n")
  }
}
