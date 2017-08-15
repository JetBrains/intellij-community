package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileBasedIndex;

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
    //ApplicationManager.getApplication().invokeLater(() -> myFileBasedIndex.requestReindex(file), ModalityState.NON_MODAL);
  }
}
