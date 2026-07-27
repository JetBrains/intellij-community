// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import java.io.OutputStream
import java.util.Locale
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

/**
 * Writes rendered suites as the JUnit XML shape Bazel consumes from `XML_OUTPUT_FILE`:
 * `<testsuites><testsuite><testcase><failure|skipped/>...`.
 *
 * Elements are indented one level per nesting depth so a failed run is readable in the report;
 * the character content of `failure`, `error` and `system-out` stays inline, keeping the reported
 * messages and stacktraces byte-exact.
 */
internal fun writeJUnitXml(suites: List<TestSuiteResult>, out: OutputStream) {
  val writer = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(out, "UTF-8")
  try {
    writer.writeStartDocument("UTF-8", "1.0")
    writer.indent(0)
    writer.writeStartElement("testsuites")
    writer.writeAttribute("tests", suites.testCount.toString())
    writer.writeAttribute("failures", suites.failedCount.toString())
    writer.writeAttribute("errors", suites.erroredCount.toString())
    writer.writeAttribute("skipped", suites.ignoredCount.toString())
    writer.writeAttribute("time", suites.sumOf(TestSuiteResult::durationMillis).toXmlSeconds())
    suites.forEach { writer.writeSuite(it) }
    writer.indent(0)
    writer.writeEndElement()
    writer.writeEndDocument()
  }
  finally {
    writer.close()
  }
  // A writer cannot emit anything after the root element, so the trailing newline that makes the
  // report a well-formed text file is appended to the stream itself (close() leaves it open).
  out.write('\n'.code)
}

private fun XMLStreamWriter.writeSuite(suite: TestSuiteResult) {
  indent(1)
  writeStartElement("testsuite")
  writeAttribute("name", suite.name.sanitized())
  writeAttribute("tests", suite.tests.size.toString())
  writeAttribute("failures", suite.tests.count { it.status is TestStatus.Failed }.toString())
  writeAttribute("errors", suite.tests.count { it.status is TestStatus.Errored }.toString())
  writeAttribute("skipped", suite.tests.count { it.status is TestStatus.Ignored }.toString())
  writeAttribute("time", suite.durationMillis.toXmlSeconds())
  suite.tests.forEach { writeTestCase(it) }
  indent(1)
  writeEndElement()
}

private fun XMLStreamWriter.writeTestCase(test: TestCaseResult) {
  indent(2)
  writeStartElement("testcase")
  writeAttribute("classname", test.className.sanitized())
  writeAttribute("name", test.name.sanitized())
  writeAttribute("time", test.durationMillis.toXmlSeconds())
  when (test.status) {
    is TestStatus.Passed -> Unit
    is TestStatus.Failed -> {
      indent(3)
      writeStartElement("failure")
      writeAttribute("message", test.status.message.sanitized())
      writeCharacters(test.status.details.sanitized())
      writeEndElement()
    }
    is TestStatus.Errored -> {
      indent(3)
      writeStartElement("error")
      writeAttribute("message", test.status.message.sanitized())
      writeCharacters(test.status.details.sanitized())
      writeEndElement()
    }
    is TestStatus.Ignored -> {
      indent(3)
      writeStartElement("skipped")
      writeAttribute("message", test.status.reason.sanitized())
      writeEndElement()
    }
  }
  when {
    test.output.isNotEmpty() -> {
      indent(3)
      writeStartElement("system-out")
      writeCharacters(test.output.joinToString("\n").sanitized())
      writeEndElement()
    }
    else -> Unit
  }
  // A passed test with no output has no children: closing it on the same line keeps `<testcase>`
  // free of whitespace-only content.
  when {
    test.hasChildElements -> indent(2)
    else -> Unit
  }
  writeEndElement()
}

private fun XMLStreamWriter.indent(depth: Int) = writeCharacters("\n" + "  ".repeat(depth))

private val TestCaseResult.hasChildElements: Boolean
  get() = status !is TestStatus.Passed || output.isNotEmpty()

private val TestSuiteResult.durationMillis: Long
  get() = tests.sumOf(TestCaseResult::durationMillis)

private fun Long.toXmlSeconds(): String = String.format(Locale.ROOT, "%.3f", this / 1000.0)

/** Drops characters that are not legal in XML 1.0 documents (e.g. control characters in stacktraces). */
private fun String.sanitized(): String = filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
