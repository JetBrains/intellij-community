package com.intellij.refactoring.changeClassSignature;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ChangeClassSigntaureViewDescriptor extends UsageViewDescriptorAdapter {
  private final PsiClass myClass;

  public ChangeClassSigntaureViewDescriptor(PsiClass aClass) {
    super();
    myClass = aClass;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[]{myClass};
  }

  public String getProcessedElementsHeader() {
    return StringUtil.capitalize(UsageViewUtil.getType(myClass));
  }
}
