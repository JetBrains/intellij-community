/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;

/**
 * @author peter
*/
public class SameProjectWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    Module elementModule = ModuleUtil.findModuleForPsiElement(element);
    return elementModule != null && elementModule.getProject() == location.getPositionModule().getProject();
  }
}
