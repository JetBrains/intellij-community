// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.problems

import com.intellij.platform.lsp.impl.problems.LspProjectErrorsApplier.Input
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Event
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Key
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DiagnosticTag
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** The adapter rules A1-A5 (pure: string clients, a recording sink and watcher, a fake file lookup). */
class LspProjectErrorsAdapterTest {
  private val a = "file:///d/a.f"
  private val b = "file:///d/b.f"

  /** A shown row: equal rows have the same client, key and file, as [LspFileProblem]. */
  private class Row(val client: String, val key: Key, val file: String, val diagnostic: Diagnostic) {
    override fun equals(other: Any?): Boolean = other is Row && other.client == client && other.key == key && other.file == file

    override fun hashCode(): Int = (client.hashCode() * 31 + key.hashCode()) * 31 + file.hashCode()
  }

  /** A fresh fixture per test: no shared state. [files] maps a URI to its file name, a missing one has no file. */
  private class Fixture(val live: MutableSet<String> = mutableSetOf("s", "t"), val files: MutableMap<String, String> = mutableMapOf()) {
    val log = ArrayList<String>()
    val rows = ArrayList<Row>()

    val applier = LspProjectErrorsApplier(
      isLive = { it in live },
      problem = { problem -> files[problem.key.uri]?.let { Row(problem.client, problem.key, it, problem.diagnostic) } },
      sink = object : LspProjectErrorsApplier.Sink<Row> {
        override fun appeared(problem: Row) {
          rows.add(problem)
          log.add("+${problem.client}:${problem.key.message}@${problem.file}")
        }

        override fun updated(problem: Row) {
          rows.add(problem)
          log.add("~${problem.client}:${problem.key.message}@${problem.file}")
        }

        override fun disappeared(problem: Row) {
          log.add("-${problem.client}:${problem.key.message}@${problem.file}")
        }
      },
      watcher = object : LspProjectErrorsApplier.Watcher<String> {
        override fun watch(client: String) {
          log.add("watch $client")
        }

        override fun unwatch(client: String) {
          log.add("unwatch $client")
        }
      },
    )

    fun apply(vararg inputs: Input<String>): List<String> {
      log.clear()
      for (input in inputs) applier.apply(input)
      return log.toList()
    }
  }

  private fun error(message: String, line: Int = 0) =
    Diagnostic(Range(Position(line, 0), Position(line, 1)), message).also { it.severity = DiagnosticSeverity.Error }

  private fun Diagnostic.with(tags: List<DiagnosticTag>): Diagnostic = Diagnostic(range, "").also {
    it.message = message
    it.severity = severity
    it.tags = tags
  }

  private fun changed(client: String, vararg entries: Pair<String, List<Diagnostic>>, open: Set<String> = emptySet()): Input<String> =
    Input.Changed(Event.Snapshot(client, entries.toMap(), open))

  private fun fixture() = Fixture(files = mutableMapOf(a to "a", b to "b"))

  @Test
  fun `A1 the first event adds a client once, a gone client is not watched`() {
    val f = fixture()
    assertEquals(listOf("watch s"), f.apply(Input.Added("s"), Input.Added("s")))
    assertEquals(emptyList<String>(), f.apply(Input.Added("u")), "not live")
    f.live.remove("t")
    assertEquals(emptyList<String>(), f.apply(Input.Removed("t"), Input.Added("t")), "removed before its first event was applied")
    assertEquals(emptyList<String>(), f.apply(changed("t", a to listOf(error("x")))), "a client that is not watched")
  }

  @Test
  fun `A3 snapshots of a watched client go through the model`() {
    val f = fixture()
    f.apply(Input.Added("s"))
    assertEquals(listOf("+s:x@a", "+s:y@b"), f.apply(changed("s", a to listOf(error("x")), b to listOf(error("y")))))
    assertEquals(emptyList<String>(), f.apply(changed("s", a to listOf(error("x")), b to listOf(error("y")))), "the same snapshot")
    assertEquals(listOf("-s:y@b"), f.apply(changed("s", a to listOf(error("x")), b to listOf(error("y")), open = setOf(b))), "opened on the server")
    assertEquals(listOf("-s:x@a"), f.apply(changed("s", a to emptyList(), b to listOf(error("y")), open = setOf(b))), "cleared by the server")
  }

  @Test
  fun `A4 an update swaps an equal row, a moved file is another row, no file is no row`() {
    val f = fixture()
    f.apply(Input.Added("s"), changed("s", a to listOf(error("x"))))
    val tagged = error("x").with(tags = listOf(DiagnosticTag.Deprecated))
    assertEquals(listOf("~s:x@a"), f.apply(changed("s", a to listOf(tagged))))
    assertSame(tagged, f.rows.last().diagnostic)

    f.files[a] = "a2"
    assertEquals(listOf("-s:x@a", "+s:x@a2"), f.apply(changed("s", a to listOf(error("x")))), "the URI maps to another file now")

    f.files.remove(a)
    assertEquals(listOf("-s:x@a2"), f.apply(changed("s", a to listOf(tagged))), "the file is gone")
    assertEquals(emptyList<String>(), f.apply(changed("s", a to emptyList())), "a model problem with no row disappears quietly")

    assertEquals(emptyList<String>(), f.apply(changed("s", a to listOf(error("z")))), "no file, no row")
    f.files[a] = "a"
    assertEquals(listOf("+s:z@a"), f.apply(changed("s", a to listOf(error("z").with(tags = listOf(DiagnosticTag.Unnecessary))))), "an update of an unshown problem appears")
  }

  @Test
  fun `A2 a removed client loses its rows, its late snapshot is dropped`() {
    val f = fixture()
    f.apply(Input.Added("s"), Input.Added("t"), changed("s", a to listOf(error("x"))), changed("t", a to listOf(error("y"))))
    assertEquals(listOf("unwatch s", "-s:x@a"), f.apply(Input.Removed("s")))
    assertEquals(emptyList<String>(), f.apply(changed("s", a to listOf(error("x")))), "a snapshot sent before the job stopped")
    assertEquals(emptyList<String>(), f.apply(Input.Removed("s")), "removed twice")
    assertEquals(listOf("-t:y@a"), f.apply(changed("t", a to emptyList())), "the other client goes on")
  }

  @Test
  fun `A5 dispose removes every row and ignores later input`() {
    val f = fixture()
    f.apply(Input.Added("s"), Input.Added("t"), changed("s", a to listOf(error("x"))), changed("t", b to listOf(error("y"))))
    f.log.clear()
    f.applier.dispose()
    assertEquals(listOf("-s:x@a", "-t:y@b", "unwatch s", "unwatch t"), f.log.sorted())
    assertEquals(emptyList<String>(), f.apply(Input.Added("s"), changed("t", b to listOf(error("y"))), Input.Removed("t")))
    f.log.clear()
    f.applier.dispose()
    assertEquals(emptyList<String>(), f.log, "dispose twice")
  }

  @Test
  fun `the text is the first non-blank line of the message`() {
    assertEquals("mismatched types", LspFileProblem.text(error("\n  mismatched types  \nexpected `i32`")))
    assertEquals("x", LspFileProblem.text(error("x")))
  }
}
