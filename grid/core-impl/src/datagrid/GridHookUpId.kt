package com.intellij.database.datagrid

import org.jetbrains.annotations.ApiStatus

/**
 * Names a hookup that [GridHookUpRegistry] registered, so an editor or a diff can acquire it later.
 * It stays valid while the hookup is registered, also while the registry keeps it for a reopen.
 * Its string form is the registry's random id plus a sequence number.
 * A later lookup can then tell a closed hookup of this registry from an id that another registry gave.
 */
@ApiStatus.Internal
@ConsistentCopyVisibility
data class GridHookUpId internal constructor(private val value: String) {
  override fun toString(): String = value
}
