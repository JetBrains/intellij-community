/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.statistics;

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class Statistician<T,Loc> {
  @Nullable
  public abstract StatisticsInfo serialize(T element, Loc location);
}
