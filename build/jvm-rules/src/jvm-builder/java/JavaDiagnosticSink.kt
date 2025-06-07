// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage")

package org.jetbrains.bazel.jvm.worker.java

import com.intellij.openapi.util.NlsSafe
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import org.jetbrains.bazel.jvm.util.emptySet
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.jps.incremental.java.CustomOutputDataListener
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.javac.DiagnosticOutputConsumer
import org.jetbrains.jps.javac.ExternalJavacManager
import org.jetbrains.jps.javac.JavacFileReferencesRegistrar
import org.jetbrains.jps.javac.JpsInfoDiagnostic
import org.jetbrains.jps.javac.ast.api.JavacFileData
import org.jetbrains.jps.service.JpsServiceManager
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.tools.Diagnostic
import javax.tools.JavaFileObject

internal class JavaDiagnosticSink(
  private val context: BazelCompileContext,
  private val registrars: MutableCollection<JavacFileReferencesRegistrar>,
  private val out: Appendable,
) : DiagnosticOutputConsumer {
  private val errorCounter = AtomicInteger(0)
  private val warningCounter = AtomicInteger(0)
  private val filesWithErrors = ObjectLinkedOpenHashSet<File>()

  fun getFilesWithErrors(): Set<File> = filesWithErrors.ifEmpty { emptySet() }

  val errorCount: Int
    get() = errorCounter.get()

  val warningCount: Int
    get() = warningCounter.get()

  override fun javaFileLoaded(file: File?) {
  }

  override fun registerJavacFileData(data: JavacFileData) {
    for (registrar in registrars) {
      registrar.registerFile(
        context,
        data.filePath,
        data.refs.entries,
        data.defs,
        data.casts,
        data.implicitToStringRefs,
      )
    }
  }

  override fun customOutputData(pluginId: String, dataName: String?, data: ByteArray) {
    if (JavacFileData.CUSTOM_DATA_PLUGIN_ID == pluginId && JavacFileData.CUSTOM_DATA_KIND == dataName) {
      registerJavacFileData(JavacFileData.fromBytes(data))
    }
    else {
      for (listener in JpsServiceManager.getInstance().getExtensions(CustomOutputDataListener::class.java)) {
        if (pluginId == listener.id) {
          listener.processData(context, dataName, data)
          return
        }
      }
    }
  }

  override fun outputLineAvailable(line: @NlsSafe String?) {
    if (line.isNullOrEmpty()) {
      return
    }

    when {
      line.startsWith(ExternalJavacManager.STDOUT_LINE_PREFIX) || line.startsWith(ExternalJavacManager.STDERR_LINE_PREFIX) -> {
        out.appendLine(line)
      }

      line.contains("java.lang.OutOfMemoryError") -> {
        context.compilerMessage(kind = BuildMessage.Kind.ERROR, message = "OutOfMemoryError: insufficient memory")
        errorCounter.incrementAndGet()
      }

      else -> {
        out.appendLine(line)
      }
    }
  }

  override fun report(diagnostic: Diagnostic<out JavaFileObject>) {
    val kind = when (diagnostic.kind) {
      Diagnostic.Kind.ERROR -> {
        errorCounter.incrementAndGet()
        BuildMessage.Kind.ERROR
      }

      Diagnostic.Kind.MANDATORY_WARNING, Diagnostic.Kind.WARNING -> {
        warningCounter.incrementAndGet()
        BuildMessage.Kind.WARNING
      }

      // do not print warnings about x-lint
      Diagnostic.Kind.NOTE -> return
      Diagnostic.Kind.OTHER -> if (diagnostic is JpsInfoDiagnostic) BuildMessage.Kind.JPS_INFO else BuildMessage.Kind.OTHER
      else -> BuildMessage.Kind.OTHER
    }

    val source: JavaFileObject? = diagnostic.getSource()
    val sourceFile = if (source == null) null else File(source.toUri())
    val sourcePath = if (sourceFile == null) {
      null
    }
    else {
      if (kind == BuildMessage.Kind.ERROR) {
        filesWithErrors.add(sourceFile)
      }
      sourceFile.invariantSeparatorsPath
    }

    val message = diagnostic.getMessage(Locale.US)
    context.compilerMessage(
      kind = kind,
      message = message,
      sourcePath = sourcePath,
      line = diagnostic.lineNumber.toInt(),
      column = diagnostic.columnNumber.toInt(),
    )
  }
}
