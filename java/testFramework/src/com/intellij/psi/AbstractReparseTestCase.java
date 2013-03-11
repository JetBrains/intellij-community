package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.text.BlockSupport;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author maxim
 */
public abstract class AbstractReparseTestCase extends PsiTestCase {
  protected FileType myFileType;
  protected PsiFile myDummyFile;
  private int myInsertOffset;

  protected void setFileType(final FileType fileType) {
    myFileType = fileType;
  }

  protected void insert(@NonNls final String s) throws IncorrectOperationException {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            String oldText = myDummyFile.getText();
            String expectedNewText = oldText.substring(0, myInsertOffset) + s + oldText.substring(myInsertOffset);

            try {
              doReparseAndCheck(s, expectedNewText, 0);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
            myInsertOffset += s.length();
          }
        });
      }
    }, "asd", null);
  }

  protected void moveEditPointLeft(int count) {
    myInsertOffset -= count;
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
    String foundStructure = DebugUtil.treeToString(SourceTreeToPsiMap.psiElementToTree(myDummyFile), false);
    final PsiFile psiFile = createDummyFile(getName() + "." + myFileType.getDefaultExtension(), expectedNewText);
    String expectedStructure = DebugUtil.treeToString(SourceTreeToPsiMap.psiElementToTree(psiFile), false);
    if (!expectedStructure.equals(foundStructure)) {
      System.out.println("expected: ");
      System.out.println(expectedStructure);
      System.out.println("found: ");
      System.out.println(foundStructure);
      assertEquals(expectedStructure, foundStructure);
    }

    assertEquals("Reparse tree should be equal to the document", expectedNewText, myDummyFile.getText());
  }

  protected void doReparse(final String s, final int length) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            BlockSupport blockSupport = ServiceManager.getService(myProject, BlockSupport.class);
            try {
              blockSupport.reparseRange(myDummyFile, myInsertOffset - length, myInsertOffset, s);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, "asd", null);
  }

  protected void prepareFile(@NonNls String prefix, @NonNls String suffix) throws IncorrectOperationException {
    myDummyFile = createDummyFile(getName() + "." + myFileType.getDefaultExtension(), prefix + suffix);
    myInsertOffset = prefix.length();
  }
}
