/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.configurationStore

import com.intellij.openapi.options.BaseSchemeProcessor
import com.intellij.openapi.options.ExternalizableScheme
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.*
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.serialize
import com.intellij.util.xmlb.toByteArray
import gnu.trove.THashMap
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Path

internal val FILE_SPEC = "REMOTE"

/**
 * Functionality without stream provider covered, ICS has own test suite
 */
internal class SchemeManagerTest {
  companion object {
    @JvmField
    @ClassRule val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()
  @Rule fun getTemporaryFolder() = tempDirManager

  private var localBaseDir: Path? = null
  private var remoteBaseDir: Path? = null

  private fun getTestDataPath() = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/platform/platform-tests/testData/options"

  @Test fun loadSchemes() {
    doLoadSaveTest("options1", "1->first;2->second")
  }

  @Test fun loadSimpleSchemes() {
    doLoadSaveTest("options", "1->1")
  }

  @Test fun deleteScheme() {
    val manager = createAndLoad("options1")
    manager.removeScheme("first")
    manager.save()

    checkSchemes("2->second")
  }

  @Test fun renameScheme() {
    val manager = createAndLoad("options1")

    val scheme = manager.findSchemeByName("first")
    assertThat(scheme).isNotNull()
    scheme!!.name = "renamed"
    manager.save()

    checkSchemes("2->second;renamed->renamed")
  }

  @Test fun testRenameScheme2() {
    val manager = createAndLoad("options1")

    val first = manager.findSchemeByName("first")
    assertThat(first).isNotNull()
    assert(first != null)
    first!!.name = "2"
    val second = manager.findSchemeByName("second")
    assertThat(second).isNotNull()
    assert(second != null)
    second!!.name = "1"
    manager.save()

    checkSchemes("1->1;2->2")
  }

  @Test fun testDeleteRenamedScheme() {
    val manager = createAndLoad("options1")

    val firstScheme = manager.findSchemeByName("first")
    assertThat(firstScheme).isNotNull()
    assert(firstScheme != null)
    firstScheme!!.name = "first_renamed"
    manager.save()

    checkSchemes(remoteBaseDir!!.resolve("REMOTE"), "first_renamed->first_renamed;2->second", true)
    checkSchemes(localBaseDir!!, "", false)

    firstScheme.name = "first_renamed2"
    manager.removeScheme(firstScheme)
    manager.save()

    checkSchemes(remoteBaseDir!!.resolve("REMOTE"), "2->second", true)
    checkSchemes(localBaseDir!!, "", false)
  }

  @Test fun testDeleteAndCreateSchemeWithTheSameName() {
    val manager = createAndLoad("options1")
    val firstScheme = manager.findSchemeByName("first")
    assertThat(firstScheme).isNotNull()

    manager.removeScheme(firstScheme!!)
    manager.addScheme(TestScheme("first"))
    manager.save()
    checkSchemes("2->second;first->first")
  }

  @Test fun testGenerateUniqueSchemeName() {
    val manager = createAndLoad("options1")
    val scheme = TestScheme("first")
    manager.addNewScheme(scheme, false)

    assertThat("first2").isEqualTo(scheme.name)
  }

  fun TestScheme.save(file: Path) {
    file.write(serialize().toByteArray())
  }

  @Test fun `different extensions`() {
    val dir = tempDirManager.newPath()

    val scheme = TestScheme("local", "true")
    scheme.save(dir.resolve("1.icls"))
    TestScheme("local", "false").save(dir.resolve("1.xml"))

    val schemesManager = SchemeManagerImpl(FILE_SPEC, object: TestSchemesProcessor() {
      override val isUpgradeNeeded = true

      override val schemeExtension = ".icls"
    }, null, dir)
    schemesManager.loadSchemes()
    assertThat(schemesManager.allSchemes).containsOnly(scheme)

    assertThat(dir.resolve("1.icls")).isRegularFile()
    assertThat(dir.resolve("1.xml")).isRegularFile()

    scheme.data = "newTrue"
    schemesManager.save()

    assertThat(dir.resolve("1.icls")).isRegularFile()
    assertThat(dir.resolve("1.xml")).doesNotExist()
  }

  @Test fun setSchemes() {
    val dir = tempDirManager.newPath()
    val schemeManager = createSchemeManager(dir)
    schemeManager.loadSchemes()
    assertThat(schemeManager.allSchemes).isEmpty()

    val scheme = TestScheme("s1")
    schemeManager.setSchemes(listOf(scheme))

    val schemes = schemeManager.allSchemes
    assertThat(schemes).containsOnly(scheme)

    assertThat(dir.resolve("s1.xml")).doesNotExist()

    scheme.data = "newTrue"
    schemeManager.save()

    assertThat(dir.resolve("s1.xml")).isRegularFile()

    schemeManager.setSchemes(emptyList())

    schemeManager.save()

    assertThat(dir).doesNotExist()
  }

  @Test fun `reload schemes`() {
    val dir = tempDirManager.newPath()
    val schemeManager = createSchemeManager(dir)
    schemeManager.loadSchemes()
    assertThat(schemeManager.allSchemes).isEmpty()

    val scheme = TestScheme("s1", "oldData")
    schemeManager.setSchemes(listOf(scheme))
    assertThat(schemeManager.allSchemes).containsOnly(scheme)
    schemeManager.save()

    dir.resolve("s1.xml").write("""<scheme name="s1" data="newData" />""")
    schemeManager.reload()

    assertThat(schemeManager.allSchemes).containsOnly(TestScheme("s1", "newData"))
  }

  @Test fun `save only if scheme differs from bundled`() {
    val dir = tempDirManager.newPath()
    var schemeManager = createSchemeManager(dir)
    val converter: (Element) -> TestScheme = { XmlSerializer.deserialize(it, TestScheme::class.java)!! }
    val bundledPath = "/bundledSchemes/default"
    schemeManager.loadBundledScheme(bundledPath, this, converter)
    val customScheme = TestScheme("default")
    assertThat(schemeManager.allSchemes).containsOnly(customScheme)

    schemeManager.save()
    assertThat(dir).doesNotExist()

    schemeManager.save()
    schemeManager.setSchemes(listOf(customScheme))
    assertThat(dir).doesNotExist()

    assertThat(schemeManager.allSchemes).containsOnly(customScheme)

    customScheme.data = "foo"
    schemeManager.save()
    assertThat(dir.resolve("default.xml")).isRegularFile()

    schemeManager = createSchemeManager(dir)
    schemeManager.loadBundledScheme(bundledPath, this, converter)
    schemeManager.loadSchemes()

    assertThat(schemeManager.allSchemes).containsOnly(customScheme)
  }

  @Test fun `don't remove dir if no schemes but at least one non-hidden file exists`() {
    val dir = tempDirManager.newPath()
    val schemeManager = createSchemeManager(dir)

    val scheme = TestScheme("s1")
    schemeManager.setSchemes(listOf(scheme))

    schemeManager.save()

    val schemeFile = dir.resolve("s1.xml")
    assertThat(schemeFile).isRegularFile()

    schemeManager.setSchemes(emptyList())

    dir.resolve("empty").write(byteArrayOf())

    schemeManager.save()

    assertThat(schemeFile).doesNotExist()
    assertThat(dir).isDirectory()
  }

  @Test fun `remove empty directory only if some file was deleted`() {
    val dir = tempDirManager.newPath()
    val schemeManager = createSchemeManager(dir)
    schemeManager.loadSchemes()

    dir.createDirectories()
    schemeManager.save()
    assertThat(dir).isDirectory()

    schemeManager.addScheme(TestScheme("test"))
    schemeManager.save()
    assertThat(dir).isDirectory()

    schemeManager.setSchemes(emptyList())
    schemeManager.save()
    assertThat(dir).doesNotExist()
  }

  @Test fun rename() {
    val dir = tempDirManager.newPath()
    val schemeManager = createSchemeManager(dir)
    schemeManager.loadSchemes()
    assertThat(schemeManager.allSchemes).isEmpty()

    val scheme = TestScheme("s1")
    schemeManager.setSchemes(listOf(scheme))

    val schemes = schemeManager.allSchemes
    assertThat(schemes).containsOnly(scheme)

    assertThat(dir.resolve("s1.xml")).doesNotExist()

    scheme.data = "newTrue"
    schemeManager.save()

    assertThat(dir.resolve("s1.xml")).isRegularFile()

    scheme.name = "s2"

    schemeManager.save()

    assertThat(dir.resolve("s1.xml")).doesNotExist()
    assertThat(dir.resolve("s2.xml")).isRegularFile()
  }

  @Test fun `path must not contains ROOT_CONFIG macro`() {
    assertThatThrownBy({ SchemeManagerFactory.getInstance().create("\$ROOT_CONFIG$/foo", TestSchemesProcessor()) }).hasMessage("Path must not contains ROOT_CONFIG macro, corrected: foo")
  }

  @Test fun `path must be system-independent`() {
    assertThatThrownBy({ SchemeManagerFactory.getInstance().create("foo\\bar", TestSchemesProcessor())}).hasMessage("Path must be system-independent, use forward slash instead of backslash")
  }

  private fun createSchemeManager(dir: Path) = SchemeManagerImpl(FILE_SPEC, TestSchemesProcessor(), null, dir)

  private fun createAndLoad(testData: String): SchemeManagerImpl<TestScheme, TestScheme> {
    createTempFiles(testData)
    return createAndLoad()
  }

  private fun doLoadSaveTest(testData: String, expected: String, localExpected: String = "") {
    val schemesManager = createAndLoad(testData)
    schemesManager.save()
    checkSchemes(remoteBaseDir!!.resolve("REMOTE"), expected, true)
    checkSchemes(localBaseDir!!, localExpected, false)
  }

  private fun checkSchemes(expected: String) {
    checkSchemes(remoteBaseDir!!.resolve("REMOTE"), expected, true)
    checkSchemes(localBaseDir!!, "", false)
  }

  private fun createAndLoad(): SchemeManagerImpl<TestScheme, TestScheme> {
    val schemesManager = SchemeManagerImpl(FILE_SPEC, TestSchemesProcessor(), MockStreamProvider(remoteBaseDir!!), localBaseDir!!)
    schemesManager.loadSchemes()
    return schemesManager
  }

  private fun createTempFiles(testData: String) {
    val temp = tempDirManager.newPath()
    localBaseDir = temp.resolve("__local")
    remoteBaseDir = temp
    FileUtil.copyDir(File("${getTestDataPath()}/$testData"), temp.resolve("REMOTE").toFile())
  }
}

private fun checkSchemes(baseDir: Path, expected: String, ignoreDeleted: Boolean) {
  val filesToScheme = StringUtil.split(expected, ";")
  val fileToSchemeMap = THashMap<String, String>()
  for (fileToScheme in filesToScheme) {
    val index = fileToScheme.indexOf("->")
    fileToSchemeMap.put(fileToScheme.substring(0, index), fileToScheme.substring(index + 2))
  }

  baseDir.directoryStreamIfExists {
    for (file in it) {
      val fileName = FileUtil.getNameWithoutExtension(file.fileName.toString())
      if ("--deleted" == fileName && ignoreDeleted) {
        assertThat(fileToSchemeMap).containsKey(fileName)
      }
    }
  }

  for (file in fileToSchemeMap.keys) {
    assertThat(baseDir.resolve("$file.xml")).isRegularFile()
  }

  baseDir.directoryStreamIfExists {
    val schemesProcessor = TestSchemesProcessor()
    for (file in it) {
      val scheme = schemesProcessor.readScheme(loadElement(file), true)!!
      assertThat(fileToSchemeMap.get(FileUtil.getNameWithoutExtension(file.fileName.toString()))).isEqualTo(scheme.name)
    }
  }
}

@Tag("scheme")
data class TestScheme(@field:com.intellij.util.xmlb.annotations.Attribute @field:kotlin.jvm.JvmField var name: String = "", @field:com.intellij.util.xmlb.annotations.Attribute var data: String? = null) : ExternalizableScheme {
  override fun getName() = name

  override fun setName(value: String) {
    name = value
  }
}

open class TestSchemesProcessor : BaseSchemeProcessor<TestScheme, TestScheme>() {
  override fun readScheme(element: Element, duringLoad: Boolean) = XmlSerializer.deserialize(element, TestScheme::class.java)

  override fun writeScheme(scheme: TestScheme) = scheme.serialize()
}

fun SchemeManagerImpl<*, *>.save() {
  val errors = SmartList<Throwable>()
  save(errors)
  CompoundRuntimeException.throwIfNotEmpty(errors)
}