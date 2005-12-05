package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.InspectionsBundle;

/**
 * @author cdr
 */
public abstract class ReplaceWithFileTemplateFix implements LocalQuickFix {
  public String getName() {
    return InspectionsBundle.message("default.file.template.replace.with.actual.file.template");
  }

  public String getFamilyName() {
    return getName();
  }
}
