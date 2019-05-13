// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.SchemeFileTracker
import com.intellij.configurationStore.schemeManager.SchemeManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.options.ExternalizableScheme
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.*
import com.intellij.util.loadElement
import com.intellij.util.toByteArray
import com.intellij.util.xmlb.annotations.Tag
import gnu.trove.THashMap
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.function.Function

internal const val FILE_SPEC = "REMOTE"

/**
 * Functionality without stream provider covered, ICS has own test suite
 */
internal class SchemeManagerTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Rule
  @JvmField
  val tempDirManager = TemporaryDirectory()

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
    assertThat(scheme).isNotNull
    @Suppress("SpellCheckingInspection")
    scheme!!.name = "Grünwald"
    manager.save()

    @Suppress("SpellCheckingInspection")
    checkSchemes("2->second;Grünwald->Grünwald")
  }

  @Test fun testRenameScheme2() {
    val manager = createAndLoad("options1")

    val first = manager.findSchemeByName("first")
    assertThat(first).isNotNull
    assert(first != null)
    first!!.name = "2"
    val second = manager.findSchemeByName("second")
    assertThat(second).isNotNull
    assert(second != null)
    second!!.name = "1"
    manager.save()

    checkSchemes("1->1;2->2")
  }

  @Test fun testDeleteRenamedScheme() {
    val manager = createAndLoad("options1")

    val firstScheme = manager.findSchemeByName("first")
    assertThat(firstScheme).isNotNull
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
    assertThat(firstScheme).isNotNull

    manager.removeScheme(firstScheme!!)
    manager.addScheme(TestScheme("first"))
    manager.save()
    checkSchemes("2->second;first->first")
  }

  @Test fun testGenerateUniqueSchemeName() {
    val manager = createAndLoad("options1")
    val scheme = TestScheme("first")
    manager.addScheme(scheme, false)

    assertThat("first2").isEqualTo(scheme.name)
  }

  fun TestScheme.save(file: Path) {
    file.write(serialize()!!.toByteArray())
  }

  @Test fun `different extensions - old, new`() {
    doDifferentExtensionTest(listOf("1.xml", "1.icls"))
  }

  @Test fun `different extensions - new, old`() {
    doDifferentExtensionTest(listOf("1.icls", "1.xml"))
  }

  private fun doDifferentExtensionTest(fileNames: List<String>) {
    val dir = tempDirManager.newPath()

    val scheme = TestScheme("local", "true")
    scheme.save(dir.resolve("1.icls"))
    TestScheme("local", "false").save(dir.resolve("1.xml"))

    class ATestSchemesProcessor : TestSchemesProcessor(), SchemeExtensionProvider {
      override val schemeExtension = ".icls"
    }

    // use provider to specify exact order of files (it is critical to test both variants - old, new or new, old)
    val schemeManager = SchemeManagerImpl(FILE_SPEC, ATestSchemesProcessor(), object : StreamProvider {
      override val isExclusive = true

      override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
        getFile(fileSpec).write(content, 0, size)
      }

      override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
        getFile(fileSpec).inputStream().use(consumer)
        return true
      }

      override fun processChildren(path: String,
                                   roamingType: RoamingType,
                                   filter: (name: String) -> Boolean,
                                   processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean {
        for (name in fileNames) {
          dir.resolve(name).inputStream().use {
            processor(name, it, false)
          }
        }
        return true
      }

      override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
        getFile(fileSpec).delete()
        return true
      }

      private fun getFile(fileSpec: String) = dir.resolve(fileSpec.substring(FILE_SPEC.length + 1))
    }, dir)
    schemeManager.loadSchemes()
    assertThat(schemeManager.allSchemes).containsOnly(scheme)

    assertThat(dir.resolve("1.icls")).isRegularFile()
    assertThat(dir.resolve("1.xml")).isRegularFile()

    scheme.data = "newTrue"
    schemeManager.save()

    assertThat(dir.resolve("1.icls")).isRegularFile()
    assertThat(dir.resolve("1.xml")).doesNotExist()
  }

  @Test fun setSchemes() {
    val dir = tempDirManager.newPath()
    val schemeManager = SchemeManagerImpl(FILE_SPEC, TestSchemesProcessor(), null, dir, schemeNameToFileName = MODERN_NAME_CONVERTER)
    schemeManager.loadSchemes()
    assertThat(schemeManager.allSchemes).isEmpty()

    @Suppress("SpellCheckingInspection")
    val schemeName = "Grünwald и русский"
    val scheme = TestScheme(schemeName)
    schemeManager.setSchemes(listOf(scheme))

    val schemes = schemeManager.allSchemes
    assertThat(schemes).containsOnly(scheme)

    assertThat(dir.resolve("$schemeName.xml")).doesNotExist()

    scheme.data = "newTrue"
    schemeManager.save()

    assertThat(dir.resolve("$schemeName.xml")).isRegularFile()

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
    val bundledPath = "/com/intellij/configurationStore/bundledSchemes/default"
    schemeManager.loadBundledScheme(bundledPath, this)
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
    schemeManager.loadBundledScheme(bundledPath, this)
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

  @Test fun `rename A to B and B to A`() {
    val dir = tempDirManager.newPath()
    val schemeManager = createSchemeManager(dir)

    val a = TestScheme("a", "a")
    val b = TestScheme("b", "b")
    schemeManager.setSchemes(listOf(a, b))
    schemeManager.save()

    assertThat(dir.resolve("a.xml")).isRegularFile()
    assertThat(dir.resolve("b.xml")).isRegularFile()

    a.name = "b"
    b.name = "a"

    schemeManager.save()

    assertThat(dir.resolve("a.xml").readText()).isEqualTo("""<scheme name="a" data="b" />""")
    assertThat(dir.resolve("b.xml").readText()).isEqualTo("""<scheme name="b" data="a" />""")
  }

  @Test fun `VFS - rename A to B and B to A`() {
    val dir = tempDirManager.newPath(refreshVfs = true)
    val schemeManager = SchemeManagerImpl(FILE_SPEC, TestSchemesProcessor(), null, dir, fileChangeSubscriber = { schemeManager ->
      @Suppress("UNCHECKED_CAST")
      ApplicationManager.getApplication().messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, SchemeFileTracker(
        schemeManager as SchemeManagerImpl<Any, Any>, null))
    })

    val a = TestScheme("a", "a")
    val b = TestScheme("b", "b")
    schemeManager.setSchemes(listOf(a, b))
    runInEdtAndWait { schemeManager.save() }

    assertThat(dir.resolve("a.xml")).isRegularFile()
    assertThat(dir.resolve("b.xml")).isRegularFile()

    a.name = "b"
    b.name = "a"

    runInEdtAndWait { schemeManager.save() }

    assertThat(dir.resolve("a.xml").readText()).isEqualTo("""<scheme name="a" data="b" />""")
    assertThat(dir.resolve("b.xml").readText()).isEqualTo("""<scheme name="b" data="a" />""")
  }

  @Test fun `path must not contains ROOT_CONFIG macro`() {
    assertThatThrownBy { SchemeManagerFactory.getInstance().create("\$ROOT_CONFIG$/foo", TestSchemesProcessor()) }.hasMessage("Path must not contains ROOT_CONFIG macro, corrected: foo")
  }

  @Test fun `path must be system-independent`() {
    assertThatThrownBy { SchemeManagerFactory.getInstance().create("foo\\bar", TestSchemesProcessor())}.hasMessage("Path must be system-independent, use forward slash instead of backslash")
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
    for (file in it) {
      val scheme = loadElement(file).deserialize(TestScheme::class.java)
      assertThat(fileToSchemeMap.get(FileUtil.getNameWithoutExtension(file.fileName.toString()))).isEqualTo(scheme.name)
    }
  }
}

@Tag("scheme")
data class TestScheme(@field:com.intellij.util.xmlb.annotations.Attribute @field:kotlin.jvm.JvmField var name: String = "", @field:com.intellij.util.xmlb.annotations.Attribute var data: String? = null) : ExternalizableScheme, SerializableScheme {
  override fun getName() = name

  override fun setName(value: String) {
    name = value
  }

  override fun writeScheme() = serialize()!!
}

open class TestSchemesProcessor : LazySchemeProcessor<TestScheme, TestScheme>() {
  override fun createScheme(dataHolder: SchemeDataHolder<TestScheme>,
                            name: String,
                            attributeProvider: Function<in String, String?>,
                            isBundled: Boolean): TestScheme {
    val scheme = dataHolder.read().deserialize(TestScheme::class.java)
    dataHolder.updateDigest(scheme)
    return scheme
  }
}