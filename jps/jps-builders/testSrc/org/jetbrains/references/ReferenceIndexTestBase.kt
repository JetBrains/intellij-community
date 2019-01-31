// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.references

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.PathUtil
import com.intellij.util.indexing.IndexId
import com.intellij.util.indexing.impl.MapIndexStorage
import com.intellij.util.indexing.impl.MapReduceIndex
import com.intellij.util.io.PersistentStringEnumerator
import org.jetbrains.jps.backwardRefs.*
import org.jetbrains.jps.backwardRefs.index.CompiledFileData
import org.jetbrains.jps.backwardRefs.index.JavaCompilerIndices
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.builders.TestProjectBuilderLogger
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import java.io.File

abstract class ReferenceIndexTestBase : JpsBuildTestCase() {
  public override fun setUp() {
    super.setUp()
    System.setProperty(JavaBackwardReferenceIndexWriter.PROP_KEY, true.toString())
  }

  public override fun tearDown() {
    super.tearDown()
    System.clearProperty(JavaBackwardReferenceIndexWriter.PROP_KEY)
  }

  protected fun assertIndexOnRebuild(vararg files: String) {
    var representativeFile: String? = null
    for (file in files) {
      val addedFile = addFile(file)
      if (representativeFile == null) {
        representativeFile = addedFile
      }
    }
    addModule("m", PathUtil.getParentPath(representativeFile!!))
    rebuildAllModules()
    assertIndexEquals("initialIndex.txt")
  }

  protected fun renameFile(fileToRename: String, newName: String) {
    rename(orCreateProjectDir.path + "/m/" + fileToRename, newName)
  }

  protected fun changeFileContent(name: String, changesSourceFile: String) {
    changeFile("m/" + name, FileUtil.loadFile(File(testDataRootPath + "/" + getTestName(true) + "/" + changesSourceFile), CharsetToolkit.UTF8_CHARSET))
  }

  protected fun addFile(name: String): String {
    return createFile("m/" + name, FileUtil.loadFile(File(getTestDataPath() + name), CharsetToolkit.UTF8_CHARSET))
  }


  protected fun assertIndexEquals(expectedIndexDumpFile: String) {
    assertSameLinesWithFile(testDataRootPath + "/" + getTestName(true) + "/" + expectedIndexDumpFile, indexAsText())
  }

