// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

/**
 * A merge throws this when the two histories give one event id to two different operations.
 *
 * One ([agent], [seq]) pair names one operation forever. A clash means that two branches edited
 * concurrently under one [Agent], which the [DocumentBranch] contract forbids. The merge changes
 * nothing, so a caller can fall back to a merge of the two texts.
 *
 * A merge does not find every clash, but a clash that it reports is always real.
 */
class EventIdClashException internal constructor(
  private val agent: Agent,
  private val seq: Long,
  message: String,
) : IllegalArgumentException(message) {

  /**
   * The agent of the id that names two operations.
   */
  fun agent(): Agent = agent

  /**
   * The seq of the id that names two operations.
   */
  fun seq(): Long = seq
}
