/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;

public interface DiffPanel extends DiffViewer {

  void setTitle1(String title);
  void setTitle2(String title);
  void setContents(DiffContent content1, DiffContent content2);

  boolean hasDifferences();

  void dispose();
}
