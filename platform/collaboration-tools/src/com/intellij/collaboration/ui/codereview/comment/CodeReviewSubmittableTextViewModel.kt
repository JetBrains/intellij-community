// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

interface CodeReviewSubmittableTextViewModel {
  val project: Project

  /**
   * Input text state
   */
  val text: MutableStateFlow<String>

  /**
   * State of submission progress
   * null means that submission wasn't started yet
   */
  val state: StateFlow<ComputedResult<Unit>?>

  val focusRequests: Flow<Unit>

  fun requestFocus()
}

abstract class CodeReviewSubmittableTextViewModelBase(
  override val project: Project,
  parentCs: CoroutineScope,
  initialText: String
) : CodeReviewSubmittableTextViewModel {
  protected val cs = parentCs.childScope()
  private val taskLauncher = SingleCoroutineLauncher(cs)

  final override val text: MutableStateFlow<String> = MutableStateFlow(initialText)

  private val _state = MutableStateFlow<ComputedResult<Unit>?>(null)
  final override val state: StateFlow<ComputedResult<Unit>?> = _state.asStateFlow()

  private val _focusRequestsChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
  final override val focusRequests: Flow<Unit> get() = _focusRequestsChannel.receiveAsFlow()

  final override fun requestFocus() {
    cs.launch {
      _focusRequestsChannel.send(Unit)
    }
  }

  protected fun submit(submitter: suspend (String) -> Unit) {
    taskLauncher.launch {
      val newText = text.first()
      _state.value = ComputedResult.loading()
      try {
        submitter(newText)

        _state.value = ComputedResult.success(Unit)
      }
      catch (ce: CancellationException) {
        _state.value = null
        throw ce
      }
      catch (e: Exception) {
        _state.value = ComputedResult.failure(e)
      }
    }
  }
}