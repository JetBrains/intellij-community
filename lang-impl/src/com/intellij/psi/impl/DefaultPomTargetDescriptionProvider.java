/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl;

import com.intellij.pom.PomDescriptionProvider;
import com.intellij.pom.PomTarget;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.util.xml.TypeNameManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DefaultPomTargetDescriptionProvider extends PomDescriptionProvider {
  public String getElementDescription(@NotNull PomTarget element, @NotNull ElementDescriptionLocation location) {
    if (location == UsageViewTypeLocation.INSTANCE) {
      return TypeNameManager.getTypeName(element.getClass());
    }
    return null;
  }
}
