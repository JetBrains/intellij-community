package com.intellij.openapi.ui.popup;

/**
 * @author yole
 */
public abstract class JBPopupAdapter implements JBPopupListener {
  public void beforeShown(LightweightWindowEvent event) {
  }

  public void onClosed(LightweightWindowEvent event) {
  }
}