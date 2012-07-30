package com.intellij.compiler;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author nik
 */
public class CompilerEncodingServiceTest extends PsiTestCase {
  private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

  public void testDefaultEncoding() {
    assertSameElements(getService().getAllModuleEncodings(myModule), getIdeDefault());
    assertEquals(getIdeDefault(), getService().getPreferredModuleEncoding(myModule));
  }

  public void testJavaFileEncoding() {
    assertSameElements(getService().getAllModuleEncodings(myModule), getIdeDefault());
    final VirtualFile file = createFile("A.java");
    assertSameElements(getService().getAllModuleEncodings(myModule), getIdeDefault());
    EncodingProjectManager.getInstance(myProject).setEncoding(file, WINDOWS_1251);

    assertSameElements(getService().getAllModuleEncodings(myModule), WINDOWS_1251);
  }

  public void testNonJavaFileEncoding() {
    final VirtualFile file = createFile("A.properties");
    EncodingProjectManager.getInstance(myProject).setEncoding(file, WINDOWS_1251);

    assertSameElements(getService().getAllModuleEncodings(myModule), getIdeDefault());
  }

  public void testTwoJavaFilesWithDifferentEncodings() {
    final VirtualFile fileA = createFile("A.java");
    final VirtualFile fileB = createFile("B.java");
    EncodingProjectManager.getInstance(myProject).setEncoding(fileA, WINDOWS_1251);
    EncodingProjectManager.getInstance(myProject).setEncoding(fileB, WINDOWS_1252);

    assertSameElements(getService().getAllModuleEncodings(myModule), WINDOWS_1251, WINDOWS_1252);
  }

  public void testJavaAndNonJavaFilesWithDifferentEncodings() {
    final VirtualFile fileA = createFile("A.java");
    final VirtualFile fileB = createFile("B.properties");
    EncodingProjectManager.getInstance(myProject).setEncoding(fileA, WINDOWS_1251);
    EncodingProjectManager.getInstance(myProject).setEncoding(fileB, WINDOWS_1252);

    assertSameElements(getService().getAllModuleEncodings(myModule), WINDOWS_1251);
  }

  public void testSourceRootEncodingDominatesOnFileEncoding() {
    final VirtualFile file = createFile("A.java");
    EncodingProjectManager.getInstance(myProject).setEncoding(file, WINDOWS_1251);
    assertSameElements(getService().getAllModuleEncodings(myModule), WINDOWS_1251);
    assertEquals(WINDOWS_1251, getService().getPreferredModuleEncoding(myModule));

    EncodingProjectManager.getInstance(myProject).setEncoding(file.getParent(), WINDOWS_1252);

    assertSameElements(getService().getAllModuleEncodings(myModule), WINDOWS_1251, WINDOWS_1252);
    assertEquals(WINDOWS_1252, getService().getPreferredModuleEncoding(myModule));
  }

  public void testUseContentRootEncodingIfEncodingForSourceRootIsNotSpecified() throws IOException {
    VirtualFile contentRoot = getVirtualFile(createTempDir("contentRoot"));
    PsiTestUtil.addContentRoot(myModule, contentRoot);
    VirtualFile srcDir = contentRoot.createChildDirectory(this, "src");
    PsiTestUtil.addSourceRoot(myModule, srcDir);
    EncodingProjectManager.getInstance(myProject).setEncoding(contentRoot, WINDOWS_1251);
    assertSameElements(getService().getAllModuleEncodings(myModule), WINDOWS_1251);
  }

  private VirtualFile createFile(final String fileName) {
    try {
      final VirtualFile file = createFile(fileName, "").getVirtualFile();
      assertNotNull(file);
      return file;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  private static Charset getIdeDefault() {
    return EncodingManager.getInstance().getDefaultCharset();
  }

  private CompilerEncodingService getService() {
    return CompilerEncodingService.getInstance(myProject);
  }
}
