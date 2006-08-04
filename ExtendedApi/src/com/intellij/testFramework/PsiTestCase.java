package com.intellij.testFramework;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Mike
 */
public abstract class PsiTestCase extends ModuleTestCase {
  protected PsiManagerImpl myPsiManager;

  protected PsiFile myFile;
  protected PsiTestData myTestDataBefore;
  protected PsiTestData myTestDataAfter;
  private String myDataRoot;

  protected void setUp() throws Exception {
    super.setUp();
    myPsiManager = (PsiManagerImpl) PsiManager.getInstance(myProject);
  }

  protected void tearDown() throws Exception {
    myPsiManager = null;
    myFile = null;
    myTestDataBefore = null;
    myTestDataAfter = null;
    super.tearDown();
  }

  protected PsiFile createDummyFile(String fileName, String text) throws IncorrectOperationException {
    return myPsiManager.getElementFactory().createFileFromText(fileName, text);
  }

  protected PsiFile createFile(@NonNls String fileName, String text) throws Exception {
    return createFile(myModule, fileName, text);
  }
  protected PsiFile createFile(Module module, String fileName, String text) throws Exception {
    File dir = createTempDir("unitTest");
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));

    return createFile(module, vDir, fileName, text);
  }

  protected PsiFile createFile(Module module, VirtualFile vDir, String fileName, String text) throws IOException {
    if (!ModuleRootManager.getInstance(module).getFileIndex().isInSourceContent(vDir)) {
      PsiTestUtil.addSourceContentToRoots(module, vDir);
    }

    final VirtualFile vFile = vDir.createChildData(vDir, fileName);
    VfsUtil.saveText(vFile, text);
    assertNotNull(vFile);
    final PsiFile file = myPsiManager.findFile(vFile);
    assertNotNull(file);
    return file;
  }

  protected PsiElement configureByFileWithMarker(String filePath, String marker) throws Exception{
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(filePath.replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", vFile);

    String fileText = VfsUtil.loadText(vFile);
    fileText = StringUtil.convertLineSeparators(fileText, "\n");

    int offset = fileText.indexOf(marker);
    assertTrue(offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + marker.length());

    myFile = createFile(vFile.getName(), fileText);

    return myFile.findElementAt(offset);
  }

  protected void configure(String path, String dataName) throws Exception {
    myDataRoot = PathManagerEx.getTestDataPath() + path;

    myTestDataBefore = loadData(dataName);

    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.4"));
    VirtualFile vDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, myDataRoot, myFilesToDelete);

    final VirtualFile vFile = vDir.findChild(myTestDataBefore.getTextFile());
    myFile = myPsiManager.findFile(vFile);
  }

  private PsiTestData loadData(String dataName) throws Exception {
    Document document = JDOMUtil.loadDocument(new File(myDataRoot + "/" + "data.xml"));

    PsiTestData data = createData();
    Element documentElement = document.getRootElement();

    final List nodes = documentElement.getChildren("data");

    for (int i = 0; i < nodes.size(); i++) {
      Element node = (Element)nodes.get(i);
      String value = node.getAttributeValue("name");

      if (value.equals(dataName)) {
        DefaultJDOMExternalizer.readExternal(data, node);
        data.loadText(myDataRoot);

        return data;
      }
    }

    throw new IllegalArgumentException("Cannot find data chunk '" + dataName + "'");
  }

  protected PsiTestData createData() {
    return new PsiTestData();
  }

  protected void checkResult(String dataName) throws Exception {
    myTestDataAfter = loadData(dataName);

    final String textExpected = myTestDataAfter.getText();
    final String actualText = myFile.getText();

    if (!textExpected.equals(actualText)) {
      System.out.println("Text mismatch: " + getName() + "(" + getClass().getName() + ")");
      System.out.println("Text expected:");
      printText(textExpected);
      System.out.println("Text found:");
      printText(actualText);

      assertTrue("text", false);
    }

//    assertEquals(myTestDataAfter.getText(), myFile.getText());
  }

  protected static void printText(String text) {
    final String q = "\"";
    System.out.print(q);

    text = StringUtil.convertLineSeparators(text, "\n");

    StringTokenizer tokenizer = new StringTokenizer(text, "\n", true);
    while (tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();

      if (token.equals("\n")) {
        System.out.print(q);
        System.out.println();
        System.out.print(q);
        continue;
      }

      System.out.print(token);
    }

    System.out.print(q);
    System.out.println();
  }

  protected void addLibraryToRoots(final VirtualFile jarFile, OrderRootType rootType) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(myModule);
    final ModifiableRootModel rootModel = manager.getModifiableModel();
    final Library jarLibrary = rootModel.getModuleLibraryTable().createLibrary();
    final Library.ModifiableModel libraryModel = jarLibrary.getModifiableModel();
    libraryModel.addRoot(jarFile, rootType);
    libraryModel.commit();
    rootModel.commit();
  }


  public PsiFile getFile() {
    return myFile;
  }

  public com.intellij.openapi.editor.Document getDocument(PsiFile file) {
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }
}
