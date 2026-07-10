// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import org.jetbrains.annotations.ApiStatus

/**
 * A marker interface for [Configurable]s that require an explicit user action outside the IDE
 * (such as browser-based authorization or a native file picker) before their state can be reset.
 *
 * When a settings window regains focus, the Settings editor normally resets any configurable that
 * was unmodified when focus was lost but now reports `isModified() == true` (e.g. due to a
 * background/external change). Implementing this interface opts the configurable out of that
 * automatic reset, ensuring that ongoing external interactions are not interrupted.
 */
@ApiStatus.OverrideOnly
interface NoAutomaticReset
