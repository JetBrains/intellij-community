package com.intellij.openapi.vcs.rollback;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * @author yole
 */
public abstract class DefaultRollbackEnvironment implements RollbackEnvironment {
  public String getRollbackOperationName() {
    return VcsBundle.message("changes.action.rollback.text");
  }

  public List<VcsException> rollbackModifiedWithoutCheckout(final List<VirtualFile> files) {
    throw new UnsupportedOperationException();
  }

  public void rollbackIfUnchanged(final VirtualFile file) {
  }
}