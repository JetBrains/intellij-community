// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.fileTemplates.impl

import com.intellij.ide.fileTemplates.*
import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.ide.fileTemplates.impl.FTManager
import com.intellij.ide.fileTemplates.impl.FileTemplateBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.JavaProjectTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.registerExtension
import com.intellij.util.io.delete
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.*

internal class FileTemplatesTest : JavaProjectTestCase() {
  private var testConfigDir: Path? = null

  override fun tearDown() {
    try {
      testConfigDir?.delete()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testAllTemplates() {
    val testsDir = File("${PathManagerEx.getTestDataPath()}/ide/fileTemplates")
    val includeTemplateName = "include1.inc"
    val includeTemplateExtension = "txt"
    val customIncludeFileName = "$includeTemplateName.$includeTemplateExtension"
    val customInclude = File(testsDir, customIncludeFileName)
    val includeText = FileUtil.loadFile(customInclude, FileTemplate.ourEncoding)
    val templateManager = FileTemplateManager.getInstance(project)
    val originalIncludes = ArrayList(Arrays.asList(*templateManager.allPatterns))
    try {
      // configure custom include
      val allIncludes: MutableList<FileTemplate> = ArrayList(originalIncludes)
      val custom = CustomFileTemplate(includeTemplateName, includeTemplateExtension)
      custom.text = includeText
      allIncludes.add(custom)
      templateManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, allIncludes)
      val txt = ".txt"
      val children = testsDir.listFiles { dir: File?, name: String -> name.endsWith(".out$txt") }
      assertThat(children).isNotEmpty()
      for (resultFile in children) {
        val name = resultFile.name
        val base = name.substring(0, name.length - txt.length - ".out".length)
        val propFile = File(resultFile.parent, "$base.prop$txt")
        val inFile = File(resultFile.parent, base + txt)
        val inputText = FileUtil.loadFile(inFile, FileTemplate.ourEncoding)
        val outputText = FileUtil.loadFile(resultFile, FileTemplate.ourEncoding)
        val properties = Properties()
        properties.load(FileReader(propFile))
        properties[FileTemplateManager.PROJECT_NAME_VARIABLE] = project.name
        UsefulTestCase.LOG.debug(resultFile.name)
        doTestTemplate(inputText, properties, outputText)
      }
    }
    finally {
      templateManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, originalIncludes)
    }
  }

  private fun doTestTemplate(inputString: String, properties: Properties, expected: String) {
    var inputString: String? = inputString
    var expected: String? = expected
    inputString = StringUtil.convertLineSeparators(inputString!!)
    expected = StringUtil.convertLineSeparators(expected!!)
    val result = FileTemplateUtil.mergeTemplate(properties, inputString, false)
    assertEquals(expected, result)
    val attrs = FileTemplateUtil.calculateAttributes(inputString, Properties(), false, project).asList()
    assertThat(properties.size - 1 <= attrs.size).isTrue()
    val e = properties.propertyNames()
    while (e.hasMoreElements()) {
      val s = e.nextElement() as String
      assertTrue("Attribute '$s' not found in properties", attrs.contains(s) || FileTemplateManager.PROJECT_NAME_VARIABLE === s)
    }
  }

