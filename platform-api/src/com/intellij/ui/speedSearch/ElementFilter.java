/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.speedSearch;

import com.intellij.openapi.Disposable;

public interface ElementFilter<T> {

  boolean shouldBeShowing(T value);

  interface Active<T> extends ElementFilter<T>{
    void fireUpdate();
    void addListener(Listener listener, Disposable parent);
  }

  interface Listener {
    void update();
  }

}