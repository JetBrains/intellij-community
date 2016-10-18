/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.io.PersistentStringEnumerator
import com.sun.tools.javac.util.Convert
import org.jetbrains.jps.backwardRefs.BackwardReferenceIndexWriter
import org.jetbrains.jps.backwardRefs.ByteArrayEnumerator
import org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex
import org.jetbrains.jps.backwardRefs.LightRef
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.builders.TestProjectBuilderLogger
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import java.io.File

abstract class ReferenceIndexTestBase : JpsBuildTestCase() {
  public override fun setUp() {
    BackwardReferenceIndexWriter.forceEnabled = true
    super.setUp()
//    System.setProperty(BackwardReferenceIndexWriter.PROP_KEY, true.toString())
  }

  public override fun tearDown() {
    super.tearDown()
    BackwardReferenceIndexWriter.forceEnabled = false
    BackwardReferenceIndexWriter.clearInstance()
//    System.clearProperty(BackwardReferenceIndexWriter.PROP_KEY)
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
    val index = CompilerBackwardReferenceIndex(buildDir)

    try {
      val fileEnumerator = index.filePathEnumerator
      val nameEnumerator = index.byteSeqEum

      val result = StringBuilder()
      result.append("Backward Hierarchy:\n")
      val hierarchyText = mutableListOf<String>()
      index.backwardHierarchyMap.forEachEntry { superClass, inheritors ->
        val superClassName = superClass.asText(nameEnumerator)
        val inheritorsText = mutableListOf<String>()
        inheritors.forEach { id ->
          inheritorsText.add(id.asText(nameEnumerator))
        }
        inheritorsText.sort()
        hierarchyText.add(superClassName + " -> " + inheritorsText.joinToString(separator = " "))
        true
      }
      hierarchyText.sort()
      result.append(hierarchyText.joinToString(separator = "\n"))

      result.append("\n\nBackward References:\n")
      val referencesText = mutableListOf<String>()
      index.backwardReferenceMap.forEachEntry { usage, files ->
        val referents = mutableListOf<String>()
        files.forEach { id ->
          referents.add(id.asFileName(fileEnumerator))
        }
        referents.sort()
        referencesText.add(usage.asText(nameEnumerator) + " in " + referents.joinToString(separator = " "))
        true
      }
      referencesText.sort()
      result.append(referencesText.joinToString(separator = "\n"))

      result.append("\n\nClass Definitions:\n")
      val classDefs = mutableListOf<String>()
      index.backwardClassDefinitionMap.forEachEntry { usage, files ->
        val definitionFiles = mutableListOf<String>()
        files.forEach { id ->
          definitionFiles.add(id.asFileName(fileEnumerator))
        }
        definitionFiles.sort()
        classDefs.add(usage.asText(nameEnumerator) + " in " + definitionFiles.joinToString(separator = " "))
        true
      }
      classDefs.sort()
      result.append(classDefs.joinToString(separator = "\n"))

      return result.toString()
    } finally {
      index.close()
    }
  }

  private fun getTestDataPath() = testDataRootPath + "/" + getTestName(true) + "/"

  private fun Int.asName(byteArrayEnumerator: ByteArrayEnumerator): String = Convert.utf2string(byteArrayEnumerator.valueOf(this))

  private fun CompilerBackwardReferenceIndex.LightDefinition.asText(byteArrayEnumerator: ByteArrayEnumerator) = this.ref.asText(byteArrayEnumerator)

  private fun LightRef.asText(byteArrayEnumerator: ByteArrayEnumerator): String =
      when (this) {
        is LightRef.JavaLightMethodRef -> "${this.owner.name.asName(byteArrayEnumerator)}.${this.name.asName(byteArrayEnumerator)}(${this.parameterCount})"
        is LightRef.JavaLightFieldRef -> "${this.owner.name.asName(byteArrayEnumerator)}.${this.name.asName(byteArrayEnumerator)}"
        is LightRef.JavaLightClassRef -> this.name.asName(byteArrayEnumerator)
        is LightRef.JavaLightFunExprDef -> "fun_expr(id=${this.id})"
        else -> throw UnsupportedOperationException()
      }

  private fun Int.asFileName(fileNameEnumerator: PersistentStringEnumerator) = FileUtil.getNameWithoutExtension(File(fileNameEnumerator.valueOf(this)).canonicalFile)
}