// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.model.Pointer

/**
 * Usages include both declarations and references.
 *
 * @see PsiRenameUsage
 * @see ModifiableRenameUsage
 */
interface RenameUsage {

  fun createPointer(): Pointer<out RenameUsage>

  /**
   * Whether this usage is a declaration (`true`).
   * Other (`false`) usages may include references, text usages, model usages, etc.
   */
  val declaration: Boolean

  /**
   * Example: setting Kotlin function name to a string which is not a valid Java identifier will break references in Java.
   * Such situations require user attention, so conflicts will trigger additional dialogs or another UI.
   *
   * The same usage might produce conflict and might stop producing conflict depending on chosen [newName].
   * This method might be called several times to recompute the conflicts if the user updates the name.
   *
   * The conflict might appear elsewhere, it's not necessary that conflict appears inside the usage PsiElement.
   *
   * @param newName new name of the [RenameTarget] which is targeted by this usage
   * @return list of conflicts produced by this usage, or empty list if there are no conflicts
   */
  fun conflicts(newName: String): List<RenameConflict> = emptyList()
}
