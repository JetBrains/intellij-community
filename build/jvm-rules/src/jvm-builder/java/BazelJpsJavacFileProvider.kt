// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "HardCodedStringLiteral", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.java

import com.intellij.compiler.instrumentation.FailSafeClassReader
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelTargetBuildOutputConsumer
import org.jetbrains.bazel.jvm.worker.core.output.InMemoryJavaOutputFileObject
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.dependencyView.Callbacks.Backend
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.javac.InputFileObject
import org.jetbrains.jps.javac.JpsJavacFileProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.net.URI
import java.nio.file.Path
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.tools.FileObject
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.StandardLocation

// not thread-safe - for one javac call
internal class BazelJpsJavacFileProvider(
  private val outputConsumer: BazelTargetBuildOutputConsumer,
  private val mappingsCallback: Backend?,
  private val outputSink: OutputSink,
  expectedOutputFileCount: Int,
) : JpsJavacFileProvider {
  private val outputs = ArrayList<InMemoryJavaOutputFileObject>(expectedOutputFileCount)

  fun registerOutputs(context: BazelCompileContext, classpath: Array<Path>): Boolean {
    val successfullyCompiled = ObjectLinkedOpenHashSet<File>(outputs.size)
    for (fileObject in outputs) {
      val content = fileObject.content
      val sourceIoFile = fileObject.source
      if (content == null) {
        throw IllegalStateException("File object has no content (outputPath=${fileObject.path}, source=$sourceIoFile)")
      }

      val outKind = fileObject.kind
      if (outKind == JavaFileObject.Kind.CLASS) {
        successfullyCompiled.add(sourceIoFile)
      }

      // first, handle [src->output] mapping and register paths for `files_generated` event
      outputConsumer.registerJavacCompiledClass(
        relativeOutputPath = fileObject.path,
        sourceFile = sourceIoFile.toPath(),
      )

      if (outKind == JavaFileObject.Kind.CLASS && mappingsCallback != null) {
        // register in mappings any non-temp class file
        val reader = FailSafeClassReader(content)
        mappingsCallback.associate(fileObject.path, listOf(sourceIoFile.invariantSeparatorsPath), reader, false)
      }
    }

    instrumentClasses(classpath, outputs, outputSink)

    var hasErrors = false
    outputSink.registerJavacOutput(outputs) { file, message ->
      context.compilerMessage(kind = Kind.ERROR, message = message)
      successfullyCompiled.remove(file)
      JavaBuilderUtil.registerFilesWithErrors(context, listOf(file))
      hasErrors = true
    }

    if (successfullyCompiled.isNotEmpty()) {
      outputConsumer.addRegisteredSourceCount(successfullyCompiled.size)
      JavaBuilderUtil.registerSuccessfullyCompiled(context, successfullyCompiled)
    }

    return hasErrors
  }

  override fun list(
    location: JavaFileManager.Location,
    packageName: String,
    kinds: Set<JavaFileObject.Kind>,
    recurse: Boolean
  ): Iterable<JavaFileObject> {
    if (!kinds.contains(JavaFileObject.Kind.CLASS)) {
      return emptySequence<JavaFileObject>().asIterable()
    }

    return sequence {
      outputSink.findByPackage(packageName, recurse) { relativePath, data, offset, length ->
        yield(InMemoryJavaInputFileObject(path = relativePath, data = data, offset = offset, length = length))
      }
    }.asIterable()
  }

  override fun inferBinaryName(location: JavaFileManager.Location, file: JavaFileObject): String? {
    if (location == StandardLocation.CLASS_PATH && file is InMemoryJavaInputFileObject) {
      return file.path.substringBeforeLast('.').replace('/', '.')
    }
    return null
  }

  override fun getFileForOutput(fileName: String, className: String, sibling: FileObject): JavaFileObject {
    val result = InMemoryJavaOutputFileObject(
      path = fileName.replace(File.separatorChar, '/'),
      source = (sibling as InputFileObject).file!!,
    )
    outputs.add(result)
    return result
  }
}

private class InMemoryJavaInputFileObject(
  @JvmField val path: String,
  private val data: ByteArray,
  private val offset: Int,
  private val length: Int,
) : JavaFileObject {
  override fun getKind(): JavaFileObject.Kind = JavaFileObject.Kind.CLASS

  override fun isNameCompatible(simpleName: String, kind: JavaFileObject.Kind): Boolean {
    return kind == JavaFileObject.Kind.CLASS && simpleName == path
  }

  override fun getNestingKind(): NestingKind? = null

  override fun getAccessLevel(): Modifier? = null

  override fun toUri(): URI = throw UnsupportedOperationException()

  override fun getName(): String = path

  override fun openOutputStream(): OutputStream = throw IllegalStateException()

  override fun openWriter(): Writer = throw IllegalStateException()

  override fun openInputStream(): InputStream = ByteArrayInputStream(data, offset, length)

  override fun openReader(ignoreEncodingErrors: Boolean): Reader = getCharContent(true).reader()

  override fun getCharContent(ignoreEncodingErrors: Boolean): String {
    return data.decodeToString(startIndex = offset, endIndex = offset + length)
  }

  override fun getLastModified(): Long = 1

  override fun delete(): Boolean = throw IllegalStateException()
}