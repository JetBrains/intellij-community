// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.problems

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Change
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Event
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Key
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Problem
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.State
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<LspProjectErrors>()

/**
 * Feeds the Project Errors tab of the Problems View with the errors LSP servers push for files they do not have open
 * ([LspProjectErrorsModel]). One applier coroutine in the service scope owns all state: it gets the raw events of
 * [LspClientManagerListener] (a client is watched on its first event, so the subscription replay seeds the current clients),
 * keeps the push store and the open files of each client, builds its snapshots (at most one per [THROTTLE]), runs the model
 * and applies the changes to the [ProblemsCollector]. The file of a problem is looked up there, off the listener threads.
 * When the scope ends (project close, module unload) the applier removes every problem it added.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class LspProjectErrors(override val project: Project, scope: CoroutineScope) : ProblemsProvider {
  private val signals = Channel<Signal>(Channel.UNLIMITED)

  /** A raw event; the listener threads only enqueue, the applier coroutine does the work. */
  private sealed interface Signal {
    val client: LspClient

    class Seen(override val client: LspClient) : Signal

    class Removed(override val client: LspClient) : Signal

    class Push(override val client: LspClient, val params: PublishDiagnosticsParams) : Signal

    class Opened(override val client: LspClient, val file: VirtualFile) : Signal

    class Closed(override val client: LspClient, val file: VirtualFile) : Signal

    class Recompute(override val client: LspClient) : Signal
  }

  init {
    LspClientManager.getInstance(project).addListener(
      listener = object : LspClientManagerListener {
        override fun clientAdded(lspClient: LspClient) {
          signals.trySend(Signal.Seen(lspClient))
        }

        override fun clientRemoved(lspClient: LspClient) {
          signals.trySend(Signal.Removed(lspClient))
        }

        override fun serverStateChanged(lspClient: LspClient) {
          if (!isLive(lspClient)) signals.trySend(Signal.Removed(lspClient))
        }

        override fun fileOpened(lspClient: LspClient, file: VirtualFile) {
          signals.trySend(Signal.Opened(lspClient, file))
        }

        override fun fileClosed(lspClient: LspClient, file: VirtualFile) {
          signals.trySend(Signal.Closed(lspClient, file))
        }

        override fun diagnosticsPublished(lspClient: LspClient, params: PublishDiagnosticsParams) {
          signals.trySend(Signal.Push(lspClient, params))
        }
      },
      parentDisposable = this,
      sendEventsForExistingClients = true,
    )
    scope.launch { run() }
  }

  /** The store of one watched client: its pushes by URI, the files open on its server, and the resolved files by URI. */
  private class ClientState {
    val pushes = HashMap<String, List<Diagnostic>>()
    val open = HashSet<VirtualFile>()
    val files = HashMap<String, VirtualFile>()
    val dirty = Channel<Unit>(Channel.CONFLATED)
    var job: Job? = null
  }

  private suspend fun run() {
    val collector = project.serviceAsync<ProblemsCollector>()
    coroutineScope {
      val clients = HashMap<LspClient, ClientState>()
      val applier = LspProjectErrorsApplier(
        isLive = ::isLive,
        problem = { problem -> fileProblem(clients[problem.client], problem) },
        sink = object : LspProjectErrorsApplier.Sink<LspFileProblem> {
          override fun appeared(problem: LspFileProblem) = collector.problemAppeared(problem)

          override fun updated(problem: LspFileProblem) = collector.problemUpdated(problem)

          override fun disappeared(problem: LspFileProblem) = collector.problemDisappeared(problem)
        },
        watcher = object : LspProjectErrorsApplier.Watcher<LspClient> {
          override fun watch(client: LspClient) {
            val state = ClientState()
            clients[client] = state
            state.job = launch {
              for (unit in state.dirty) {
                signals.send(Signal.Recompute(client))
                delay(THROTTLE)
              }
            }
          }

          override fun unwatch(client: LspClient) {
            val state = clients.remove(client) ?: return
            state.dirty.close()
            state.job?.cancel()
          }
        },
      )

      fun store(signal: Signal, mutate: (ClientState) -> Unit) {
        applier.apply(LspProjectErrorsApplier.Input.Added(signal.client)) // A1: the first event of a live client starts watching it
        val state = clients[signal.client] ?: return
        mutate(state)
        state.dirty.trySend(Unit)
      }

      try {
        for (signal in signals) {
          when (signal) {
            is Signal.Seen -> applier.apply(LspProjectErrorsApplier.Input.Added(signal.client))
            is Signal.Removed -> applier.apply(LspProjectErrorsApplier.Input.Removed(signal.client))
            is Signal.Push -> store(signal) { state ->
              val pushed = signal.params.diagnostics.orEmpty()
              if (pushed.isEmpty()) state.pushes.remove(signal.params.uri) else state.pushes[signal.params.uri] = pushed
            }
            is Signal.Opened -> store(signal) { it.open.add(signal.file) }
            is Signal.Closed -> store(signal) { it.open.remove(signal.file) }
            is Signal.Recompute -> clients[signal.client]?.let { state ->
              applier.apply(LspProjectErrorsApplier.Input.Changed(snapshot(signal.client, state)))
            }
          }
        }
      }
      finally {
        // the scope ended: the problems go now, the collector outlives this module
        applier.dispose()
      }
    }
  }

  /** The snapshot of one client; the applier coroutine consumes it synchronously, before the next mutation of [state]. */
  private fun snapshot(client: LspClient, state: ClientState): Event.Snapshot<LspClient> {
    val openOnServer = HashSet<String>()
    for (uri in state.pushes.keys) {
      val file = resolve(client, state, uri) ?: continue
      if (file in state.open) openOnServer.add(uri)
    }
    return Event.Snapshot(client, state.pushes, openOnServer)
  }

  /** E2 gave a `file:` URI; a file the VFS does not know (yet), a directory or a light file is not shown. */
  private fun fileProblem(state: ClientState?, problem: Problem<LspClient>): LspFileProblem? {
    val file = state?.let { resolve(problem.client, it, problem.key.uri) }
    if (file == null) {
      LOG.debug { "${problem.client.descriptor.presentableName}: no file for ${problem.key.uri}, its error is not shown" }
      return null
    }
    return LspFileProblem(this, problem.client, problem.key, problem.diagnostic, file)
  }

  private fun resolve(client: LspClient, state: ClientState, uri: String): VirtualFile? {
    state.files[uri]?.let { if (it.isValid) return it else state.files.remove(uri) }
    val file = client.descriptor.findFileByUri(uri)?.takeIf { it.isValid && !it.isDirectory && it !is LightVirtualFile } ?: return null
    state.files[uri] = file
    return file
  }

  companion object {
    /** A snapshot of a client goes to the model at most this often; the last one always goes. */
    val THROTTLE: Duration = 200.milliseconds

    private fun isLive(client: LspClient): Boolean = when (client.state) {
      LspServerState.Initializing, LspServerState.Running -> true
      LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly -> false
    }
  }
}

