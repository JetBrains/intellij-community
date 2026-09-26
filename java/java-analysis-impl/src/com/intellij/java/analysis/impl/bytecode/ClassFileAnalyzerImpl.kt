// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.bytecode

import com.intellij.java.analysis.bytecode.ClassFileAnalyzer
import com.intellij.java.analysis.bytecode.JvmBytecodeDeclarationProcessor
import com.intellij.java.analysis.bytecode.JvmBytecodeReferenceProcessor
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

internal class ClassFileAnalyzerImpl(
  declarationProcessor: JvmBytecodeDeclarationProcessor?,
  referenceProcessor: JvmBytecodeReferenceProcessor?,
  implicitAncestorReferencesResolver: ClassAncestorResolver? = null,
) : ClassFileAnalyzer {
  private val visitor = ClassFileAnalysisVisitor(declarationProcessor, referenceProcessor, implicitAncestorReferencesResolver)

  override fun processFile(path: Path) {
    // ASM ClassReader in any case reads the whole file into memory
    processFileContent(Files.readAllBytes(path))
  }

  @Deprecated("Use processFileContent instead", replaceWith = ReplaceWith("processFileContent(classFileContent)"))
  override fun processData(data: ByteArray) {
    processFileContent(data)
  }

  override fun processFileContent(classFileContent: ByteArray) {
    visitor.processFileContent(classFileContent)
  }

  override fun processInputStream(inputStream: InputStream) {
    processFileContent(inputStream.readAllBytes())
  }

  override fun processClassFiles(root: Path, relativePathFilter: (String) -> Boolean) {
    withClassRootEntries(root) { entries ->
      entries
        .filter { relativePathFilter(it.entryName) }
        .forEach { entry -> processFile(entry.path) }
    }
  }
}

internal data class ClassFileEntry(val entryName: String, val path: Path)

@OptIn(ExperimentalPathApi::class)
internal fun <R> withClassRootEntries(classRoot: Path, block: (entries: Sequence<ClassFileEntry>) -> R): R {
  return withClassRoot(classRoot) { nioRoot ->
    val sequence = nioRoot
      .walk()
      .filter { path ->
        path.extension == "class" && !classRoot.relativize(path).startsWith("META-INF/")
      }
      .map { ClassFileEntry(it.relativeTo(nioRoot).invariantSeparatorsPathString, it) }
    block(sequence)
  }
}

internal fun <R> withClassRoot(classRoot: Path, block: (root: Path) -> R): R {
  return when {
    classRoot.isDirectory() -> block(classRoot)
    classRoot.isRegularFile() && classRoot.extension == "jar" -> {
      FileSystems.newFileSystem(classRoot).use {
        block(it.rootDirectories.single())
      }
    }
    else -> error("Unsupported classes output root: $classRoot")
  }
}
