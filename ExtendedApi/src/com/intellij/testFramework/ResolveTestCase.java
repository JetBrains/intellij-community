
package com.intellij.testFramework;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class ResolveTestCase extends PsiTestCase {
  @NonNls protected static final String MARKER = "<ref>";

  protected PsiReference configureByFile(@NonNls String filePath) throws Exception{
    return configureByFile(filePath, null);
  }
  
  protected PsiReference configureByFile(@NonNls String filePath, @Nullable VirtualFile parentDir) throws Exception{
    final String fullPath = getTestDataPath() + filePath;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", vFile);

    String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");

    final String fileName = vFile.getName();

    return configureByFileText(fileText, fileName, parentDir);
  }

  protected PsiReference configureByFileText(String fileText, String fileName) throws Exception {
    return configureByFileText(fileText, fileName, null);
  }
  
  protected PsiReference configureByFileText(String fileText, String fileName, @Nullable final VirtualFile parentDir) throws Exception {
    int offset = fileText.indexOf(MARKER);
    assertTrue(offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());

    myFile = parentDir == null? createFile(myModule, fileName, fileText) : createFile(myModule, parentDir, fileName, fileText);
    PsiReference ref = myFile.findReferenceAt(offset);

    assertNotNull(ref);

    return ref;
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath() + "/psi/resolve/";
  }
}