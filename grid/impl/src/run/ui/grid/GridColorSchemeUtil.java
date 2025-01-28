package com.intellij.database.run.ui.grid;

import com.intellij.database.editor.DataGridColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class GridColorSchemeUtil {
  public static Color doGetGridColor(@NotNull GridColorsScheme scheme) {
    return ObjectUtils.chooseNotNull(scheme.getColor(EditorColors.INDENT_GUIDE_COLOR), UIUtil.getTableGridColor());
  }

  public static Color doGetForeground(@NotNull GridColorsScheme scheme) {
    return ObjectUtils.chooseNotNull(scheme.getDefaultForeground(), UIUtil.getTableForeground());
  }

  public static Color doGetBackground(@NotNull GridColorsScheme scheme) {
    return ObjectUtils.chooseNotNull(scheme.getDefaultBackground(), UIUtil.getTableBackground());
  }

  public static @NotNull Color doGetSelectionForeground(@NotNull GridColorsScheme scheme) {
    return ObjectUtils.chooseNotNull(scheme.getColor(DataGridColors.GRID_SELECTION_FOREGROUND), UIUtil.getTableSelectionForeground());
  }

  public static @NotNull Color doGetSelectionBackground(@NotNull GridColorsScheme scheme) {
    return ObjectUtils.chooseNotNull(scheme.getColor(DataGridColors.GRID_SELECTION_BACKGROUND),
                                     UIUtil.getTableSelectionBackground(true));
  }

  public static void setUpTableColors(@NotNull JTable table, @NotNull GridColorsScheme scheme) {
    table.setBackground(doGetBackground(scheme));
    table.setForeground(doGetForeground(scheme));
    table.setGridColor(doGetGridColor(scheme));
    table.setSelectionForeground(doGetSelectionForeground(scheme));
    table.setSelectionBackground(doGetSelectionBackground(scheme));
  }
}
