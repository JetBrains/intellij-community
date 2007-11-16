package com.intellij.openapi.ui.popup;

import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public abstract class JBPopupAdapter implements JBPopupListener {
  public void beforeShown(final Project project, final JBPopup popup) {
  }

  public void onClosed(final JBPopup popup) {
  }
}