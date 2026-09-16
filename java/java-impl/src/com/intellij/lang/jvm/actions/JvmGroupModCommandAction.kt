// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmClass
import com.intellij.modcommand.ModCommandAction
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * The [ModCommandAction] counterpart of [JvmGroupIntentionAction].
 *
 * It supplies the data which [groupActionsByType] needs to show the same action for several
 * target classes as one intention with a target chooser.
 */
@ApiStatus.Internal
public interface JvmGroupModCommandAction : ModCommandAction {

  /**
   * Given two actions, *Create method 'foo' in 'SomeClass'* and *Create method 'foo' in 'OtherClass'*,
   * we show them as one action, and the user picks the target class later.
   * Such actions must have [equal][Object.equals] action groups.
   */
  public fun getActionGroup(): JvmActionGroup

  /**
   * The name of the group when the actions are [grouped][getActionGroup].
   *
   * This text uses the terms of the target language, e.g. *Create method 'foo'* for Java.
   */
  public fun getGroupDisplayText(): @Nls(capitalization = Nls.Capitalization.Sentence) String =
    getActionGroup().getDisplayText(getRenderData())

  /**
   * Extra data for [JvmActionGroup] when there is no way to choose the
   * [group display text][getGroupDisplayText] from the grouped actions.
   */
  public fun getRenderData(): JvmActionGroup.RenderData? = null

  /**
   * The class the action adds the member to. It names the entries of the target chooser.
   */
  public fun getTarget(): JvmClass?
}
