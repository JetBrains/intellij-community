// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView;

import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ErrorTreeElement {
  public static final ErrorTreeElement[] EMPTY_ARRAY = new ErrorTreeElement[0];

  private final ErrorTreeElementKind myKind;

  protected ErrorTreeElement() {
    this(ErrorTreeElementKind.GENERIC);
  }

  protected ErrorTreeElement(@NotNull ErrorTreeElementKind kind) {
    myKind = kind;
  }

  @NotNull
  public ErrorTreeElementKind getKind() {
    return myKind;
  }

  public abstract String[] getText();

  public abstract Object getData();

  @Override
  public final String toString() {
    String[] text = getText();
    return text != null && text.length > 0? text[0] : "";
  }

  public abstract String getExportTextPrefix();

  @Nullable
  public CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
    return null;
  }

  @Nullable
  public CustomizeColoredTreeCellRenderer getRightSelfRenderer() {
    return null;
  }
}
