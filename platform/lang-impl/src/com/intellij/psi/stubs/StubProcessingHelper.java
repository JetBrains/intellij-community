package com.intellij.psi.stubs;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.diagnostic.errordialog.Attachment;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Author: dmitrylomov
 */
public class StubProcessingHelper extends StubProcessingHelperBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubProcessingHelper");
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


  protected String stubTreeAndIndexDoNotMatch(StubTree stubTree,
                                            PsiFileWithStubSupport psiFile,
                                            List<StubElement<?>> plained,
                                            VirtualFile virtualFile,
                                            StubTree stubTreeFromIndex) {
    return LogMessageEx.createEvent("PSI and index do not match: PSI " + psiFile + ", first stub " + plained.get(0),
                                    "Please report the problem to JetBrains with the file attached",
                                    new Attachment(virtualFile != null ? virtualFile.getPath() : "vFile.txt",
                                                   psiFile.getText()), new Attachment("stubTree.txt",
                                                                                      ((PsiFileStubImpl)stubTree
                                                                                        .getRoot()).printTree()),
                                    new Attachment("stubTreeFromIndex.txt", stubTreeFromIndex == null
                                                                            ? "null"
                                                                            : ((PsiFileStubImpl)stubTreeFromIndex
                                                                              .getRoot()).printTree())).toString();
  }

}
