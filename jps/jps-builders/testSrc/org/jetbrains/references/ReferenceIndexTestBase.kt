/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.references

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.PathUtil
import com.intellij.util.indexing.IndexId
import com.intellij.util.indexing.impl.MapIndexStorage
import com.intellij.util.indexing.impl.MapReduceIndex
import com.intellij.util.io.PersistentStringEnumerator
import org.jetbrains.backwardRefs.CompilerBackwardReferenceIndex
import org.jetbrains.backwardRefs.LightRef
import org.jetbrains.backwardRefs.NameEnumerator
import org.jetbrains.backwardRefs.SignatureData
import org.jetbrains.jps.backwardRefs.*
import org.jetbrains.backwardRefs.index.CompiledFileData
import org.jetbrains.backwardRefs.index.CompilerIndices
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.builders.TestProjectBuilderLogger
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import java.io.File
import java.io.IOException

abstract class ReferenceIndexTestBase : JpsBuildTestCase() {
  public override fun setUp() {
    super.setUp()
    System.setProperty(JpsJavacReferenceIndexWriterHolder.PROP_KEY, true.toString())
  }

  public override fun tearDown() {
    super.tearDown()
    System.clearProperty(JpsJavacReferenceIndexWriterHolder.PROP_KEY)
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
    val index = object: CompilerBackwardReferenceIndex(buildDir, true) {
      override fun createBuildDataCorruptedException(cause: IOException?): RuntimeException = BuildDataCorruptedException(cause)
    }

    try {
      val fileEnumerator = index.filePathEnumerator
      val nameEnumerator = index.byteSeqEum

      val result = StringBuilder()
      result.append("Backward Hierarchy:\n")
      val hierarchyText = mutableListOf<String>()
      storage(index, CompilerIndices.BACK_HIERARCHY).processKeys { superClass ->
        val superClassName = superClass.asText(nameEnumerator)
        val inheritorsText = mutableListOf<String>()
        index[CompilerIndices.BACK_HIERARCHY].getData(superClass).forEach { i, children ->
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
      storage(index, CompilerIndices.BACK_USAGES).processKeys { usage ->
        val referents = mutableListOf<String>()
        val valueIt = index[CompilerIndices.BACK_USAGES].getData(usage)

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
      storage(index, CompilerIndices.BACK_CLASS_DEF).processKeys { usage ->
        val definitionFiles = mutableListOf<String>()
        val valueIt = index[CompilerIndices.BACK_CLASS_DEF].getData(usage).valueIterator
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
      storage(index, CompilerIndices.BACK_MEMBER_SIGN).processKeys { sign ->
        val definedMembers = mutableListOf<String>()
        val valueIt = index[CompilerIndices.BACK_MEMBER_SIGN].getData(sign).valueIterator
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

      return result.toString()
    }
    finally {
      index.close()
      pd.release()
    }
  }

  private fun <K, V> storage(index: CompilerBackwardReferenceIndex, id: IndexId<K, V>) = (index[id] as MapReduceIndex<K, V, CompiledFileData>).storage as MapIndexStorage<K, V>

  private fun getTestDataPath() = testDataRootPath + "/" + getTestName(true) + "/"

  private fun Int.asName(nameEnumerator: NameEnumerator): String = nameEnumerator.getName(this)

  private fun LightRef.asText(nameEnumerator: NameEnumerator): String =
      when (this) {
        is LightRef.JavaLightMethodRef -> "${this.owner.name.asName(nameEnumerator)}.${this.name.asName(nameEnumerator)}(${this.parameterCount})"
        is LightRef.JavaLightFieldRef -> "${this.owner.name.asName(nameEnumerator)}.${this.name.asName(nameEnumerator)}"
        is LightRef.JavaLightClassRef -> this.name.asName(nameEnumerator)
        is LightRef.JavaLightFunExprDef -> "fun_expr(id=${this.id})"
        is LightRef.JavaLightAnonymousClassRef -> "anonymous(id=${this.name})"
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