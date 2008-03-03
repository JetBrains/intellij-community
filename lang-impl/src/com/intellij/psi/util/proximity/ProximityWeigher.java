/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import com.intellij.psi.PsiElement;
import com.intellij.psi.Weigher;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class ProximityWeigher extends Weigher<PsiElement, ProximityLocation> {

  public abstract Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location);
}
