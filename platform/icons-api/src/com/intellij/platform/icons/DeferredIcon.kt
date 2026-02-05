// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons

/**
 * Deferred Icon takes time to resolve; therefore, it is postponed to be resolved after first render. Placeholder icon
 * can be set to provide a visual representation while the actual icon is being resolved.
 *
 * @see IconManager.deferredIcon
 * @see IconManager.forceEvaluation
 */
interface DeferredIcon : Icon {
    val id: IconIdentifier
    val placeholder: Icon?
}
