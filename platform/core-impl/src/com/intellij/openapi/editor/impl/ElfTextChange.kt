// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command

/**
 * Text change produced on the elf document and waiting to be replayed to real.
 *
 * The record keeps enough state to revert the original elf event, rebase it over
 * real-document edits, and replay it later with the same command and bulk-update
 * context. [patch] is stored so the replay applies exactly the original snapshot
 * update, including the whole-text instance of a whole-text replacement.
 */
internal class ElfTextChange(
  val snapshotBefore: DocumentSnapshot,
  val changeEvent: DocumentEvent,
  val patch: DocumentTextPatch,
  val isInBulkUpdate: Boolean,
  val project: Project?,
  val commandName: @Command String?,
  val commandGroupId: Any?,
  val isTransparent: Boolean,
)