  fun testFindFileByUrl() {
    val catchBodyTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY)
    assertNotNull(catchBodyTemplate)
  }

  fun testCollect_undefined_attribute_names() {
    val template = addTestTemplate("my_class", "\${ABC} \${DEF} \${NAME}")
    val properties = Properties()
    properties["NAME"] = "zzz"
    assertThat(template.getUnsetAttributes(properties, project)).containsOnly("ABC", "DEF")
  }

  fun test_collect_undefined_attribute_names_from_included_templates() {
    val included = addTestTemplate("included", "\${ABC} \${DEF}")
    assert(included === FileTemplateManager.getInstance(project).getTemplate("included.java"))
    val template = addTestTemplate("my_class", "#parse(\"included.java\") \${DEF} \${NAME}")
    val properties = Properties()
    properties["NAME"] = "zzz"
    assertThat(template.getUnsetAttributes(properties, project)).contains("ABC", "DEF")
  }

  fun testDefaultPackage() {
    doClassTest("package \${PACKAGE_NAME}; public class \${NAME} {}", "public class XXX {\n}")
  }

  private fun doClassTest(templateText: String, result: String) {
    val name = "my_class"
    val template = addTestTemplate(name, templateText)
    val psiDirectory = createDirectory()
    val psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory!!, "XXX", name)
    assertNotNull(psiClass)
    assertEquals(result, psiClass.containingFile.text)
    FileTemplateManager.getInstance(project).removeTemplate(template)
  }

  fun testPopulateDefaultProperties() {
    val name = "my_class"
    val template = addTestTemplate(name, """
   package ${"$"}{PACKAGE_NAME}; 
   // ${"$"}{USER} 
   public class ${"$"}{NAME} {}
   """.trimIndent())
    val psiDirectory = createDirectory()
    val psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory!!, "XXX", name)
    assertFalse(psiClass.containingFile.text.contains("\${USER}"))
    FileTemplateManager.getInstance(project).removeTemplate(template)
  }

  fun testDirPath() {
    val template = FileTemplateManager.getInstance(project).addTemplate(name, "txt")
    disposeOnTearDown(
      Disposable { FileTemplateManager.getInstance(project).removeTemplate(template) })
    template.text = "\${DIR_PATH}; \${FILE_NAME}"
    val tempDir = tempDir.createVirtualDir()
    val directory = PsiManager.getInstance(project).findDirectory(tempDir)
    val element = FileTemplateUtil.createFromTemplate(template, "foo", Properties(), directory!!)
    com.intellij.testFramework.assertions.Assertions.assertThat(element.text).endsWith(tempDir.nameSequence.toString() + "; foo.txt")
  }

  fun testFileNameTrimming() {
    val handler: CreateFromTemplateHandler = DefaultCreateFromTemplateHandler()
    ApplicationManager.getApplication().registerExtension(CreateFromTemplateHandler.EP_NAME, handler, testRootDisposable)
    val template = FileTemplateManager.getInstance(project).addTemplate(name, "txt")
    disposeOnTearDown(
      Disposable { FileTemplateManager.getInstance(project).removeTemplate(template) })
    template.text = "\${FILE_NAME}"
    val tempDir = tempDir.createVirtualDir()
    val directory = PsiManager.getInstance(project).findDirectory(tempDir)
    val element = FileTemplateUtil.createFromTemplate(template, "foo.txt", Properties(), directory!!)
    assertEquals("foo.txt", element.text)
  }

  private fun addTestTemplate(name: String, text: String): FileTemplate {
    val template = FileTemplateManager.getInstance(project).addTemplate(name, "java")
    disposeOnTearDown(Disposable { FileTemplateManager.getInstance(project).removeTemplate(template) })
    template.text = text
    return template
  }

  private fun createDirectory(): PsiDirectory? {
    val tempDir = tempDir.createVirtualDir()
    PsiTestUtil.addSourceRoot(module, tempDir)
    val sourceRoot = ModuleRootManager.getInstance(module).sourceRoots[0]
    return PsiManager.getInstance(project).findDirectory(sourceRoot)
  }

  private fun doTestSaveLoadTemplate(name: String, ext: String) {
    val templateManager = FTManager("test", testConfigRoot!!)
    val template = templateManager.addTemplate(name, ext) as FileTemplateBase
    val qName = template.qualifiedName
    templateManager.saveTemplates()
    templateManager.removeTemplate(qName)
    templateManager.loadCustomizedContent()
    val loadedTemplate: FileTemplate? = templateManager.findTemplateByName(name)
    assertNotNull("Template '$qName' was not found", loadedTemplate)
    assertEquals(name, loadedTemplate!!.name)
    assertEquals(ext, loadedTemplate.extension)
    assertNotSame(template, loadedTemplate)
  }

  private val testConfigRoot: Path?
    get() {
      if (testConfigDir == null) {
        testConfigDir = FileUtil.createTempDirectory(getTestName(true), "config").toPath()
      }
      return testConfigDir
    }

  fun testSaveLoadCustomTemplate() {
    doTestSaveLoadTemplate("name", "ext")
  }

  fun testSaveLoadCustomTemplateDottedName() {
    doTestSaveLoadTemplate("name.has.dots", "ext")
  }

  fun testSaveLoadCustomTemplateDottedExt() {
    if (checkFileWithUnicodeNameCanBeFound()) {
      doTestSaveLoadTemplate("name", "ext.has.dots")
    }
  }

  fun testCanCreateDoubleExtension() {
    val template = FileTemplateManager.getInstance(project).addTemplate(name, "my.txt")
    disposeOnTearDown(Disposable { FileTemplateManager.getInstance(project).removeTemplate(template) })
    val tempDir = tempDir.createVirtualDir()
    val directory = PsiManager.getInstance(project).findDirectory(tempDir)
    assertTrue(FileTemplateUtil.canCreateFromTemplate(arrayOf(directory), template))
  }

  private fun checkFileWithUnicodeNameCanBeFound(): Boolean {
    try {
      val name = FTManager.encodeFileName("test", "ext.has.dots")
      val file = createTempFile(name, "test")
      FileUtil.loadFile(File(file.absolutePath), StandardCharsets.UTF_8)
      UsefulTestCase.LOG.debug("File loaded: " + file.absolutePath)
      val dir = File(file.parent)
      val files = dir.listFiles()
      assertNotNull(files)
      val nameList = ArrayList<String>()
      for (child in files!!) {
        nameList.add(child.name)
      }
      for (listedName in nameList) {
        if (listedName == name) {
          return true
        }
      }
      UsefulTestCase.LOG.debug("No matching file found, locale: " + Locale.getDefault().displayName)
    }
    catch (ignored: IOException) {
    }
    return false
  }

  fun testStringUtilsSpecialVariableWorksAndHasRemoveAndHumpMethod() {
    val template = addTestTemplate("my_class", "prefix \${StringUtils.removeAndHump(\"foo_barBar\")} suffix")
    assertThat(template.getText(emptyMap<Any?, Any>())).isEqualTo("prefix FooBarBar suffix")
  }
}