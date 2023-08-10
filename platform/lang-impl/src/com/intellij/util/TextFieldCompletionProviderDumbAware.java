package com.intellij.util;

import com.intellij.openapi.project.DumbAware;

public abstract class TextFieldCompletionProviderDumbAware extends TextFieldCompletionProvider implements DumbAware {

  protected TextFieldCompletionProviderDumbAware() {
  }

  protected TextFieldCompletionProviderDumbAware(boolean caseInsensitivity) {
    super(caseInsensitivity);
  }
}