  protected fun indexAsText(): String {
    val pd = createProjectDescriptor(BuildLoggingManager(TestProjectBuilderLogger()))
    val manager = pd.dataManager
    val buildDir = manager.dataPaths.dataStorageRoot
    val index = JavaCompilerBackwardReferenceIndex(buildDir, true)

    try {
      val fileEnumerator = index.filePathEnumerator
      val nameEnumerator = index.byteSeqEum

      val result = StringBuilder()
      result.append("Backward Hierarchy:\n")
      val hierarchyText = mutableListOf<String>()
      storage(index, JavaCompilerIndices.BACK_HIERARCHY).processKeys { superClass ->
        val superClassName = superClass.asText(nameEnumerator)
        val inheritorsText = mutableListOf<String>()
        index[JavaCompilerIndices.BACK_HIERARCHY].getData(superClass).forEach { i, children ->
          children.mapTo(inheritorsText) { it.asText(nameEnumerator) }
          true
        }
        if (!inheritorsText.isEmpty()) {
          inheritorsText.sort()
          hierarchyText.add(superClassName + " -> " + inheritorsText.joinToString(separator = " "))
        }
        true
      }
      hierarchyText.sort()
      result.append(hierarchyText.joinToString(separator = "\n"))

      result.append("\n\nBackward References:\n")
      val referencesText = mutableListOf<String>()
      storage(index, JavaCompilerIndices.BACK_USAGES).processKeys { usage ->
        val referents = mutableListOf<String>()
        val valueIt = index[JavaCompilerIndices.BACK_USAGES].getData(usage)

        var sumOccurrences = 0
        valueIt.forEach({ fileId, occurrenceCount ->
                          referents.add(fileId.asFileName(fileEnumerator))
                          sumOccurrences += occurrenceCount
                          true
                        })
        if (!referents.isEmpty()) {
          referents.sort()
          referencesText.add(usage.asText(nameEnumerator) + " in " + referents.joinToString(separator = " ") + " occurrences = $sumOccurrences")
        }
        true
      }
      referencesText.sort()
      result.append(referencesText.joinToString(separator = "\n"))

      result.append("\n\nClass Definitions:\n")
      val classDefs = mutableListOf<String>()
      storage(index, JavaCompilerIndices.BACK_CLASS_DEF).processKeys { usage ->
        val definitionFiles = mutableListOf<String>()
        val valueIt = index[JavaCompilerIndices.BACK_CLASS_DEF].getData(usage).valueIterator
        while (valueIt.hasNext()) {
          valueIt.next()
          val files = valueIt.inputIdsIterator
          while (files.hasNext()) {
            definitionFiles.add(files.next().asFileName(fileEnumerator))
          }
        }
        if (!definitionFiles.isEmpty()) {
          definitionFiles.sort()
          classDefs.add(usage.asText(nameEnumerator) + " in " + definitionFiles.joinToString(separator = " "))
        }
        true
      }
      classDefs.sort()
      result.append(classDefs.joinToString(separator = "\n"))

      result.append("\n\nMembers Signatures:\n")
      val signs = mutableListOf<String>()
      storage(index, JavaCompilerIndices.BACK_MEMBER_SIGN).processKeys { sign ->
        val definedMembers = mutableListOf<String>()
        val valueIt = index[JavaCompilerIndices.BACK_MEMBER_SIGN].getData(sign).valueIterator
        while (valueIt.hasNext()) {
          val nextRefs = valueIt.next()
          nextRefs.mapTo(definedMembers) { it.asText(nameEnumerator) }
        }
        if (!definedMembers.isEmpty()) {
          definedMembers.sort()
          signs.add(sign.asText(nameEnumerator) + " <- " + definedMembers.joinToString(separator = " "))
        }
        true
      }
      signs.sort()
      result.append(signs.joinToString(separator = "\n"))

      val typeCasts = mutableListOf<String>()
      storage(index, JavaCompilerIndices.BACK_CAST).processKeys { castType ->
        val operands = mutableListOf<String>()
        val valueIt = index[JavaCompilerIndices.BACK_CAST].getData(castType).valueIterator
        while (valueIt.hasNext()) {
          val nextRefs = valueIt.next()
          nextRefs.mapTo(operands) { it.asText(nameEnumerator) }
        }
        if (!operands.isEmpty()) {
          typeCasts.add(castType.asText(nameEnumerator) + " -> " + operands.joinToString(separator = " "))
        }
        true
      }
      if (typeCasts.isNotEmpty()) {
        result.append("\n\nType Casts:\n")
        typeCasts.sort()
        result.append(typeCasts.joinToString(separator = "\n"))
      }

      val implicitToString = mutableListOf<String>()
      storage(index, JavaCompilerIndices.IMPLICIT_TO_STRING).processKeys {type ->
        val callPlaceFiles = mutableListOf<String>()
        val valueIt = index[JavaCompilerIndices.IMPLICIT_TO_STRING].getData(type).valueIterator
        while (valueIt.hasNext()) {
          valueIt.next()
          val files = valueIt.inputIdsIterator
          while (files.hasNext()) {
            callPlaceFiles.add(files.next().asFileName(fileEnumerator))
          }
        }
        if (!callPlaceFiles.isEmpty()) {
          callPlaceFiles.sort()
          implicitToString.add(type.asText(nameEnumerator) + " in " + callPlaceFiles.joinToString(separator = " "))
        }
        true
      }
      if (implicitToString.isNotEmpty()) {
        result.append("\n\nImplicit toString():\n")
        implicitToString.sort()
        result.append(implicitToString.joinToString(separator = "\n"))
      }

      return result.toString()
    }
    finally {
      index.close()
      pd.release()
    }
  }

  private fun <K, V> storage(index: JavaCompilerBackwardReferenceIndex, id: IndexId<K, V>) = (index[id] as MapReduceIndex<K, V, CompiledFileData>).storage as MapIndexStorage<K, V>

  private fun getTestDataPath() = testDataRootPath + "/" + getTestName(true) + "/"

  private fun Int.asName(nameEnumerator: NameEnumerator): String = nameEnumerator.getName(this)

  private fun CompilerRef.asText(nameEnumerator: NameEnumerator): String =
      when (this) {
        is CompilerRef.JavaCompilerMethodRef -> "${this.owner.name.asName(nameEnumerator)}.${this.name.asName(nameEnumerator)}(${this.parameterCount})"
        is CompilerRef.JavaCompilerFieldRef -> "${this.owner.name.asName(nameEnumerator)}.${this.name.asName(nameEnumerator)}"
        is CompilerRef.JavaCompilerClassRef -> this.name.asName(nameEnumerator)
        is CompilerRef.JavaCompilerFunExprDef -> "fun_expr(id=${this.id})"
        is CompilerRef.JavaCompilerAnonymousClassRef -> "anonymous(id=${this.name})"
        else -> throw UnsupportedOperationException()
      }

  private fun SignatureData.asText(nameEnumerator: NameEnumerator): String {
    return (if (this.isStatic) "static " else "") + this.rawReturnType.asName(nameEnumerator) + decodeVectorKind(this.iteratorKind)
  }

  private fun decodeVectorKind(kind: Byte): String {
    when (kind) {
      0.toByte() -> return ""
      1.toByte() -> return "[]"
      (-1).toByte() -> return " iterator"
    }
    throw IllegalArgumentException()
  }

  private fun Int.asFileName(fileNameEnumerator: PersistentStringEnumerator) = FileUtil.getNameWithoutExtension(File(fileNameEnumerator.valueOf(this)).canonicalFile)
}
