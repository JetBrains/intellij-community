package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.InspectionsBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public abstract class ReplaceWithFileTemplateFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return InspectionsBundle.message("default.file.template.replace.with.actual.file.template");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }
}
