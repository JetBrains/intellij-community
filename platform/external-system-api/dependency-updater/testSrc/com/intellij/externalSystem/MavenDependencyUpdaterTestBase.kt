package com.intellij.externalSystem

import com.intellij.externalSystem.DependencyModifierService.Companion.getInstance
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.VfsTestUtil
import junit.framework.AssertionFailedError
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

abstract class MavenDependencyUpdaterTestBase : MavenMultiVersionImportingTestCase() {
  private var myTestDataDir: File? = null
  private var myProjectDataDir: File? = null
  protected var myExpectedDataDir: File? = null

  protected var myModifierService: DependencyModifierService? = null

  public override fun setUp() = runBlocking {
    super.setUp()
    myTestDataDir = PathManagerEx.findFileUnderCommunityHome("platform/external-system-api/dependency-updater/testData/maven")
    assertTrue(myTestDataDir!!.isDirectory)
    myProjectDataDir = File(File(myTestDataDir, "projects"), getTestName(true))
    myExpectedDataDir = File(File(myTestDataDir, "expected"), getTestName(true))
    myModifierService = getInstance(myProject)
    prepareAndImport()
  }

  private suspend fun prepareAndImport() {
    createProjectPom("")
    FileUtil.copyDir(myProjectDataDir!!, myProjectRoot.toNioPath().toFile())
    myProjectRoot.refresh(false, true)
    importProjectAsync()
  }

  protected suspend fun findDependencyTag(group: String, artifact: String, version: String): XmlTag? {
    return readAction {
      val pom = PsiUtilCore.getPsiFile(myProject, myProjectPom)
      findDependencyTag(group, artifact, version, pom)
    }
  }

  private fun findDependencyTag(group: String, artifact: String, version: String, pom: PsiFile?): XmlTag? {
    val model = MavenDomUtil.getMavenDomModel(pom!!, MavenDomProjectModel::class.java)
    for (dependency in model!!.dependencies.dependencies) {
      if (dependency.groupId.stringValue == group && dependency.artifactId.stringValue == artifact && dependency.version.stringValue == version) {
        return dependency.xmlTag
      }
    }
    return null
  }

  @Throws(IOException::class)
  protected fun assertFilesAsExpected() {
    assertTrue(File(myExpectedDataDir, "pom.xml").isFile)

    Files.walkFileTree(myExpectedDataDir!!.toPath(), object : SimpleFileVisitor<Path>() {
      @Throws(IOException::class)
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val expectedFile = file.toFile()
        val relativePath = myExpectedDataDir!!.toPath().relativize(file)
        val actual = myProjectRoot.findFileByRelativePath(FileUtil.normalize(relativePath.toString()))
        if (actual == null) {
          fail("File $file not found in actual dir")
        }
        val value = String(actual!!.contentsToByteArray(), actual.charset)
        assertFilesAreIdenticalLineByLineIgnoringIndent(expectedFile.path, value)
        return FileVisitResult.CONTINUE
      }
    })
  }

  private fun assertFilesAreIdenticalLineByLineIgnoringIndent(expectedFilePath: String, actualText: String) {
    val expectedText: String
    try {
      if (UsefulTestCase.OVERWRITE_TESTDATA) {
        VfsTestUtil.overwriteTestData(expectedFilePath, actualText)
        println("File $expectedFilePath created.")
      }
      expectedText = FileUtil.loadFile(File(expectedFilePath), StandardCharsets.UTF_8)
    }
    catch (e: FileNotFoundException) {
      VfsTestUtil.overwriteTestData(expectedFilePath, actualText)
      throw AssertionFailedError("No output text found. File $expectedFilePath created.")
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }

    val expectedLines = Arrays.stream(StringUtil.splitByLines(expectedText.trim { it <= ' ' }, true))
      .map { s: String -> s.trimStart() }
      .toArray()
    val actualLines = Arrays.stream(StringUtil.splitByLines(actualText.trim { it <= ' ' }, true))
      .map { s: String -> s.trimStart() }
      .toArray()

    if (!expectedLines.contentEquals(actualLines)) {
      throw FileComparisonFailedError(null, StringUtil.convertLineSeparators(expectedText.trim { it <= ' ' }),
                                      StringUtil.convertLineSeparators(actualText.trim { it <= ' ' }), expectedFilePath)
    }
  }
}
