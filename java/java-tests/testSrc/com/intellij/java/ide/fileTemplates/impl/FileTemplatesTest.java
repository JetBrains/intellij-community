// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.*;
import com.intellij.ide.fileTemplates.impl.BundledFileTemplate;
import com.intellij.ide.fileTemplates.impl.CustomFileTemplate;
import com.intellij.ide.fileTemplates.impl.FTManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.io.PathKt;
import org.apache.velocity.runtime.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

class FileTemplatesTest extends JavaProjectTestCase {
  private Path myTestConfigDir;

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    if (myTestConfigDir != null) {
      PathKt.delete(myTestConfigDir);
    }
  }

  void testAllTemplates() throws IOException, ParseException {
    final File testsDir = new File(PathManagerEx.getTestDataPath() + "/ide/fileTemplates");

    final String includeTemplateName = "include1.inc";
    final String includeTemplateExtension = "txt";
    final String customIncludeFileName = includeTemplateName + "." + includeTemplateExtension;
    final File customInclude = new File(testsDir, customIncludeFileName);
    final String includeText = FileUtil.loadFile(customInclude, FileTemplate.ourEncoding);

    final FileTemplateManager templateManager = FileTemplateManager.getInstance(getProject());
    final ArrayList<FileTemplate> originalIncludes = new ArrayList<FileTemplate>(Arrays.asList(templateManager.getAllPatterns()));
    try {
      // configure custom include
      final List<FileTemplate> allIncludes = new ArrayList<FileTemplate>(originalIncludes);
      final CustomFileTemplate custom = new CustomFileTemplate(includeTemplateName, includeTemplateExtension);
      custom.setText(includeText);
      allIncludes.add(custom);
      templateManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, allIncludes);

      final String txt = ".txt";
      File[] children = testsDir.listFiles((dir, name) -> name.endsWith(".out" + txt));

      assertTrue(children.length > 0);
      for (File resultFile : children) {
        String name = resultFile.getName();
        String base = name.substring(0, name.length() - txt.length() - ".out".length());
        File propFile = new File(resultFile.getParent(), base + ".prop" + txt);
        File inFile = new File(resultFile.getParent(), base + txt);

        String inputText = FileUtil.loadFile(inFile, FileTemplate.ourEncoding);
        String outputText = FileUtil.loadFile(resultFile, FileTemplate.ourEncoding);

        Properties properties = new Properties();

        properties.load(new FileReader(propFile));
        properties.put(FileTemplateManager.PROJECT_NAME_VARIABLE, getProject().getName());

        LOG.debug(resultFile.getName());
        doTestTemplate(inputText, properties, outputText);
      }
    }
    finally {
      templateManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, originalIncludes);
    }
  }

  private void doTestTemplate(String inputString, Properties properties, String expected) throws ParseException {
    inputString = StringUtil.convertLineSeparators(inputString);
    expected = StringUtil.convertLineSeparators(expected);

    final String result = FileTemplateUtil.mergeTemplate(properties, inputString, false);
    assertEquals(expected, result);

    List attrs = Arrays.asList(FileTemplateUtil.calculateAttributes(inputString, new Properties(), false, getProject()));
    assertTrue(properties.size() - 1 <= attrs.size());
    Enumeration e = properties.propertyNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      assertTrue("Attribute '" + s + "' not found in properties", attrs.contains(s) || FileTemplateManager.PROJECT_NAME_VARIABLE == s);
    }
  }

  public void testFindFileByUrl() {
    FileTemplate catchBodyTemplate = FileTemplateManager.getInstance(getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    assertNotNull(catchBodyTemplate);
  }

  public void testCollect_undefined_attribute_names() throws FileTemplateParseException {
    FileTemplate template = addTestTemplate("my_class", "${ABC} ${DEF} ${NAME}");
    Properties properties = new Properties();
    properties.put("NAME", "zzz");
    assertThat(template.getUnsetAttributes(properties, getProject())).containsOnly("ABC", "DEF");
  }

  public void test_collect_undefined_attribute_names_from_included_templates() throws FileTemplateParseException {
    FileTemplate included = addTestTemplate("included", "${ABC} ${DEF}");
    assert included == FileTemplateManager.getInstance(getProject()).getTemplate("included.java");

    FileTemplate template = addTestTemplate("my_class", "#parse(\"included.java\") ${DEF} ${NAME}");
    Properties properties = new Properties();
    properties.put("NAME", "zzz");
    assertThat(template.getUnsetAttributes(properties, getProject())).contains("ABC", "DEF");
  }

  void testDefaultPackage() {
    doClassTest("package ${PACKAGE_NAME}; public class ${NAME} {}", "public class XXX {\n}");
  }

  private void doClassTest(String templateText, String result) {
    String name = "my_class";
    FileTemplate template = addTestTemplate(name, templateText);
    PsiDirectory psiDirectory = createDirectory();
    PsiClass psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory, "XXX", name);
    assertNotNull(psiClass);
    assertEquals(result, psiClass.getContainingFile().getText());
    FileTemplateManager.getInstance(getProject()).removeTemplate(template);
  }

  void testPopulateDefaultProperties() {
    String name = "my_class";
    FileTemplate template = addTestTemplate(name, "package ${PACKAGE_NAME}; \n" +
                                                  "// ${USER} \n" +
                                                  "public class ${NAME} {}");
    PsiDirectory psiDirectory = createDirectory();
    PsiClass psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory, "XXX", name);
    assertFalse(psiClass.getContainingFile().getText().contains("${USER}"));
    FileTemplateManager.getInstance(getProject()).removeTemplate(template);
  }

  void testDirPath() throws Exception {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).addTemplate(getName(), "txt");
    disposeOnTearDown(() -> FileTemplateManager.getInstance(getProject()).removeTemplate(template));
    template.setText("${DIR_PATH}; ${FILE_NAME}");

    VirtualFile tempDir = getTempDir().createVirtualDir();
    PsiDirectory directory = PsiManager.getInstance(getProject()).findDirectory(tempDir);
    PsiElement element = FileTemplateUtil.createFromTemplate(template, "foo", new Properties(), directory);

    assertThat(element.getText()).endsWith(tempDir.getNameSequence() + "; foo.txt");
  }

  void testFileNameTrimming() throws Exception {
    CreateFromTemplateHandler handler = new DefaultCreateFromTemplateHandler();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), CreateFromTemplateHandler.EP_NAME, handler, getTestRootDisposable());
    FileTemplate template = FileTemplateManager.getInstance(getProject()).addTemplate(getName(), "txt");
    disposeOnTearDown(() -> FileTemplateManager.getInstance(getProject()).removeTemplate(template));
    template.setText("${FILE_NAME}");

    VirtualFile tempDir = getTempDir().createVirtualDir();
    PsiDirectory directory = PsiManager.getInstance(getProject()).findDirectory(tempDir);
    PsiElement element = FileTemplateUtil.createFromTemplate(template, "foo.txt", new Properties(), directory);

    assertEquals("foo.txt", element.getText());
  }

  private FileTemplate addTestTemplate(String name, String text) {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).addTemplate(name, "java");
    disposeOnTearDown(() -> FileTemplateManager.getInstance(getProject()).removeTemplate(template));
    template.setText(text);
    return template;
  }

  private PsiDirectory createDirectory() {
    VirtualFile tempDir = getTempDir().createVirtualDir();
    PsiTestUtil.addSourceRoot(getModule(), tempDir);
    VirtualFile sourceRoot = ModuleRootManager.getInstance(getModule()).getSourceRoots()[0];
    return PsiManager.getInstance(getProject()).findDirectory(sourceRoot);
  }

  private void doTestSaveLoadTemplate(String name, String ext) throws IOException {
    FTManager templateManager = new FTManager("test", getTestConfigRoot());
    BundledFileTemplate template = (BundledFileTemplate)templateManager.addTemplate(name, ext);
    String qName = template.getQualifiedName();
    templateManager.saveTemplates();
    templateManager.removeTemplate(qName);
    templateManager.loadCustomizedContent();
    FileTemplate loadedTemplate = templateManager.findTemplateByName(name);
    assertNotNull("Template '" + qName + "' was not found", loadedTemplate);
    assertEquals(name, loadedTemplate.getName());
    assertEquals(ext, loadedTemplate.getExtension());
    assertNotSame(template, loadedTemplate);
  }

  private Path getTestConfigRoot() throws IOException {
    if (myTestConfigDir == null) {
      myTestConfigDir = FileUtil.createTempDirectory(getTestName(true), "config").toPath();
    }
    return myTestConfigDir;
  }

  void testSaveLoadCustomTemplate() throws IOException {
    doTestSaveLoadTemplate("name", "ext");
  }

  void testSaveLoadCustomTemplateDottedName() throws IOException {
    doTestSaveLoadTemplate("name.has.dots", "ext");
  }

  void testSaveLoadCustomTemplateDottedExt() throws IOException {
    if (checkFileWithUnicodeNameCanBeFound()) {
      doTestSaveLoadTemplate("name", "ext.has.dots");
    }
  }

  void testCanCreateDoubleExtension() {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).addTemplate(getName(), "my.txt");
    disposeOnTearDown(() -> FileTemplateManager.getInstance(getProject()).removeTemplate(template));

    VirtualFile tempDir = getTempDir().createVirtualDir();
    PsiDirectory directory = PsiManager.getInstance(getProject()).findDirectory(tempDir);
    assertTrue(FileTemplateUtil.canCreateFromTemplate(new PsiDirectory[]{directory}, template));
  }

  private boolean checkFileWithUnicodeNameCanBeFound() {
    try {
      //noinspection GroovyAccessibility
      String name = FTManager.encodeFileName("test", "ext.has.dots");
      File file = createTempFile(name, "test");
      FileUtil.loadFile(new File(file.getAbsolutePath()), StandardCharsets.UTF_8);
      LOG.debug("File loaded: " + file.getAbsolutePath());
      File dir = new File(file.getParent());
      File[] files = dir.listFiles();
      assertNotNull(files);
      List<String> nameList = new ArrayList<>();
      for (File child : files) {
        nameList.add(child.getName());
      }
      for (String listedName : nameList) {
        if (listedName.equals(name)) {
          return true;
        }
      }
      LOG.debug("No matching file found, locale: " + Locale.getDefault().getDisplayName());
      return false;
    }
    catch (IOException ignored) {
      return false;
    }
  }

  public void testStringUtilsSpecialVariableWorksAndHasRemoveAndHumpMethod() throws IOException {
    FileTemplate template = addTestTemplate("my_class", "prefix ${StringUtils.removeAndHump(\"foo_barBar\")} suffix");
    String evaluated = template.getText(Collections.emptyMap());
    assert evaluated == "prefix FooBarBar suffix";
  }
}