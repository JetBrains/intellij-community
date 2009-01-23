package com.intellij.execution.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;

public class DisposedPsiManagerCheck {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.DisposedPsiManagerCheck");
  private final Throwable myAllocationPlace;
  private final Project myProject;

  public DisposedPsiManagerCheck(final Project project) {
    myProject = project;
    myAllocationPlace = new Throwable();
  }

  public void performCheck() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if (psiManager == null)
      log("Is null");
    else if (psiManager.isDisposed())
      log("Disposed");
  }

  private void log(@NonNls final String message) {
    LOG.error(message + "\n" + StringUtil.getThrowableText(myAllocationPlace));
  }
}
