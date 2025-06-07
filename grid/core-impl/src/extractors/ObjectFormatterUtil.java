package com.intellij.database.extractors;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.grid.editors.GridCellEditorHelper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.sql.Types;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class ObjectFormatterUtil {
  private static StringBuilder appendHex(StringBuilder sb, long value, int padTo) {
    String hex = Long.toHexString(value);
    for (int i = hex.length(); i < padTo; i++) {
      sb.append("0");
    }
    return sb.append(StringUtil.toUpperCase(hex));
  }

  public static @NotNull String toHexString(byte @NotNull [] bytes) {
    var sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      appendHex(sb, b & 0xFFL, 2);
    }
    return sb.toString();
  }

  private static void swapBytes(byte @NotNull [] bytes, int i, int j) {
    if (i >= bytes.length || i < 0 || j >= bytes.length || j < 0 || i == j) return;
    byte tmp = bytes[i];
    bytes[i] = bytes[j];
    bytes[j] = tmp;
  }

  private static void swapUUIDBytes(byte @NotNull [] bytes) {
    if (bytes.length != 16) return;
    swapBytes(bytes, 0, 4);
    swapBytes(bytes, 1, 5);
    swapBytes(bytes, 2, 6);
    swapBytes(bytes, 3, 7);
    swapBytes(bytes, 4, 6);
    swapBytes(bytes, 5, 7);
  }

  // Since the `swapUUIDBytes` is not an involution...
  private static void swapUUIDBytesReverse(byte @NotNull [] bytes) {
    if (bytes.length != 16) return;
    swapBytes(bytes, 5, 7);
    swapBytes(bytes, 4, 6);
    swapBytes(bytes, 3, 7);
    swapBytes(bytes, 2, 6);
    swapBytes(bytes, 1, 5);
    swapBytes(bytes, 0, 4);
  }

  private static boolean isNilUUID(UUID uuid) {
    return StringUtil.equals(uuid.toString(), "00000000-0000-0000-0000-000000000000");
  }

  @Contract("null->false")
  public static boolean isValidUUIDWithKnownVersion(@Nullable UUID uuid) {
    return uuid != null && ((1 <= uuid.version() && uuid.version() <= 5) || isNilUUID(uuid));
  }

  public static @Nullable UUID toUUID(byte @NotNull [] bytes, boolean swapFlag) {
    if (bytes.length != 16) return null;
    byte[] resultBytes = bytes.clone();
    if (swapFlag) {
      swapUUIDBytes(resultBytes);
    }
    ByteBuffer bb = ByteBuffer.wrap(resultBytes);
    long high = bb.getLong();
    long low = bb.getLong();
    return new UUID(high, low);
  }

  public static byte[] UUIDtoBytes(@NotNull UUID uuid, boolean swapFlag) {
    var bytes = ByteBuffer.allocate(16);
    bytes.putLong(uuid.getMostSignificantBits());
    bytes.putLong(uuid.getLeastSignificantBits());
    byte[] resultBytes = bytes.array();
    if (swapFlag) {
      swapUUIDBytesReverse(resultBytes);
    }
    return resultBytes;
  }

  private static void toPresentableHexString(@NotNull InputStream input, @NotNull StringBuilder sb) throws IOException {
    long i = 0;
    StringBuilder sb2 = new StringBuilder();
    boolean first = true;
    while (input.available() > 0) {
      if (first) first = false;
      else sb.append("\n");
      sb2.setLength(0);
      appendHex(sb, i * 16, 8).append("  ");
      for (int j = 0; j < 16; j++) {
        if (input.available() > 0) {
          int value = input.read();
          appendHex(sb, value, 2).append(" ");
          sb2.append(!Character.isISOControl(value) ? (char)value : '.');
        }
        else {
          for (; j < 16; j++) {
            sb.append("   ");
          }
        }
      }
      sb.append("   ").append(sb2);
      i++;
    }
    input.close();
  }

  private static @NotNull String toPresentableHexString(byte @NotNull [] bytes) {
    try {
      int expectedLength = (bytes.length / 16 + 1) * (4 * 16 + 14);
      var sb = new StringBuilder(expectedLength);
      toPresentableHexString(new ByteArrayInputStream(bytes), sb);
      return sb.toString();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String toTextString(byte @NotNull [] bytes) {
    TextInfo result = TextInfo.tryDetectString(bytes);
    if (result != null) return result.text;
    else return null;
  }

  public static @NotNull String toPresentableString(byte @NotNull [] bytes, @Nullable BinaryDisplayType displayType) {
    if (displayType == null) return "0x" + toHexString(bytes);
    String result;
    return switch (displayType) {
      case DETECT -> {
        if (bytes.length == 16) {
          result = toString(toUUID(bytes, false));
        }
        else {
          result = toTextString(bytes);
        }
        yield Objects.requireNonNullElseGet(result, () -> "0x" + toHexString(bytes));
      }
      case UUID -> {
        result = toString(toUUID(bytes, false));
        yield Objects.requireNonNullElseGet(result, () -> "0x" + toHexString(bytes));
      }
      case UUID_SWAP -> {
        result = toString(toUUID(bytes, true));
        yield Objects.requireNonNullElseGet(result, () -> "0x" + toHexString(bytes));
      }
      case TEXT -> {
        result = toTextString(bytes);
        yield Objects.requireNonNullElseGet(result, () -> "0x" + toHexString(bytes));
      }
      case HEX -> "0x" + toHexString(bytes);
      case HEX_ASCII -> toPresentableHexString(bytes);
    };
  }

  @Contract("null->null;!null->!null")
  private static @Nullable String toString(@Nullable UUID uuid) {
    return uuid == null ? null : uuid.toString().toLowerCase(Locale.ENGLISH);
  }

  public static boolean isBooleanColumn(@NotNull GridColumn column, int type) {
    String className = column instanceof JdbcColumnDescriptor ? ((JdbcColumnDescriptor)column).getJavaClassName() : null;
    return type == Types.BOOLEAN ||
           (isBit(column.getTypeName(), type) ||
            CommonClassNames.JAVA_LANG_BOOLEAN.equals(className)) && ((JdbcGridColumn)column).getSize() <= 1;
  }

  public static boolean isNumericCell(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return isNumericValue(GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column));
  }

  public static boolean isNumericValue(int type) {
    return ArrayUtil.contains(
      type,
      Types.INTEGER,
      Types.SMALLINT,
      Types.TINYINT,
      Types.BIGINT,
      Types.REAL,
      Types.FLOAT,
      Types.DOUBLE,
      Types.DECIMAL,
      Types.NUMERIC
    );
  }

  public static boolean isBinary(@Nullable GridColumn column, int type) {
    String typeName = column == null ? null : column.getTypeName();
    return type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY || type == Types.BLOB ||
           isBit(typeName, type) && (column == null || !isBooleanColumn(column, type)) ||
           typeName != null && StringUtil.containsIgnoreCase(typeName, "BINARY");
  }

  protected static boolean isBit(@Nullable String typeName, int jdbcType) {
    return jdbcType == Types.BIT ||
           typeName != null && StringUtil.findIgnoreCase(typeName, "bit", "bit varying", "varbit");
  }

  public static boolean isNumberType(int jdbcType) {
    boolean result = switch (jdbcType) {
      case Types.BIGINT, Types.DECIMAL, Types.DOUBLE, Types.FLOAT, Types.INTEGER, Types.NUMERIC -> true;
      default -> false;
    };
    return result;
  }

  public static boolean isStringType(int jdbcType) {
    return switch (jdbcType) {
      case Types.VARCHAR, Types.CHAR, Types.CLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.NCLOB -> true;
      default -> false;
    };
  }

  public static boolean isIntegerOrBigInt(int type) {
    return switch (type) {
      case Types.INTEGER, Types.BIGINT -> true;
      default -> false;
    };
  }
}
