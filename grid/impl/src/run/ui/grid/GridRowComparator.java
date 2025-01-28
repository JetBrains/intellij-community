package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.GridUtilCore;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.sql.Types;
import java.util.Comparator;

public class GridRowComparator implements Comparator<GridRow> {
  protected final GridColumn myColumn;

  protected GridRowComparator(@NotNull GridColumn column) {
    myColumn = column;
  }

  @Override
  public int compare(GridRow row1, GridRow row2) {
    Object v1 = myColumn.getValue(row1);
    Object v2 = myColumn.getValue(row2);
    return compareObjects(v1, v2);
  }

  public int compareObjects(Object v1, Object v2) {
    if (v1 instanceof String && v2 instanceof String) {
      if (myColumn.getType() == Types.INTEGER) {
        try {
          int i1 = Integer.parseInt((String)v1);
          int i2 = Integer.parseInt((String)v2);
          return Integer.compare(i1, i2);
        }
        catch (NumberFormatException ignored) {
        }
      }
      else if (myColumn.getType() == Types.DOUBLE) {
        try {
          double i1 = Double.parseDouble((String)v1);
          double i2 = Double.parseDouble((String)v2);
          return Double.compare(i1, i2);
        }
        catch (NumberFormatException ignored) {
        }
      }
      else if (myColumn.getType() == Types.BIGINT) {
        try {
          BigInteger i1 = new BigInteger((String)v1);
          BigInteger i2 = new BigInteger((String)v2);
          return i1.compareTo(i2);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
    return compareValues(v1, v2);
  }

  public static @Nullable GridRowComparator create(@NotNull GridColumn column) {
    return GridUtilCore.isRowId(column) ? null : new GridRowComparator(column);
  }

  private static int compareValues(Object v1, Object v2) {
    // NULLs first
    if (v1 == v2) return 0;
    else if (v1 == null) return -1;
    else if (v2 == null) return 1;

    // Failed to load errors next
    if (GridUtil.isFailedToLoad(v1)) {
      return GridUtil.isFailedToLoad(v2) ? Comparing.compare((String)v1, (String)v2) : -1;
    }
    else if (GridUtil.isFailedToLoad(v2)) return 1;

    // Generic Comparable case and the rest
    if (v1 instanceof Comparable && v2 instanceof Comparable && v1.getClass() == v2.getClass()) {
      //noinspection unchecked
      return ((Comparable)v1).compareTo(v2);
    }
    else if (v1 instanceof String && v2 instanceof LobInfo.ClobInfo) {
      return -((LobInfo.ClobInfo)v2).compareTo((String)v1);
    }
    else if (v2 instanceof String && v1 instanceof LobInfo.ClobInfo) {
      return ((LobInfo.ClobInfo)v1).compareTo((String)v2);
    }
    else if (v1 instanceof Number && v2 instanceof Number) {
      // cheap CCE fix for the time being
      int result1 = Double.compare(((Number)v1).doubleValue(), ((Number)v2).doubleValue());
      long result2 = ((Number)v1).longValue() - ((Number)v2).longValue();
      if (result1 < 0 && result2 < 0) return -1;
      if (result1 > 0 && result2 > 0) return 1;
      return 0;
    }
    else if (v1 instanceof byte[] && v2 instanceof LobInfo.BlobInfo) {
      return -((LobInfo.BlobInfo)v2).compareTo((byte[])v1);
    }
    else if (v2 instanceof byte[] && v1 instanceof LobInfo.BlobInfo) {
      return ((LobInfo.BlobInfo)v1).compareTo((byte[])v2);
    }
    else if (v1 instanceof Object[] array1 && v2 instanceof Object[] array2) {
      int maxLength = Math.max(array1.length, array2.length);
      for (int i = 0; i < maxLength; i++) {
        int comparisonResult = compareValues(i < array1.length ? array1[i] : null, i < array2.length ? array2[i] : null);
        if (comparisonResult != 0) {
          return comparisonResult;
        }
      }
      return 0;
    }
    return v1.getClass().getCanonicalName().compareTo(v2.getClass().getCanonicalName());
  }
}
