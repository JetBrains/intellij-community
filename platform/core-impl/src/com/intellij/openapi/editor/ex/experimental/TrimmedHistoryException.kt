// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

/**
 * A merge or a replay throws this when it needs events that [DocumentBranch.trimmedTo] dropped.
 *
 * A branch that holds an event concurrent with the base version of a trimmed graph cannot merge with
 * it. Nor can a branch that lacks events that the trim dropped. The merge changes nothing.
 */
class TrimmedHistoryException internal constructor(message: String) : IllegalArgumentException(message)
