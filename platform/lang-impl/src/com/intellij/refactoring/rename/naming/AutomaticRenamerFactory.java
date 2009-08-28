package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface AutomaticRenamerFactory {
  ExtensionPointName<AutomaticRenamerFactory> EP_NAME = ExtensionPointName.create("com.intellij.automaticRenamerFactory");

  boolean isApplicable(PsiElement element);

  @Nullable
  String getOptionName();

  boolean isEnabled();
  void setEnabled(boolean enabled);

  AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages);
}
