/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import com.intellij.usages.impl.rules.UsageTypeProvider;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.lang.StdLanguages;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public class DomUsageTypeProvider implements UsageTypeProvider {
  @Nullable
  public UsageType getUsageType(PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (StdLanguages.XML.equals(psiFile.getLanguage()) && DomManager.getDomManager(element.getProject()).getFileElement((XmlFile)psiFile, DomElement.class) != null) {
      return DOM_USAGE_TYPE;
    }
    return null;
  }

  private static final UsageType DOM_USAGE_TYPE = new UsageType(DomBundle.message("dom.usage.type"));
}