/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs.fileView;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ColumnInfo;

/**
 * author: lesya
 */
public abstract class DualViewColumnInfo<Item, Aspect> extends ColumnInfo<Item, Aspect>{
  public DualViewColumnInfo(String name) {
    super(name);
  }

  public abstract boolean shouldBeShownIsTheTree();

  public abstract boolean shouldBeShownIsTheTable();
}
