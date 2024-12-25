// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaResolveTestCase extends JavaPsiTestCase {
  protected static final String MARKER = "<ref>";

  private Document myDocument;

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myDocument != null) {
        FileDocumentManager.getInstance().reloadFromDisk(myDocument);
        myDocument = null;
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected PsiReference configureByFile(@NotNull String filePath) throws Exception {
    return configureByFile(filePath, null);
  }

  protected PsiReference configureByFile(@TestDataFile @NotNull String filePath, @Nullable VirtualFile parentDir) throws Exception {
    final VirtualFile vFile = VfsTestUtil.findFileByCaseSensitivePath(getTestDataPath() + filePath);
    assertNotNull("file " + filePath + " not found", vFile);

    String fileText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile));
    return configureByFileText(fileText, vFile.getName(), parentDir);
  }

  protected PsiReference configureByFileText(String fileText, String fileName) throws Exception {
    return configureByFileText(fileText, fileName, null);
  }

  protected PsiReference configureByFileText(String fileText, String fileName, @Nullable VirtualFile parentDir) throws Exception {
    int offset = fileText.indexOf(MARKER);
    assertTrue(String.format("Expected to find %s marker in file but was none", MARKER), offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());

    if (parentDir == null) {
      myFile = createFile(myModule, fileName, fileText);
    }
    else {
      VirtualFile existing = parentDir.findChild(fileName);
      if (existing != null) {
        myDocument = FileDocumentManager.getInstance().getDocument(existing);
        assertNotNull(myDocument);
        final String finalFileText = fileText;
        ApplicationManager.getApplication().runWriteAction(() -> myDocument.setText(finalFileText));

        myFile = PsiManager.getInstance(getProject()).findFile(existing);
        assertNotNull(myFile);
        assertEquals(fileText, myFile.getText());
      }
      else {
        myFile = createFile(myModule, parentDir, fileName, fileText);
      }
    }

    PsiReference ref = myFile.findReferenceAt(offset);
    assertNotNull(ref);
    return ref;
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathManagerEx.getTestDataPath() + "/psi/resolve/";
  }
}