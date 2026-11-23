// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.problems

import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Change
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Event
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.State
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Step
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DiagnosticTag
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The Project Errors model rules E1-E8 (pure). */
class LspProjectErrorsModelTest {
  private val a = "file:///d/a.f"
  private val b = "file:///d/b.f"
  private val c = "file:///d/c.f"

  private fun error(message: String, line: Int = 0, severity: DiagnosticSeverity? = DiagnosticSeverity.Error) =
    Diagnostic(Range(Position(line, 0), Position(line, 1)), message).also { it.severity = severity }

  private fun Diagnostic.with(
    tags: List<DiagnosticTag>? = this.tags,
    code: Either<String, Int>? = this.code,
    source: String? = this.source,
  ): Diagnostic = Diagnostic(range, "").also {
    it.message = message
    it.severity = severity
    it.tags = tags
    it.code = code
    it.source = source
  }

  private fun snapshot(client: String, vararg entries: Pair<String, List<Diagnostic>>, open: Set<String> = emptySet()): Event<String> =
    Event.Snapshot(client, entries.toMap(), open)

  private fun reduceAll(vararg events: Event<String>, cap: Int = LspProjectErrorsModel.CAP): Step<String> {
    var step = Step(State<String>(), emptyList(), 0)
    for (event in events) step = LspProjectErrorsModel.reduce(step.state, event, cap)
    return step
  }

  private fun Step<String>.then(event: Event<String>, cap: Int = LspProjectErrorsModel.CAP) = LspProjectErrorsModel.reduce(state, event, cap)

  private fun Step<String>.shown(cap: Int = LspProjectErrorsModel.CAP): List<String> =
    LspProjectErrorsModel.shown(state, cap).values.map { "${it.client}:${it.key.uri.substringAfterLast('/')}:${it.key.message}" }

  private fun List<Change<String>>.described(): List<String> = map {
    val kind = when (it) {
      is Change.Appeared -> "+"
      is Change.Updated -> "~"
      is Change.Disappeared -> "-"
    }
    "$kind${it.problem.client}:${it.problem.key.message}"
  }

  @Test
  fun `E2-E4 only errors of closed file URIs appear`() {
    val step = reduceAll(snapshot(
      "s",
      a to listOf(error("pushed")),
      b to listOf(error("open")),
      "jar:file:///d/l.jar!/x.f" to listOf(error("jar")),
      c to listOf(error("warning", severity = DiagnosticSeverity.Warning), error("no severity", severity = null), error("error")),
      open = setOf(b),
    ))
    assertEquals(listOf("+s:pushed", "+s:error"), step.changes.described())
    assertEquals(0, step.hidden)
  }

  @Test
  fun `E3 a document opened on the server leaves and comes back after the close`() {
    val opened = reduceAll(snapshot("s", a to listOf(error("x")))).then(snapshot("s", a to listOf(error("x")), open = setOf(a)))
    assertEquals(listOf("-s:x"), opened.changes.described())
    val closed = opened.then(snapshot("s", a to listOf(error("x"))))
    assertEquals(listOf("+s:x"), closed.changes.described())
  }

  @Test
  fun `the same snapshot again changes nothing`() {
    val first = reduceAll(snapshot("s", a to listOf(error("x"), error("y", line = 1))))
    assertEquals(emptyList<Change<String>>(), first.then(snapshot("s", a to listOf(error("x"), error("y", line = 1)))).changes)
  }

  @Test
  fun `E5 equal keys in one client are one problem, in two clients two`() {
    val code = error("x").with(code = Either.forLeft("E1"), source = "lint")
    val step = reduceAll(snapshot("s", a to listOf(code, code.with(tags = listOf(DiagnosticTag.Deprecated)))), snapshot("t", a to listOf(code)))
    assertEquals(listOf("s:a.f:x", "t:a.f:x"), step.shown())
    assertEquals(null, LspProjectErrorsModel.shown(step.state).values.first().diagnostic.tags, "the first of equal keys wins")
    val otherCode = step.then(snapshot("s", a to listOf(code, code.with(code = Either.forLeft("E2")))))
    assertEquals(listOf("+s:x"), otherCode.changes.described(), "another code is another key")
  }

  @Test
  fun `E7 the same key with another diagnostic is updated, another range is another problem`() {
    val first = reduceAll(snapshot("s", a to listOf(error("x"), error("y"))))
    val tagged = first.then(snapshot("s", a to listOf(error("x").with(tags = listOf(DiagnosticTag.Deprecated)), error("y"))))
    assertEquals(listOf("~s:x"), tagged.changes.described())
    assertEquals(listOf(DiagnosticTag.Deprecated), (tagged.changes.single() as Change.Updated).problem.diagnostic.tags)
    val moved = tagged.then(snapshot("s", a to listOf(error("x").with(tags = listOf(DiagnosticTag.Deprecated)), error("y", line = 3))))
    assertEquals(listOf("-s:y", "+s:y"), moved.changes.described())
  }

  @Test
  fun `E6 the cap keeps client order, then URI order, then server order`() {
    val step = reduceAll(
      snapshot("s", b to listOf(error("b1"), error("b2", line = 1)), a to listOf(error("a1"))),
      snapshot("t", a to listOf(error("t1"))),
      cap = 2,
    )
    assertEquals(listOf("s:a.f:a1", "s:b.f:b1"), step.shown(cap = 2))
    assertEquals(2, step.hidden)
    val again = step.then(snapshot("s", a to listOf(error("a1"))), cap = 2)
    assertEquals(listOf("-s:b1", "+t:t1"), again.changes.described(), "a new snapshot of s keeps its place before t")
    assertEquals(0, again.hidden)
  }

  @Test
  fun `E6 a removed client frees room for hidden problems`() {
    val step = reduceAll(snapshot("s", a to listOf(error("s1"), error("s2", line = 1))), snapshot("t", a to listOf(error("t1"))), cap = 2)
    assertEquals(1, step.hidden)
    val removed = step.then(Event.Removed("s"), cap = 2)
    assertEquals(listOf("-s:s1", "-s:s2", "+t:t1"), removed.changes.described())
    assertEquals(0, removed.hidden)
  }

  @Test
  fun `E8 a removed client loses its problems, an unknown one changes nothing, cleared removes all`() {
    val step = reduceAll(snapshot("s", a to listOf(error("x"))), snapshot("t", b to listOf(error("y"))))
    assertEquals(listOf("-s:x"), step.then(Event.Removed("s")).changes.described())
    assertEquals(emptyList<Change<String>>(), step.then(Event.Removed("u")).changes)
    val cleared = step.then(Event.Cleared)
    assertEquals(listOf("-s:x", "-t:y"), cleared.changes.described())
    assertEquals(State<String>(), cleared.state)
  }

  @Test
  fun `an empty snapshot removes the problems of the client`() {
    val step = reduceAll(snapshot("s", a to listOf(error("x")))).then(snapshot("s", a to emptyList()))
    assertEquals(listOf("-s:x"), step.changes.described())
  }
}
