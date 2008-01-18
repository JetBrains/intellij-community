package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;

/**
 * @author yole
 */
public interface TemplateOptionalProcessor {
  ExtensionPointName<TemplateOptionalProcessor> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateOptionalProcessor");

  void processText(final Project project, final Template template, final Document document, final RangeMarker templateRange);
  @Nls
  String getOptionName();

  boolean isEnabled(final Template template);
  void setEnabled(Template template, boolean value);
}
