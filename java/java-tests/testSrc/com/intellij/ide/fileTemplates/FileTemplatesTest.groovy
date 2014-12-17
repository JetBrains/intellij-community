package com.intellij.ide.fileTemplates
import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.ide.fileTemplates.impl.FileTemplateTestUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IdeaTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.properties.EncodingAwareProperties

public class FileTemplatesTest extends IdeaTestCase {
  private File myTestConfigDir;

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    if (myTestConfigDir !=null && myTestConfigDir.exists()) {
      FileUtil.delete(myTestConfigDir);
    }
  }

  public void testAllTemplates() throws Exception {
    final File testsDir = new File(PathManagerEx.getTestDataPath()+"/ide/fileTemplates");

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
      File[] children = testsDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(".out"+txt);
        }
      });

      assertTrue(children.length > 0);
      for (File resultFile : children) {
        String name = resultFile.getName();
        String base = name.substring(0, name.length() - txt.length() - ".out".length());
        File propFile = new File(resultFile.getParent(), base + ".prop" + txt);
        File inFile = new File(resultFile.getParent(), base + txt);
  
        String inputText = FileUtil.loadFile(inFile, FileTemplate.ourEncoding);
        String outputText = FileUtil.loadFile(resultFile, FileTemplate.ourEncoding);
  
        EncodingAwareProperties properties = new EncodingAwareProperties();
  
        properties.load(propFile, FileTemplate.ourEncoding);
        properties.put(FileTemplateManager.PROJECT_NAME_VARIABLE, getProject().getName())
  
        System.out.println(resultFile.getName());
        doTestTemplate(inputText, properties, outputText);
      }
    }
    finally {
      templateManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, originalIncludes);
    }
  }

  private void doTestTemplate(String inputString, Properties properties, String expected) throws Exception {
    inputString = StringUtil.convertLineSeparators(inputString);
    expected = StringUtil.convertLineSeparators(expected);
    
    final String result = FileTemplateUtil.mergeTemplate(properties, inputString, false);
    assertEquals(expected, result);

    List attrs = Arrays.asList(FileTemplateUtil.calculateAttributes(inputString, new Properties(), false, getProject()));
    assertTrue(properties.size() - 1 <= attrs.size());
    Enumeration e = properties.propertyNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      assertTrue("Attribute '" + s + "' not found in properties", attrs.contains(s) || FileTemplateManager.PROJECT_NAME_VARIABLE.equals(s));
    }
  }

  public void testFindFileByUrl() throws Exception {
    FileTemplate catchBodyTemplate = FileTemplateManager.getInstance(getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    assertNotNull(catchBodyTemplate);
  }

  public void "test collect undefined attribute names"() {
    FileTemplate template = addTestTemplate("myclass", '${ABC} ${DEF} ${NAME}')
    Properties properties = new Properties()
    properties.NAME = 'zzz'
    assert template.getUnsetAttributes(properties, project) as Set == ['ABC', 'DEF'] as Set
  }

  public void "test collect undefined attribute names from included templates"() {
    def included = addTestTemplate("included", '${ABC} ${DEF}')
    assert included == FileTemplateManager.getInstance(getProject()).getTemplate("included.java")

    FileTemplate template = addTestTemplate("myclass", '#parse("included.java") ${DEF} ${NAME}')
    Properties properties = new Properties()
    properties.NAME = 'zzz'
    assert template.getUnsetAttributes(properties, project) as Set == ['ABC', 'DEF'] as Set
  }

  public void testDefaultPackage() throws Exception {
    String name = "myclass";
    FileTemplate template = addTestTemplate(name, 'package ${PACKAGE_NAME}; public class ${NAME} {}')

    File temp = FileUtil.createTempDirectory(getTestName(true), "");

    myFilesToDelete.add(temp);
    final VirtualFile tempDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);

    PsiTestUtil.addSourceRoot(getModule(), tempDir);

    VirtualFile sourceRoot = ModuleRootManager.getInstance(getModule()).getSourceRoots()[0];
    PsiDirectory psiDirectory = PsiManager.getInstance(getProject()).findDirectory(sourceRoot);

    PsiClass psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory, "XXX", name);
    assertNotNull(psiClass);
    assertEquals("public class XXX {\n}", psiClass.getContainingFile().getText());
    FileTemplateManager.getInstance(getProject()).removeTemplate(template);
  }

  private FileTemplate addTestTemplate(String name, String text) {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).addTemplate(name, "java");
    disposeOnTearDown({ FileTemplateManager.getInstance(getProject()).removeTemplate(template) } as Disposable)
    template.setText(text);
    template
  }

  public void doTestSaveLoadTemplate(String name, String ext) {
    FileTemplateTestUtil.TestFTManager templateManager = new FileTemplateTestUtil.TestFTManager("test", "testTemplates",
                                                                                                getTestConfigRoot());
    FileTemplate template = templateManager.addTemplate(name, ext);
    String qName = template.getQualifiedName();
    templateManager.saveTemplates();
    templateManager.removeTemplate(qName);
    FileTemplateTestUtil.loadCustomizedContent(templateManager);
    FileTemplate loadedTemplate = templateManager.findTemplateByName(name);
    assertNotNull("Template '" + qName + "' was not found", loadedTemplate);
    assertEquals(name, loadedTemplate.getName());
    assertEquals(ext, loadedTemplate.getExtension());
    assertTrue(template != loadedTemplate);
  }

  private File getTestConfigRoot() throws Exception {
    if (myTestConfigDir == null) {
      myTestConfigDir = FileUtil.createTempDirectory(getTestName(true), "config");
    }
    return myTestConfigDir;
  }

  public void testSaveLoadCustomTemplate() throws Exception {
    doTestSaveLoadTemplate("name", "ext");
  }

  public void testSaveLoadCustomTemplateDottedName() throws Exception {
    doTestSaveLoadTemplate("name.has.dots", "ext");
  }

  public void testSaveLoadCustomTemplateDottedExt() throws Exception {
    doTestSaveLoadTemplate("name", "ext.has.dots");
  }
}
