package com.intellij.ide.fileTemplates;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.properties.EncodingAwareProperties;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

@SuppressWarnings({"HardCodedStringLiteral"})
public class FileTemplatesTest extends IdeaTestCase {
  public void testAllTemplates() throws Exception {
    File testsDir = new File(PathManagerEx.getTestDataPath()+"/ide/fileTemplates");
    final String txt = ".txt";

    File[] children = testsDir.listFiles(new FilenameFilter() {
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

      String inputText = new String(FileUtil.loadFileText(inFile, FileTemplate.ourEncoding));
      String outputText = new String(FileUtil.loadFileText(resultFile, FileTemplate.ourEncoding));

      EncodingAwareProperties properties = new EncodingAwareProperties();

      properties.load(propFile, FileTemplate.ourEncoding);

      System.out.println(resultFile.getName());
      doTestTemplate(inputText, properties, outputText, resultFile.getParent());
    }
  }

  private static void doTestTemplate(String inputString, Properties properties, String expected, String dir) throws Exception {
    String result = FileTemplateUtil.mergeTemplate(properties, inputString);
    assertEquals(expected, result);

    List attrs = Arrays.asList(FileTemplateUtil.calculateAttributes(inputString, new Properties(), false));
    assertTrue(properties.size() <= attrs.size());
    Enumeration e = properties.propertyNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      assertTrue("Attribute '" + s + "' not found in properties", attrs.contains(s));
    }
  }

  public void testFindFileByUrl() throws Exception {
    FileTemplate catchBodyTemplate = FileTemplateManager.getInstance().getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    assertNotNull(catchBodyTemplate);
  }

  public void testDefaultPackage() throws Exception {
    String name = "myclass";
    FileTemplate template = FileTemplateManager.getInstance().addInternal(name/*+"ForTest"*/, "java");
    try {
      template.setText("package ${PACKAGE_NAME}; public class ${NAME} {}");

      File temp = FileUtil.createTempDirectory(getTestName(true), "");

      myFilesToDelete.add(temp);
      VirtualFile tempDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);

      ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());
      ModifiableRootModel model = rootManager.getModifiableModel();
      ContentEntry contentEntry = model.addContentEntry(tempDir);
      contentEntry.addSourceFolder(tempDir, false);
      model.commit();

      VirtualFile sourceRoot = rootManager.getSourceRoots()[0];
      PsiDirectory psiDirectory = PsiManager.getInstance(getProject()).findDirectory(sourceRoot);

      PsiClass psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory, "XXX", name);
      assertNotNull(psiClass);
      assertEquals("public class XXX {\n}", psiClass.getContainingFile().getText());
    }
    finally {
      FileTemplateManager.getInstance().saveAll();
      FileTemplateManager.getInstance().removeTemplate(template, false);
    }
  }
}
