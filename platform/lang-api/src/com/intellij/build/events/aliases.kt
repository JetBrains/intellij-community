// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events

/**
 * Defines ids for:
 * - [com.intellij.build.BuildDescriptor.id]
 */
typealias BuildId = Any

/**
 * Defines ids for:
 * - [StartEvent.id]
 * - [FinishEvent.id]
 * - [ProgressBuildEvent.id]
 * - [OutputBuildEvent.parentId]
 * - [OutputReferenceEvent.parentId]
 */
typealias StartId = Any

/**
 * Defines ids for:
 * - [StartBuildEvent.id]
 * - [FinishBuildEvent.id]
 */
typealias StartBuildId = StartId

/**
 * Defines ids for:
 * - [OutputBuildEvent.id]
 * - [OutputReferenceEvent.outputIds]
 */
typealias OutputId = Any