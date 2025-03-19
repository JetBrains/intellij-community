// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.errorTreeView;

import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ErrorTreeElement {
  public static final ErrorTreeElement[] EMPTY_ARRAY = new ErrorTreeElement[0];

  private ErrorTreeElementKind myKind;

  protected ErrorTreeElement() {
    this(ErrorTreeElementKind.GENERIC);
  }

  protected ErrorTreeElement(@NotNull ErrorTreeElementKind kind) {
    myKind = kind;
  }

  public @NotNull ErrorTreeElementKind getKind() {
    return myKind;
  }

  public void setKind(@NotNull ErrorTreeElementKind kind) {
    myKind = kind;
  }

  public abstract String[] getText();

  public abstract Object getData();

  @Override
  public final String toString() {
    String[] text = getText();
    return text != null && text.length > 0? text[0] : "";
  }

  public abstract String getExportTextPrefix();

  public @Nullable CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
    return null;
  }

  public @Nullable CustomizeColoredTreeCellRenderer getRightSelfRenderer() {
    return null;
  }
  
  public @Nullable Icon getIcon() {
    return getKind().getIcon();
  }
  
  public @NotNull @Nls String getPresentableText() {
    return getKind().getPresentableText();
  }
}