/** Starts [LspProjectErrors]; it lives until the project closes. */
internal class LspProjectErrorsActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.serviceAsync<LspProjectErrors>()
  }
}

/**
 * The state of the Project Errors adapter, without coroutines or the platform, so it can be tested alone. [apply] and [dispose] run on
 * one consumer only (the applier coroutine), so the state needs no lock.
 * - A1 [Input.Added] of a live client that is not watched yet starts watching it ([Watcher.watch]); a repeat or a client that is
 *   gone already (removed before its first event was applied) does nothing.
 * - A2 [Input.Removed] stops watching and removes the problems of the client (E8); a later snapshot of it is dropped.
 * - A3 [Input.Changed] of a watched client runs the model; the changes go to [Sink] in model order (disappeared, updated, appeared).
 * - A4 [problem] makes the shown row of a model problem; null skips it (no file), and the model problem stays unshown until it
 *   changes. An update makes a new row: equal to the old one it is [Sink.updated], else the old one disappears and the new one appears.
 * - A5 [dispose] removes every shown row and ignores all later input.
 * [C] is the client identity, [P] the shown row; a row must be equal to the row of the same model problem with the same file.
 */
@ApiStatus.Internal
class LspProjectErrorsApplier<C : Any, P : Any>(
  private val isLive: (C) -> Boolean,
  private val problem: (Problem<C>) -> P?,
  private val sink: Sink<P>,
  private val watcher: Watcher<C>,
  private val cap: Int = LspProjectErrorsModel.CAP,
) {
  sealed interface Input<C> {
    data class Added<C>(val client: C) : Input<C>

    data class Removed<C>(val client: C) : Input<C>

    data class Changed<C>(val snapshot: Event.Snapshot<C>) : Input<C>
  }

  /** Where the rows go: the [ProblemsCollector] in the IDE. */
  interface Sink<P> {
    fun appeared(problem: P)

    fun updated(problem: P)

    fun disappeared(problem: P)
  }

  /** Starts and stops the snapshots of a client; [unwatch] must stop them before any later [watch] of the same client. */
  interface Watcher<C> {
    fun watch(client: C)

    fun unwatch(client: C)
  }

  private var state = State<C>()
  private val watched = LinkedHashSet<C>()
  private val shown = HashMap<Pair<C, Key>, P>()
  private var disposed = false
  private var hidden = 0

  fun apply(input: Input<C>) {
    if (disposed) return
    when (input) {
      is Input.Added -> if (input.client !in watched && isLive(input.client)) {
        watched.add(input.client)
        watcher.watch(input.client)
      }
      is Input.Removed -> if (watched.remove(input.client)) {
        watcher.unwatch(input.client)
        step(Event.Removed(input.client))
      }
      is Input.Changed -> if (input.snapshot.client in watched) step(input.snapshot)
    }
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    for (row in shown.values) sink.disappeared(row)
    shown.clear()
    for (client in watched) watcher.unwatch(client)
    watched.clear()
    state = State()
  }

  private fun step(event: Event<C>) {
    val step = LspProjectErrorsModel.reduce(state, event, cap)
    state = step.state
    for (change in step.changes) {
      val id = change.problem.client to change.problem.key
      when (change) {
        is Change.Disappeared -> shown.remove(id)?.let(sink::disappeared)
        is Change.Updated -> {
          val old = shown.remove(id)
          val new = problem(change.problem)
          when {
            new == null -> old?.let(sink::disappeared)
            old == new -> sink.updated(new)
            else -> {
              old?.let(sink::disappeared)
              sink.appeared(new)
            }
          }
          if (new != null) shown[id] = new
        }
        is Change.Appeared -> problem(change.problem)?.let {
          shown[id] = it
          sink.appeared(it)
        }
      }
    }
    if (step.hidden != hidden) {
      hidden = step.hidden
      LOG.debug { "$hidden LSP errors are over the cap of $cap, not shown" }
    }
  }
}
