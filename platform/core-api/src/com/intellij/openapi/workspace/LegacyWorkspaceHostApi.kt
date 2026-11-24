// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.workspace

import org.jetbrains.annotations.ApiStatus

/**
 * Marks [ProjectWorkspace.hostProject], the adapter that gives the host project of a workspace to a legacy consumer.
 *
 * A legacy consumer is workspace code that calls an API that takes a `Project`, such as a tree node of a tool window.
 * New code must not opt in. It uses the project of the element that it handles, or a workspace service.
 */
@ApiStatus.Internal
@RequiresOptIn(
  message = "The host project of a workspace is an adapter for a legacy consumer. New code must not use it.",
  level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class LegacyWorkspaceHostApi
