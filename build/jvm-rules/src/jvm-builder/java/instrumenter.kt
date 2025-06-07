@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.java

import androidx.collection.MutableScatterMap
import com.intellij.compiler.instrumentation.FailSafeClassReader
import com.intellij.compiler.instrumentation.InstrumentationClassFinder
import com.intellij.compiler.instrumentation.InstrumenterClassWriter
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter
import org.jetbrains.bazel.jvm.worker.core.output.InMemoryJavaOutputFileObject
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.jps.devkit.threadingModelHelper.TMHAssertionGenerator1
import org.jetbrains.jps.devkit.threadingModelHelper.TMHAssertionGenerator2
import org.jetbrains.jps.devkit.threadingModelHelper.TMHInstrumenter
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path

private val notNullAnnotations = arrayOf("org.jetbrains.annotations.NotNull")

internal fun instrumentClasses(classpath: Array<Path>, outputs: ArrayList<InMemoryJavaOutputFileObject>, outputSink: OutputSink) {
  val finder = BazelInstrumentationClassFinder(classpath, outputs, outputSink)
  for (output in outputs) {
    if (output.path == "module-info.class") {
      continue
    }

    executeInstrumentation(finder, output) { reader, writer ->
      NotNullVerifyingInstrumenter.processClassFile(reader, writer, notNullAnnotations)
    }
    executeInstrumentation(finder, output) { reader, writer ->
      val generators = if (hasThreadingAssertions(finder)) TMHAssertionGenerator2.generators() else TMHAssertionGenerator1.generators()
      TMHInstrumenter.instrument(reader, writer, generators, true)
    }
  }
}

private fun executeInstrumentation(
  finder: BazelInstrumentationClassFinder,
  output: InMemoryJavaOutputFileObject,
  executor: ((ClassReader, InstrumenterClassWriter) -> Boolean),
) {
  val reader = FailSafeClassReader(output.content!!)

  val version = InstrumenterClassWriter.getClassFileVersion(reader)
  if ((version and 0xFFFF) < Opcodes.V1_5) {
    return
  }

  val writer = InstrumenterClassWriter(reader, InstrumenterClassWriter.getAsmClassWriterFlags(version), finder)
  if (executor(reader, writer)) {
    output.content = writer.toByteArray()
  }
}

private fun hasThreadingAssertions(finder: InstrumentationClassFinder): Boolean {
  try {
    finder.loadClass("com/intellij/util/concurrency/ThreadingAssertions")
    return true
  }
  catch (_: IOException) {
    return false
  }
  catch (_: ClassNotFoundException) {
    return false
  }
}

private class BazelInstrumentationClassFinder(
  classpath: Array<Path>,
  outputs: List<InMemoryJavaOutputFileObject>,
  private val outputSink: OutputSink,
) : InstrumentationClassFinder(emptyArray(), classpath.map { it.toUri().toURL() }.toTypedArray()) {
  private val classNameToContent by lazy {
    val result = MutableScatterMap<String, ByteArray>(outputs.size)
    for (output in outputs) {
      result.put(output.path.removeSuffix(".class"), output.content!!)
    }
    result
  }

  override fun lookupClassBeforeClasspath(internalClassName: String): InputStream? {
    val content = classNameToContent.get(internalClassName)
    if (content != null) {
      return ByteArrayInputStream(content)
    }

    // kotlin-produced files or previously compiled classes
    return outputSink.findByJavaInternalClassName(internalClassName)?.let {
      ByteArrayInputStream(it)
    }
  }

  override fun lookupClassAfterClasspath(internalClassName: String): InputStream? {
    return ClassLoader.getSystemResourceAsStream("$internalClassName.class")
  }
}