// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.ui.components.breadcrumbs.Crumb;

/**
 * A breadcrumb that supports lazy tooltips.
 */
public interface LazyTooltipCrumb extends Crumb {
  /**
   * Returns `true` if `Crumb.getTooltip()` is slow and ready to be called in ReadAction on the pool thread.
   * After the first non-canceled call of the `Crumb.getTooltip()`, must return `false` to avoid postponed execution.
   */
  boolean needCalculateTooltip();
}
