// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class JUnitXmlWriterTest {
  @Test
  fun `report shape matches what Bazel consumes`() = runBlocking {
    val xml = write(feed(
      "##teamcity[testSuiteStarted name='sample.CalculatorTest']",
      "##teamcity[testStarted name='addition']",
      "hello",
      "##teamcity[testFinished name='addition' duration='12']",
      "##teamcity[testStarted name='division']",
      "##teamcity[testFailed name='division' message='expected 2' details='stack|ntrace']",
      "##teamcity[testFinished name='division' duration='7']",
      "##teamcity[testIgnored name='modulo' message='not implemented']",
      "##teamcity[testSuiteFinished name='sample.CalculatorTest']",
    ))

    assertEquals(fixture("report.xml"), xml)
  }

  @Test
  fun `errored tests produce error elements and error counts`() = runBlocking {
    // A test the run never saw finish: the interruption renders it as errored.
    val run = feed(
      "##teamcity[testStarted name='finishes']",
      "##teamcity[testFinished name='finishes' duration='3']",
      "##teamcity[testStarted name='hangs']",
    )
    run.interrupt("deadline reached")

    assertEquals(fixture("interrupted-run.xml"), write(run))
  }

  @Test
  fun `xml-significant characters survive a parse round-trip`() = runBlocking {
    // Escaping is the XMLStreamWriter's job; this pins that names, messages and details with
    // markup-significant characters come back byte-exact through a real XML parser.
    val xml = write(feed(
      "##teamcity[testSuiteStarted name='suite <&> \"q\"']",
      "##teamcity[testStarted name='test <&>']",
      "##teamcity[testFailed name='test <&>' message='a < b & c > d' details='details with <tags> & \"quotes\"']",
      "##teamcity[testFinished name='test <&>' duration='1']",
      "##teamcity[testSuiteFinished name='suite <&> \"q\"']",
    ))

    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
    val suite = document.getElementsByTagName("testsuite").item(0) as Element
    assertEquals("suite <&> \"q\"", suite.getAttribute("name"))
    val testCase = document.getElementsByTagName("testcase").item(0) as Element
    assertEquals("test <&>", testCase.getAttribute("name"))
    val failure = document.getElementsByTagName("failure").item(0) as Element
    assertEquals("a < b & c > d", failure.getAttribute("message"))
    assertEquals("details with <tags> & \"quotes\"", failure.textContent)
  }

  @Test
  fun `illegal xml characters are dropped`() = runBlocking {
    // Straight through `fail`, so the control characters reach the writer unfiltered by the parser.
    val details = "a" + 0.toChar() + "b" + 7.toChar() + "c"

    val xml = write(TestRunState().apply { addSyntheticFailure(SyntheticTestFailure.INFRASTRUCTURE_FAILURE, details) })

    assertEquals(fixture("illegal-characters.xml"), xml)
  }

  private suspend fun feed(vararg lines: String): TestRunState = TestRunState().apply {
    lines.forEach { line -> consume(line) }
  }

  private suspend fun write(run: TestRunState): String {
    // A no-op on an already-closed run (the interrupt case): the first close renders the report.
    run.complete()
    val outcome = run.outcome.await() as TestRunOutcome.Completed
    val out = ByteArrayOutputStream()
    writeJUnitXml(outcome.suites, out)
    return out.toString(Charsets.UTF_8)
  }

  private fun fixture(name: String): String =
    checkNotNull(javaClass.getResourceAsStream("fixtures/$name")) { "missing fixture: $name" }.use { stream ->
      stream.readBytes().decodeToString()
    }
}
