// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.problems

import com.intellij.platform.lsp.util.messageIfStringOrEmpty
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.jetbrains.annotations.ApiStatus

/**
 * The Project Errors tab of the LSP Client: the errors a server pushes for files it does not have open. Pure: the adapter owns
 * the [State], feeds [Event]s through [reduce] and applies [Step.changes] to the Problems View. [C] is the client identity (the
 * [com.intellij.platform.lsp.api.LspClient] object in the adapter, so a restarted client is another client). A URI is the raw
 * push string; its form is stable only within one client.
 * - E1 only the pushed snapshot counts: the adapter feeds pushes only, a pull is only for an open document.
 * - E2 only `file:` URIs: a problem needs a file on disk.
 * - E3 a URI open on the server is skipped: the editor and the Current File tab show it. Its push stays in the adapter store
 *   when it closes, so it moves here after the close, until the server clears it.
 * - E4 only [DiagnosticSeverity.Error]; a missing severity is a weak warning in the editor.
 * - E5 one problem per client and [Key]; the first of equal keys wins.
 * - E6 at most `cap` problems in all, in client order (first snapshot first), then URI order, then server order; [Step.hidden]
 *   counts the rest. A removed client frees room, so hidden problems of later clients appear.
 * - E7 a problem with the same client and key but another diagnostic (tags, related information, data) is updated.
 * - E8 a removed client loses its problems; [Event.Cleared] removes all.
 */
@ApiStatus.Internal
object LspProjectErrorsModel {
  /** The default cap (E6): large workspace pushes (rust-analyzer) stay cheap for the view. */
  const val CAP: Int = 1000

  /** The identity of a problem within one client (E5): a different range or message is another problem. */
  data class Key(val uri: String, val range: Range, val code: Either<String, Int>?, val source: String?, val message: String) {
    companion object {
      fun of(uri: String, diagnostic: Diagnostic): Key =
        Key(uri, diagnostic.range, diagnostic.code, diagnostic.source, diagnostic.messageIfStringOrEmpty)
    }
  }

  data class Problem<C>(val client: C, val key: Key, val diagnostic: Diagnostic)

  /** The errors of one client after E1-E5 in order, at most `cap` of them, and the count before the cap. */
  data class ClientErrors(val errors: Map<Key, Diagnostic>, val total: Int)

  /** The clients in first-snapshot order. */
  data class State<C>(val clients: Map<C, ClientErrors> = emptyMap())

  sealed interface Event<out C> {
    /**
     * The pushed diagnostics of [client] per URI as the server sent it, and the URIs open on its server.
     * Each snapshot replaces the previous one of [client].
     */
    data class Snapshot<C>(val client: C, val diagnostics: Map<String, List<Diagnostic>>, val openOnServer: Set<String>) : Event<C>

    data class Removed<C>(val client: C) : Event<C>

    data object Cleared : Event<Nothing>
  }

  sealed interface Change<C> {
    val problem: Problem<C>

    data class Appeared<C>(override val problem: Problem<C>) : Change<C>

    /** [problem] has the new diagnostic of the same client and key (E7). */
    data class Updated<C>(override val problem: Problem<C>) : Change<C>

    data class Disappeared<C>(override val problem: Problem<C>) : Change<C>
  }

  /** [changes]: the disappeared ones first, then the updated, then the appeared ones, each in shown order. */
  data class Step<C>(val state: State<C>, val changes: List<Change<C>>, val hidden: Int)

  fun <C> reduce(state: State<C>, event: Event<C>, cap: Int = CAP): Step<C> {
    val next = when (event) {
      is Event.Snapshot -> State(state.clients + (event.client to errors(event.diagnostics, event.openOnServer, cap)))
      is Event.Removed -> State(state.clients - event.client)
      Event.Cleared -> State()
    }
    val before = shown(state, cap)
    val after = shown(next, cap)
    val changes = ArrayList<Change<C>>()
    before.filterKeys { it !in after }.values.mapTo(changes) { Change.Disappeared(it) }
    for ((id, problem) in after) {
      val old = before[id] ?: continue
      if (old.diagnostic != problem.diagnostic) changes.add(Change.Updated(problem))
    }
    after.filterKeys { it !in before }.values.mapTo(changes) { Change.Appeared(it) }
    return Step(next, changes, next.clients.values.sumOf { it.total } - after.size)
  }

  /** The shown problems (E6) by client and key, in shown order. */
  fun <C> shown(state: State<C>, cap: Int = CAP): Map<Pair<C, Key>, Problem<C>> {
    val result = LinkedHashMap<Pair<C, Key>, Problem<C>>()
    for ((client, errors) in state.clients) {
      for ((key, diagnostic) in errors.errors) {
        if (result.size >= cap) return result
        result[client to key] = Problem(client, key, diagnostic)
      }
    }
    return result
  }

  /** E1-E5 for one snapshot, in URI order then server order; at most [cap] kept, since no client can show more. */
  fun errors(diagnostics: Map<String, List<Diagnostic>>, openOnServer: Set<String>, cap: Int = CAP): ClientErrors {
    val kept = LinkedHashMap<Key, Diagnostic>()
    val seen = HashSet<Key>()
    for ((uri, pushed) in diagnostics.entries.sortedBy { it.key }) {
      if (uri in openOnServer || !isFile(uri)) continue // E2, E3
      for (diagnostic in pushed) {
        if (diagnostic.severity != DiagnosticSeverity.Error) continue // E4
        val key = Key.of(uri, diagnostic)
        if (!seen.add(key)) continue // E5
        if (kept.size < cap) kept[key] = diagnostic
      }
    }
    return ClientErrors(kept, seen.size)
  }

  private fun isFile(uri: String): Boolean = uri.startsWith("file:", ignoreCase = true)
}
