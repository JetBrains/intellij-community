/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.speedSearch;

public interface ElementFilter<T> {

  boolean shouldBeShowing(T value);
  SpeedSearch getSpeedSearch();

}