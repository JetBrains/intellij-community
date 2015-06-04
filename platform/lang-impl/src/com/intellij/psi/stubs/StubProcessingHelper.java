package com.intellij.psi.stubs;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

/**
 * Author: dmitrylomov
 */
public class StubProcessingHelper extends StubProcessingHelperBase {
  private final FileBasedIndex myFileBasedIndex;

  public StubProcessingHelper(FileBasedIndex fileBasedIndex) {
    myFileBasedIndex = fileBasedIndex;
  }

  @Override
  protected void onInternalError(final VirtualFile file) {
    // requestReindex() may want to acquire write lock (for indices not requiring content loading)
    // thus, because here we are under read lock, need to use invoke later
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myFileBasedIndex.requestReindex(file);
      }
    }, ModalityState.NON_MODAL);
  }


  @Override
  protected Object stubTreeAndIndexDoNotMatch(@NotNull ObjectStubTree stubTree, PsiFileWithStubSupport psiFile) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    StubTree stubTreeFromIndex = (StubTree)StubTreeLoader.getInstance().readFromVFile(psiFile.getProject(), virtualFile);
    String details = "Please report the problem to JetBrains with the file attached";
    details += StubTreeLoader.getInstance().getStubAstMismatchDiagnostics(virtualFile, psiFile, stubTree, null);
    details += "\n" + DebugUtil.currentStackTrace();
    String fileText = psiFile instanceof PsiCompiledElement ? "compiled" : psiFile.getText();
    return LogMessageEx.createEvent("PSI and index do not match",
                                    details,
                                    new Attachment(virtualFile.getPath() + "_file.txt", fileText),
                                    new Attachment("stubTree.txt", ((PsiFileStubImpl)stubTree.getRoot()).printTree()),
                                    new Attachment("stubTreeFromIndex.txt", stubTreeFromIndex == null
                                                                            ? "null"
                                                                            : ((PsiFileStubImpl)stubTreeFromIndex.getRoot()).printTree()));
  }
}
