/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomDescriptionProvider;
import com.intellij.pom.PomNamedTarget;
import com.intellij.pom.PomTarget;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewNodeTextLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.util.xml.TypeNameManager;
import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DefaultPomTargetDescriptionProvider extends PomDescriptionProvider {
  public String getElementDescription(@NotNull PomTarget element, @NotNull ElementDescriptionLocation location) {
    if (element instanceof PsiElement) return null;
    
    if (location == UsageViewTypeLocation.INSTANCE) {
      return TypeNameManager.getTypeName(element.getClass());
    }
    if (location == UsageViewNodeTextLocation.INSTANCE) {
      return TypeNameManager.getTypeName(element.getClass()) + " " + StringUtil.notNullize(element instanceof PomNamedTarget ? ((PomNamedTarget)element).getName() : null, "''");
    }
    if (location instanceof HighlightUsagesDescriptionLocation) {
      return TypeNameManager.getTypeName(element.getClass());
    }
    return null;
  }
}
