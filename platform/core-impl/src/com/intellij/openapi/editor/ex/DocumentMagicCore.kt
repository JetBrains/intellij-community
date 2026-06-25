// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus

/**
 * Context-dependent core used by a magic document.
 *
 * Document roles:
 * - real document is the old lock-guarded document model. It remains authoritative
 *   and may be changed only on EDT under the global RW lock.
 * - elf document is the UI view used by typing code inside an elf scope.
 *   Changes made there are synchronized back to the real document later.
 * - magic document is context-dependent: inside an elf scope it behaves like the
 *   elf document; outside that scope it behaves like the real document.
 *
 * Contract:
 * - Before the first elf scope, elf and real documents are identical.
 * - The elf document cannot observe changes made to the real document within an
 *   elf scope.
 * - elf text is normally changed only within an elf scope. Sync from elf to real
 *   may update elf state while applying the synchronized change.
 */
@ApiStatus.Internal
interface DocumentMagicCore : DocumentCore {
  /**
   * Returns the elf document core.
   */
  fun elfCore(): DocumentCore

  /**
   * Returns the authoritative lock-guarded real document core.
   */
  fun realCore(): DocumentCore
}
