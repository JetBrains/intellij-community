package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;

public interface ExpressionContext {
  @NonNls Key<String> SELECTION = Key.create("SELECTION");

  Project getProject();
  Editor getEditor();
  int getStartOffset();
  int getTemplateStartOffset();
  int getTemplateEndOffset();
  <T> T getProperty(Key<T> key);
}

