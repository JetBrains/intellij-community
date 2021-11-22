// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

/**
 * Preferred action place on Run or Debug toolbar.
 */
public enum PreferredPlace {

  /**
   * Try to move action into the toolbar from the 'More' action group.
   */
  TOOLBAR,

  /**
   * Try to move action into the 'More' action group from the toolbar.
   */
  MORE_GROUP

}
