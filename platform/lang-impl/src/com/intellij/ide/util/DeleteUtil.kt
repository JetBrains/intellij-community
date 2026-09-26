@file:ApiStatus.Internal
@file:JvmName("DeleteUtil")
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.ide.IdeBundle
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.annotations.ApiStatus

/**
 * Generates a warning message for [com.intellij.refactoring.safeDelete.SafeDeleteDialog].
 *
 * Should be invoked on the EDT before showing the dialog.
 * Ensures the message is computed under cancellable modal progress.
 * Failure to precompute the message can cause UI freezes when the dialog computes it directly on the EDT.
 *
 * @param project the project
 * @param isRegularDelete `true` to generate a message for "an unsafe delete action offering a safe delete option", corresponds to [com.intellij.refactoring.safeDelete.SafeDeleteDialog.isDelete]
 * @param elements the elements to delete
 */
fun generateSafeDeleteWarningMessageWithModalProgress(
  project: Project,
  isRegularDelete: Boolean,
  elements: Array<PsiElement>,
): @NlsContexts.DialogMessage String {
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.preparing.delete")) {
    readAction {
      generateSafeDeleteWarningMessage(isRegularDelete, elements)
    }
  }
}

fun generateSafeDeleteWarningMessage(
  isRegularDelete: Boolean,
  elements: Array<PsiElement>,
): @NlsContexts.DialogMessage String {
  val key = if (isRegularDelete) "prompt.delete.elements" else "search.for.usages.and.delete.elements"
  if (elements.size == 1) {
    val name = ElementDescriptionUtil.getElementDescription(elements[0], DeleteNameDescriptionLocation.INSTANCE)
    val type = ElementDescriptionUtil.getElementDescription(elements[0], DeleteTypeDescriptionLocation.SINGULAR)
    return IdeBundle.message(key, type + (if (name.isBlank()) "" else " \"$name\""))
  }

  val countMap = hashMapOf<String, Int>().withDefault { 0 }
  val pluralToSingular = HashMap<String, String>()
  var directoryCount = 0
  var containerType: String? = null
  for (elementToDelete in elements) {
    val type = ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.PLURAL)
    pluralToSingular[type] = ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.SINGULAR)
    countMap[type] = countMap.getValue(type) + 1
    if (elementToDelete is PsiDirectoryContainer) {
      containerType = type
      directoryCount += elementToDelete.getDirectories().size
    }
  }

  val buffer = StringBuilder()
  for ((type, count) in countMap) {
    if (!buffer.isEmpty()) {
      buffer.append(" ").append(IdeBundle.message("prompt.delete.and")).append(" ")
    }
    buffer.append(count).append(" ")
    if (count == 1) {
      buffer.append(pluralToSingular.getValue(type))
    }
    else {
      buffer.append(type)
    }
    if (type == containerType) {
      buffer.append(" ").append(IdeBundle.message("prompt.delete.directory.paren", directoryCount))
    }
  }

  return IdeBundle.message(key, buffer.toString())
}

fun generateDeleteWarningMessageWithModalProgress(
  project: Project,
  elements: Array<PsiElement>,
  filteredElements: Array<PsiElement>,
  safeDeleteApplicable: Boolean,
): @NlsContexts.DialogMessage String {
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.preparing.delete")) {
    readAction {
      generateDeleteWarningMessage(elements, filteredElements, safeDeleteApplicable)
    }
  }
}

private fun generateDeleteWarningMessage(
  elements: Array<PsiElement>,
  filteredElements: Array<PsiElement>,
  safeDeleteApplicable: Boolean,
): @NlsContexts.DialogMessage String {
  var warningMessage = generateSafeDeleteWarningMessage(isRegularDelete = true, filteredElements)

  var anyDirectories = false
  var directoryName: String? = null
  for (psiElement in elements) {
    if (psiElement is PsiDirectory && !PsiUtilBase.isSymLink(psiElement)) {
      anyDirectories = true
      directoryName = psiElement.getName()
      break
    }
  }
  if (anyDirectories) {
    if (filteredElements.size == 1) {
      warningMessage += IdeBundle.message("warning.delete.all.files.and.subdirectories", directoryName)
    }
    else {
      warningMessage += IdeBundle.message("warning.delete.all.files.and.subdirectories.in.the.selected.directory")
    }
  }

  if (safeDeleteApplicable) {
    warningMessage +=
      LangBundle.message("dialog.message.warning.safe.delete.not.available.while.updates.indices.no.usages.will.be.checked",
                         ApplicationNamesInfo.getInstance().fullProductName)
  }
  return warningMessage
}
