package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.FoldRegion;

import javax.swing.*;
import java.awt.*;

public abstract class EditorGutterComponentEx extends JComponent implements EditorGutter {

  public abstract boolean isFoldingOutlineShown();

  public abstract boolean isLineMarkersShown();

  public abstract FoldRegion findFoldingAnchorAt(int x, int y);

  public abstract int getWhitespaceSeparatorOffset();

  public abstract Color getFoldingColor(boolean isActive);

  public abstract void revalidateMarkup();

  public abstract int getLineMarkerAreaOffset();

  public abstract int getIconsAreaWidth();
}
