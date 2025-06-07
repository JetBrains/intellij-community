// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.text.BlockSupport;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author maxim
 */
public abstract class AbstractReparseTestCase extends LightJavaCodeInsightFixtureTestCase {
  protected FileType myFileType;
  protected PsiFile myDummyFile;
  private int myInsertOffset;

  @Override
  protected void tearDown() throws Exception {
    myDummyFile = null;
    myFileType = null;
    super.tearDown();
  }

  protected void setFileType(final FileType fileType) {
    myFileType = fileType;
  }

  protected void insert(final @NonNls String s) throws IncorrectOperationException {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      String oldText = myDummyFile.getText();
      String expectedNewText = oldText.substring(0, myInsertOffset) + s + oldText.substring(myInsertOffset);

      doReparseAndCheck(s, expectedNewText, 0);
      myInsertOffset += s.length();
    }), "asd", null);
  }

  protected void moveEditPointRight(int count) {
    myInsertOffset += count;
  }

  protected void setEditPoint(int pos) {
    myInsertOffset = pos;
  }

  protected void remove(int count) throws IncorrectOperationException {
    String oldText = myDummyFile.getText();
    String expectedNewText = oldText.substring(0, myInsertOffset-count) + oldText.substring(myInsertOffset);

    doReparseAndCheck("", expectedNewText, count);
    myInsertOffset -= count;
  }

  private void doReparseAndCheck(final String s, final String expectedNewText, final int length) throws IncorrectOperationException {
    doReparse(s, length);
    String foundStructure = DebugUtil.treeToString(myDummyFile.getNode(), true);
    final PsiFile psiFile = createDummyFile(getName() + "." + myFileType.getDefaultExtension(), expectedNewText);
    String expectedStructure = DebugUtil.treeToString(psiFile.getNode(), true);
    assertEquals(expectedStructure, foundStructure);

    assertEquals("Reparse tree should be equal to the document", expectedNewText, myDummyFile.getText());
  }

  protected @NotNull PsiFile createDummyFile(@NotNull String fileName, @NotNull String text) throws IncorrectOperationException {
    FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, type, text);
  }

  protected void doReparse(final String s, final int length) {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      BlockSupport blockSupport = BlockSupport.getInstance(getProject());
      blockSupport.reparseRange(myDummyFile, myInsertOffset - length, myInsertOffset, s);
    }), "asd", null);
  }

  protected void prepareFile(@NonNls String prefix, @NonNls String suffix) throws IncorrectOperationException {
    myDummyFile = createDummyFile(getName() + "." + myFileType.getDefaultExtension(), prefix + suffix);
    myInsertOffset = prefix.length();
  }
}
