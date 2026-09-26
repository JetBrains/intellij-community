// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import java.lang.annotation.Inherited

import org.jetbrains.annotations.ApiStatus

/**
 * Migration marker for editor-internal `DocumentListener` that should handle elf events.
 *
 * `DocumentListener` annotated with this marker is notified of elf events through
 * its regular callbacks:
 * - beforeElfDocumentChange -> beforeDocumentChange
 * - elfDocumentChanged -> documentChanged
 * - bulkElfUpdateStarting -> bulkUpdateStarting
 * - bulkElfUpdateFinished -> bulkUpdateFinished
 *
 * The routing is active only while `ElfFeatureFlag` is enabled; with the flag
 * disabled an annotated listener receives no elf events.
 *
 * This annotation should be removed once all candidates can properly opt in by implementing methods from `ElfDocumentListener`.
 *
 * ##### Rejected solutions
 *
 * - Method in `DocumentListener`: this is the desired end state, but it is not suitable for the migration step while `DocumentListener` extends
 *   `ElfDocumentListener` and is used with `EventDispatcher` proxies, which only support void listener methods. A boolean opt-in method would leak
 *   into public listener API and break proxy dispatch.
 * - Marker interface: it would become part of public ABI for public implementation classes, while this routing contract must stay implementation-only.
 */
@ApiStatus.Internal
@Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ElfCandidate
