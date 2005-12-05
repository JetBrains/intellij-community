package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInspection.LocalQuickFix;

/**
 * @author cdr
 */
public abstract class ReplaceWithFileTemplateFix implements LocalQuickFix {
  public String getName() {
    return "Reaplce with actual file template";
  }

  public String getFamilyName() {
    return getName();
  }
}
