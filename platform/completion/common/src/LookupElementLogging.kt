// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common

import com.intellij.codeInsight.lookup.LookupElement

/**
 * Identifies a [LookupElement] in a log message **without** disclosing its text.
 *
 * `LookupElement.toString()` is the element's `lookupString`, which is user-derived — in a terminal or a scratch buffer
 * it can be a secret the user just typed (IJPL-252788). Levels that are enabled by default (`INFO`, `WARN`, `ERROR`,
 * including the `details` of [com.intellij.openapi.diagnostic.errorWithWarnDetails], which are logged at `WARN`) must
 * use this instead. The full element still belongs in a paired `debug { }` line, which is off unless someone is
 * investigating.
 */
fun LookupElement.logId(): String = "${javaClass.name}@${Integer.toHexString(System.identityHashCode(this))}"
