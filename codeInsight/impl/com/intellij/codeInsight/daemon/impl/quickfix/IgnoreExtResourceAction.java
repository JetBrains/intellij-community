package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.psi.PsiFile;

/**
 * @author mike
 */
public class IgnoreExtResourceAction extends BaseExtResourceAction {
  protected String getQuickFixKeyId() {
    return "ignore.external.resource.text";
  }

  protected void doInvoke(final PsiFile file, final int offset, final String uri) {
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResource(uri);
  }
}
