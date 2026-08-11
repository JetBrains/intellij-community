// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentSnapshot

/**
 * Real-document text change recorded while elf and real views are separated.
 *
 * During synchronization, [snapshotAfter] is applied to the elf view before
 * pending elf changes are rebased over the real-document edit.
 */
internal class RealTextChange(
  val changeEvent: DocumentEvent,
  val snapshotAfter: DocumentSnapshot,
  val isInBulkUpdate: Boolean,
)
