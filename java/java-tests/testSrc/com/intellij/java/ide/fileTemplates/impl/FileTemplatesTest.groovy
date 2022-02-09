// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.fileTemplates.impl

import com.intellij.ide.fileTemplates.*
import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.ide.fileTemplates.impl.FTManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.JavaProjectTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.ServiceContainerUtil
import com.intellij.util.io.PathKt

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

class FileTemplatesTest extends JavaProjectTestCase {
  private Path myTestConfigDir

  @Override
  protected void tearDown() {
    super.tearDown()
    if (myTestConfigDir != null) {
      PathKt.delete(myTestConfigDir)
    }
  }

  void testAllTemplates() {
    final File testsDir = new File(PathManagerEx.getTestDataPath()+"/ide/fileTemplates")

    final String includeTemplateName = "include1.inc"
    final String includeTemplateExtension = "txt"
    final String customIncludeFileName = includeTemplateName + "." + includeTemplateExtension
    final File customInclude = new File(testsDir, customIncludeFileName)
    final String includeText = FileUtil.loadFile(customInclude, FileTemplate.ourEncoding)

    final FileTemplateManager templateManager = FileTemplateManager.getInstance(getProject())
    final ArrayList<FileTemplate> originalIncludes = new ArrayList<FileTemplate>(Arrays.asList(templateManager.getAllPatterns()))
    try {
      // configure custom include
      final List<FileTemplate> allIncludes = new ArrayList<FileTemplate>(originalIncludes)
      final CustomFileTemplate custom = new CustomFileTemplate(includeTemplateName, includeTemplateExtension)
      custom.setText(includeText)
      allIncludes.add(custom)
      templateManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, allIncludes)

      final String txt = ".txt"
      File[] children = testsDir.listFiles(new FilenameFilter() {
        @Override
         boolean accept(File dir, String name) {
          return name.endsWith(".out"+txt)
        }
      })

      assertTrue(children.length > 0)
      for (File resultFile : children) {
        String name = resultFile.getName()
        String base = name.substring(0, name.length() - txt.length() - ".out".length())
        File propFile = new File(resultFile.getParent(), base + ".prop" + txt)
        File inFile = new File(resultFile.getParent(), base + txt)

        String inputText = FileUtil.loadFile(inFile, FileTemplate.ourEncoding)
        String outputText = FileUtil.loadFile(resultFile, FileTemplate.ourEncoding)

        Properties properties = new Properties()

        properties.load(new FileReader(propFile))
        properties.put(FileTemplateManager.PROJECT_NAME_VARIABLE, getProject().getName())

        LOG.debug(resultFile.getName())
        doTestTemplate(inputText, properties, outputText)
      }
    }
    finally {
      templateManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, originalIncludes)
    }
  }

  private void doTestTemplate(String inputString, Properties properties, String expected) {
    inputString = StringUtil.convertLineSeparators(inputString)
    expected = StringUtil.convertLineSeparators(expected)

    final String result = FileTemplateUtil.mergeTemplate(properties, inputString, false)
    assertEquals(expected, result)

    List attrs = Arrays.asList(FileTemplateUtil.calculateAttributes(inputString, new Properties(), false, getProject()))
    assertTrue(properties.size() - 1 <= attrs.size())
    Enumeration e = properties.propertyNames()
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement()
      assertTrue("Attribute '" + s + "' not found in properties", attrs.contains(s) || FileTemplateManager.PROJECT_NAME_VARIABLE == s)
    }
  }

  void testFindFileByUrl() {
    FileTemplate catchBodyTemplate = FileTemplateManager.getInstance(getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY)
    assertNotNull(catchBodyTemplate)
  }

  void "test collect undefined attribute names"() {
    FileTemplate template = addTestTemplate("my_class", '${ABC} ${DEF} ${NAME}')
    Properties properties = new Properties()
    properties.NAME = 'zzz'
    assert template.getUnsetAttributes(properties, project) as Set == ['ABC', 'DEF'] as Set
  }

  void "test collect undefined attribute names from included templates"() {
    def included = addTestTemplate("included", '${ABC} ${DEF}')
    assert included == FileTemplateManager.getInstance(getProject()).getTemplate("included.java")

    FileTemplate template = addTestTemplate("my_class", '#parse("included.java") ${DEF} ${NAME}')
    Properties properties = new Properties()
    properties.NAME = 'zzz'
    assert template.getUnsetAttributes(properties, project) as Set == ['ABC', 'DEF'] as Set
  }

  void testDefaultPackage() {
    doClassTest('package ${PACKAGE_NAME}; public class ${NAME} {}', "public class XXX {\n}")
  }

  private void doClassTest(String templateText, String result) {
    String name = "my_class"
    FileTemplate template = addTestTemplate(name, templateText)
    PsiDirectory psiDirectory = createDirectory()
    PsiClass psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory, "XXX", name)
    assertNotNull(psiClass)
    assertEquals(result, psiClass.getContainingFile().getText())
    FileTemplateManager.getInstance(getProject()).removeTemplate(template)
  }

  void testPopulateDefaultProperties() {
    String name = "my_class"
    FileTemplate template = addTestTemplate(name, 'package ${PACKAGE_NAME}; \n' +
                                                  '// ${USER} \n' +
                                                  'public class ${NAME} {}')
    PsiDirectory psiDirectory = createDirectory()
    PsiClass psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory, "XXX", name)
    assertFalse(psiClass.getContainingFile().getText().contains('${USER}'))
    FileTemplateManager.getInstance(getProject()).removeTemplate(template)
  }

  void testDirPath() {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).addTemplate(name, "txt")
    disposeOnTearDown({ FileTemplateManager.getInstance(getProject()).removeTemplate(template) } as Disposable)
    template.setText('${DIR_PATH}; ${FILE_NAME}')

    VirtualFile tempDir = getTempDir().createVirtualDir()
    def directory = PsiManager.getInstance(project).findDirectory(tempDir)
    def element = FileTemplateUtil.createFromTemplate(template, "foo", new Properties(), directory)

    assertThat(element.getText()).endsWith(tempDir.nameSequence + "; foo.txt")
  }

  void testFileNameTrimming() {
    CreateFromTemplateHandler handler = new DefaultCreateFromTemplateHandler()
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), CreateFromTemplateHandler.EP_NAME, handler, getTestRootDisposable())
    FileTemplate template = FileTemplateManager.getInstance(getProject()).addTemplate(name, "txt")
    disposeOnTearDown({ FileTemplateManager.getInstance(getProject()).removeTemplate(template) } as Disposable)
    template.setText('${FILE_NAME}')

    VirtualFile tempDir = getTempDir().createVirtualDir()
    def directory = PsiManager.getInstance(project).findDirectory(tempDir)
    def element = FileTemplateUtil.createFromTemplate(template, "foo.txt", new Properties(), directory)

    assertEquals("foo.txt", element.getText())
  }

  private FileTemplate addTestTemplate(String name, String text) {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).addTemplate(name, "java")
    disposeOnTearDown({ FileTemplateManager.getInstance(getProject()).removeTemplate(template) } as Disposable)
    template.setText(text)
    template
  }

  private PsiDirectory createDirectory() {
    VirtualFile tempDir = getTempDir().createVirtualDir()
    PsiTestUtil.addSourceRoot(getModule(), tempDir)
    VirtualFile sourceRoot = ModuleRootManager.getInstance(getModule()).getSourceRoots()[0]
    PsiManager.getInstance(getProject()).findDirectory(sourceRoot)
  }

  void doTestSaveLoadTemplate(String name, String ext) {
    FTManager templateManager = new FTManager("test", getTestConfigRoot())
    FileTemplate template = templateManager.addTemplate(name, ext)
    String qName = template.getQualifiedName()
    templateManager.saveTemplates()
    templateManager.removeTemplate(qName)
    templateManager.loadCustomizedContent()
    FileTemplate loadedTemplate = templateManager.findTemplateByName(name)
    assertNotNull("Template '" + qName + "' was not found", loadedTemplate)
    assertEquals(name, loadedTemplate.getName())
    assertEquals(ext, loadedTemplate.getExtension())
    assertTrue(template != loadedTemplate)
  }

  private Path getTestConfigRoot() {
    if (myTestConfigDir == null) {
      myTestConfigDir = FileUtil.createTempDirectory(getTestName(true), "config").toPath()
    }
    return myTestConfigDir
  }

  void testSaveLoadCustomTemplate() {
    doTestSaveLoadTemplate("name", "ext")
  }

  void testSaveLoadCustomTemplateDottedName() {
    doTestSaveLoadTemplate("name.has.dots", "ext")
  }

  void testSaveLoadCustomTemplateDottedExt() {
    if (checkFileWithUnicodeNameCanBeFound()) {
      doTestSaveLoadTemplate("name", "ext.has.dots")
    }
  }

  void testCanCreateDoubleExtension() {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).addTemplate(name, "my.txt")
    disposeOnTearDown({ FileTemplateManager.getInstance(getProject()).removeTemplate(template) } as Disposable)

    VirtualFile tempDir = getTempDir().createVirtualDir()
    def directory = PsiManager.getInstance(project).findDirectory(tempDir)
    assertTrue(FileTemplateUtil.canCreateFromTemplate([directory].toArray(PsiDirectory.EMPTY_ARRAY), template))
  }

  private boolean checkFileWithUnicodeNameCanBeFound() {
    try {
      //noinspection GroovyAccessibility
      String name = FTManager.encodeFileName("test", "ext.has.dots")
      File file = createTempFile(name, "test")
      FileUtil.loadFile(new File(file.getAbsolutePath()), StandardCharsets.UTF_8)
      LOG.debug("File loaded: " + file.getAbsolutePath())
      File dir = new File(file.getParent())
      File[] files = dir.listFiles()
      assertNotNull(files)
      List<String> nameList = new ArrayList<>()
      for (File child : files) {
        nameList.add(child.getName())
      }
      for (String listedName : nameList) {
        if (listedName == name) {
          return true
        }
      }
      LOG.debug("No matching file found, locale: " + Locale.getDefault().displayName)
      return false
    }
    catch (IOException ignored) {
      return false
    }
  }

  void 'test StringUtils special variable works and has removeAndHump method'() {
    FileTemplate template = addTestTemplate("my_class", 'prefix ${StringUtils.removeAndHump("foo_barBar")} suffix')
    def evaluated = template.getText([:])
    assert evaluated == 'prefix FooBarBar suffix'
  }
}