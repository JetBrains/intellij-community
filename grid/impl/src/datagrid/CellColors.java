package com.intellij.database.datagrid;

import com.intellij.database.diff.TableDiffColors;
import com.intellij.database.run.ui.grid.CellAttributesKey;

public final class CellColors {
  private CellColors() {
  }

  public static final CellAttributesKey INSERT = new CellAttributesKey(TableDiffColors.TDIFF_INSERTED, false);
  public static final CellAttributesKey INSERT_UNDERLINED = new CellAttributesKey(TableDiffColors.TDIFF_INSERTED, true);
  public static final CellAttributesKey REMOVE = new CellAttributesKey(TableDiffColors.TDIFF_DELETED, false);
  public static final CellAttributesKey REMOVE_UNDERLINED = new CellAttributesKey(TableDiffColors.TDIFF_DELETED, true);
  public static final CellAttributesKey REPLACE = new CellAttributesKey(TableDiffColors.TDIFF_MODIFIED, false);
  public static final CellAttributesKey REPLACE_UNDERLINED = new CellAttributesKey(TableDiffColors.TDIFF_MODIFIED, true);
  public static final CellAttributesKey FM_MATCHED = new CellAttributesKey(TableDiffColors.TDIFF_FUZZY_MATCHED, false);
  public static final CellAttributesKey FM_MISMATCHED = new CellAttributesKey(TableDiffColors.TDIFF_FUZZY_MISMATCHED, false);
  public static final CellAttributesKey EXCLUDE_COLUMN = new CellAttributesKey(TableDiffColors.TDIFF_EXCLUDED_COLUMN, false);
}
