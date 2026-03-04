// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons

import org.jetbrains.annotations.ApiStatus

/**
 * Deferred Icon takes time to resolve; therefore, it is postponed to be resolved later.
 * Placeholder icon can be set to provide a visual representation while the actual icon is being resolved.
 * Unlike the older API, to force evaluation or get the resolved icon, IconManager should be used.
 *
 * @see IconManager.deferredIcon
 * @see IconManager.forceEvaluationnnnn
 */
@ApiStatus.Experimental
interface DeferredIcon: Icon {
  val id: IconIdentifier
  val placeholder: Icon?
}