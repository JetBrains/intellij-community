// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

/**
 * A merge throws this when the two graphs do not grow from one [EventGraph.createGraph] or
 * [DocumentBranch.createBranch] call. Each such call starts a LINEAGE, and only graphs of one lineage
 * merge, because only there one event id names one event.
 *
 * The merge changes nothing.
 */
class LineageMismatchException internal constructor(message: String) : IllegalArgumentException(message)
