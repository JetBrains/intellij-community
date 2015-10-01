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
import com.intellij.openapi.options.SchemesManagerFactory
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.toByteArray
import gnu.trove.THashMap
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

val FILE_SPEC = "REMOTE"

/**
 * Functionality without stream provider covered, ICS has own test suite
 */
class SchemeManagerTest {
  companion object {
    ClassRule val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()
  public Rule fun getTemporaryFolder(): TemporaryDirectory = tempDirManager

  private var localBaseDir: File? = null
  private var remoteBaseDir: File? = null

  private fun getTestDataPath() = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/platform/platform-tests/testData/options"

  public Test fun testLoadSchemes() {
    doLoadSaveTest("options1", "1->first;2->second")
  }

  public Test fun testLoadSimpleSchemes() {
    doLoadSaveTest("options", "1->1")
  }

  public Test fun testDeleteScheme() {
    val manager = createAndLoad("options1")
    manager.removeScheme(TestScheme("first"))
    manager.save()

    checkSchemes("2->second")
  }

  public Test fun testRenameScheme() {
    val manager = createAndLoad("options1")

    val scheme = manager.findSchemeByName("first")
    assertThat(scheme).isNotNull()
    scheme!!.setName("renamed")
    manager.save()

    checkSchemes("2->second;renamed->renamed")
  }

  public Test fun testRenameScheme2() {
    val manager = createAndLoad("options1")

    val first = manager.findSchemeByName("first")
    assertThat(first).isNotNull()
    assert(first != null)
    first!!.setName("2")
    val second = manager.findSchemeByName("second")
    assertThat(second).isNotNull()
    assert(second != null)
    second!!.setName("1")
    manager.save()

    checkSchemes("1->1;2->2")
  }

  public Test fun testDeleteRenamedScheme() {
    val manager = createAndLoad("options1")

    val firstScheme = manager.findSchemeByName("first")
    assertThat(firstScheme).isNotNull()
    assert(firstScheme != null)
    firstScheme!!.setName("first_renamed")
    manager.save()

    checkSchemes(File(remoteBaseDir, "REMOTE"), "first_renamed->first_renamed;2->second", true)
    checkSchemes(localBaseDir!!, "", false)

    firstScheme.setName("first_renamed2")
    manager.removeScheme(firstScheme)
    manager.save()

    checkSchemes(File(remoteBaseDir, "REMOTE"), "2->second", true)
    checkSchemes(localBaseDir!!, "", false)
  }

  public Test fun testDeleteAndCreateSchemeWithTheSameName() {
    val manager = createAndLoad("options1")
    val firstScheme = manager.findSchemeByName("first")
    assertThat(firstScheme).isNotNull()

    manager.removeScheme(firstScheme!!)
    manager.addScheme(TestScheme("first"))
    manager.save()
    checkSchemes("2->second;first->first")
  }

  public Test fun testGenerateUniqueSchemeName() {
    val manager = createAndLoad("options1")
    val scheme = TestScheme("first")
    manager.addNewScheme(scheme, false)

    assertThat("first2").isEqualTo(scheme.getName())
  }

  fun TestScheme.save(file: File) {
    FileUtil.writeToFile(file, _serialize().toByteArray())
  }

  public Test fun `different extensions`() {
    val dir = tempDirManager.newDirectory()

    val scheme = TestScheme("local", "true")
    scheme.save(File(dir, "1.icls"))
    TestScheme("local", "false").save(File(dir, "1.xml"))

    val schemesManager = SchemeManagerImpl<TestScheme, TestScheme>(FILE_SPEC, object: TestSchemesProcessor() {
      override fun isUpgradeNeeded() = true

      override fun getSchemeExtension() = ".icls"
    }, null, dir)
    schemesManager.loadSchemes()
    assertThat(schemesManager.getAllSchemes()).containsOnly(scheme)

    assertThat(File(dir, "1.icls")).isFile()
    assertThat(File(dir, "1.xml")).isFile()

    scheme.data = "newTrue"
    schemesManager.save()

    assertThat(File(dir, "1.icls")).isFile()
    assertThat(File(dir, "1.xml")).doesNotExist()
  }

  public Test fun setSchemes() {
    val dir = tempDirManager.newDirectory()
    val schemeManager = createSchemeManager(dir)
    schemeManager.loadSchemes()
    assertThat(schemeManager.getAllSchemes()).isEmpty()

    val scheme = TestScheme("s1")
    schemeManager.setSchemes(listOf(scheme))

    val schemes = schemeManager.getAllSchemes()
    assertThat(schemes).containsOnly(scheme)

    assertThat(File(dir, "s1.xml")).doesNotExist()

    scheme.data = "newTrue"
    schemeManager.save()

    assertThat(File(dir, "s1.xml")).isFile()

    schemeManager.setSchemes(emptyList())

    schemeManager.save()

    assertThat(dir).doesNotExist()
  }

  public Test fun `save only if scheme differs from bundled`() {
    val dir = tempDirManager.newDirectory()
    var schemeManager = createSchemeManager(dir)
    val converter: (Element) -> TestScheme = { XmlSerializer.deserialize(it, javaClass<TestScheme>())!! }
    val bundledPath = "/bundledSchemes/default"
    schemeManager.loadBundledScheme(bundledPath, this, converter)
    var schemes = schemeManager.getAllSchemes()
    val customScheme = TestScheme("default")
    assertThat(schemes).containsOnly(customScheme)

    schemeManager.save()
    assertThat(dir).doesNotExist()

    schemeManager.save()
    schemeManager.setSchemes(listOf(customScheme))
    assertThat(dir).doesNotExist()

    schemes = schemeManager.getAllSchemes()
    assertThat(schemes).containsOnly(customScheme)

    customScheme.data = "foo"
    schemeManager.save()
    assertThat(File(dir, "default.xml")).isFile()

    schemeManager = createSchemeManager(dir)
    schemeManager.loadBundledScheme(bundledPath, this, converter)
    schemeManager.loadSchemes()

    schemes = schemeManager.getAllSchemes()
    assertThat(schemes).containsOnly(customScheme)
  }

  public Test fun `don't remove dir if no schemes but at least one non-hidden file exists`() {
    val dir = tempDirManager.newDirectory()
    val schemeManager = createSchemeManager(dir)

    val scheme = TestScheme("s1")
    schemeManager.setSchemes(listOf(scheme))

    schemeManager.save()

    val schemeFile = File(dir, "s1.xml")
    assertThat(schemeFile).isFile()

    schemeManager.setSchemes(emptyList())

    FileUtil.writeToFile(File(dir, "empty"), byteArrayOf())

    schemeManager.save()

    assertThat(schemeFile).doesNotExist()
    assertThat(dir).isDirectory()
  }

  public Test fun `remove empty directory only if some file was deleted`() {
    val dir = tempDirManager.newDirectory()
    val schemeManager = createSchemeManager(dir)
    schemeManager.loadSchemes()

    assertThat(dir.mkdirs()).isTrue()
    schemeManager.save()
    assertThat(dir).isDirectory()

    schemeManager.addScheme(TestScheme("test"))
    schemeManager.save()
    assertThat(dir).isDirectory()

    schemeManager.setSchemes(emptyList())
    schemeManager.save()
    assertThat(dir).doesNotExist()
  }

  public Test fun rename() {
    val dir = tempDirManager.newDirectory()
    val schemeManager = createSchemeManager(dir)
    schemeManager.loadSchemes()
    assertThat(schemeManager.getAllSchemes()).isEmpty()

    val scheme = TestScheme("s1")
    schemeManager.setSchemes(listOf(scheme))

    val schemes = schemeManager.getAllSchemes()
    assertThat(schemes).containsOnly(scheme)

    assertThat(File(dir, "s1.xml")).doesNotExist()

    scheme.data = "newTrue"
    schemeManager.save()

    assertThat(File(dir, "s1.xml")).isFile()

    scheme.setName("s2")

    schemeManager.save()

    assertThat(File(dir, "s1.xml")).doesNotExist()
    assertThat(File(dir, "s2.xml")).isFile()
  }

  public Test fun `path must not contains ROOT_CONFIG macro`() {
    assertThatThrownBy({ SchemesManagerFactory.getInstance().create<TestScheme, TestScheme>("\$ROOT_CONFIG$/foo", TestSchemesProcessor()) }).hasMessage("Path must not contains ROOT_CONFIG macro, corrected: foo")
  }

  public Test fun `path must be system-independent`() {
    assertThatThrownBy({SchemesManagerFactory.getInstance().create<TestScheme, TestScheme>("foo\\bar", TestSchemesProcessor())}).hasMessage("Path must be system-independent, use forward slash instead of backslash")
  }

  private fun createSchemeManager(dir: File) = SchemeManagerImpl<TestScheme, TestScheme>(FILE_SPEC, TestSchemesProcessor(), null, dir)

  private fun createAndLoad(testData: String): SchemeManagerImpl<TestScheme, TestScheme> {
    createTempFiles(testData)
    return createAndLoad()
  }

  private fun doLoadSaveTest(testData: String, expected: String, localExpected: String = "") {
    val schemesManager = createAndLoad(testData)
    schemesManager.save()
    checkSchemes(File(remoteBaseDir, "REMOTE"), expected, true)
    checkSchemes(localBaseDir!!, localExpected, false)
  }

  private fun checkSchemes(expected: String) {
    checkSchemes(File(remoteBaseDir, "REMOTE"), expected, true)
    checkSchemes(localBaseDir!!, "", false)
  }

  private fun createAndLoad(): SchemeManagerImpl<TestScheme, TestScheme> {
    val schemesManager = SchemeManagerImpl<TestScheme, TestScheme>(FILE_SPEC, TestSchemesProcessor(), MockStreamProvider(remoteBaseDir!!), localBaseDir!!)
    schemesManager.loadSchemes()
    return schemesManager
  }

  private fun createTempFiles(testData: String) {
    val temp = tempDirManager.newDirectory()
    localBaseDir = File(temp, "__local")
    remoteBaseDir = temp
    FileUtil.copyDir(File("${getTestDataPath()}/$testData"), File(temp, "REMOTE"))
  }
}

private fun checkSchemes(baseDir: File, expected: String, ignoreDeleted: Boolean) {
  val filesToScheme = StringUtil.split(expected, ";")
  val fileToSchemeMap = THashMap<String, String>()
  for (fileToScheme in filesToScheme) {
    val index = fileToScheme.indexOf("->")
    fileToSchemeMap.put(fileToScheme.substring(0, index), fileToScheme.substring(index + 2))
  }

  val files = baseDir.listFiles()
  if (files != null) {
    for (file in files) {
      val fileName = FileUtil.getNameWithoutExtension(file)
      if ("--deleted" == fileName && ignoreDeleted) {
        assertThat(fileToSchemeMap).containsKey(fileName)
      }
    }
  }

  for (file in fileToSchemeMap.keySet()) {
    assertThat(File(baseDir, "$file.xml")).isFile()
  }

  if (files != null) {
    val schemesProcessor = TestSchemesProcessor()
    for (file in files) {
      val fileName = FileUtil.getNameWithoutExtension(file)
      val scheme = schemesProcessor.readScheme(JDOMUtil.load(file), true)
      assertThat(fileToSchemeMap.get(fileName)).isEqualTo(scheme!!.getName())
    }
  }
}

Tag("scheme")
public data class TestScheme(@Attribute private var name: String = "", @Attribute public var data: String? = null) : ExternalizableScheme {
  override fun getName() = name

  override Transient fun setName(newName: String) {
    name = newName
  }

  @suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
  override fun getExternalInfo() = null
}

public open class TestSchemesProcessor : BaseSchemeProcessor<TestScheme>() {
  override fun readScheme(element: Element) = XmlSerializer.deserialize(element, javaClass<TestScheme>())

  override fun writeScheme(scheme: TestScheme) = scheme._serialize()
}

fun SchemeManagerImpl<*, *>.save() {
  val errors = SmartList<Throwable>()
  save(errors)
  CompoundRuntimeException.doThrow(errors)
}

public fun <T : Any> T._serialize(filter: SerializationFilter? = SkipDefaultValuesSerializationFilters()): Element = XmlSerializer.serialize(this, filter)