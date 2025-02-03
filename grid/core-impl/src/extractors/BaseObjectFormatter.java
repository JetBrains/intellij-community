package com.intellij.database.extractors;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.JdbcColumnDescriptor;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.grid.editors.FormatsCache;
import com.intellij.database.run.ui.grid.editors.Formatter;
import com.intellij.database.util.LobInfoHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ClassMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.database.run.ui.grid.editors.FormatsCache.*;

public class BaseObjectFormatter implements ObjectFormatter {
  private static final Logger LOG = Logger.getInstance(BaseObjectFormatter.class);

  protected final MyMap<String> myToString = new MyMap<>();
  public final FormatsCache myFormatsCache;
  public final FormatterCreator myFormatterCreator;

  public BaseObjectFormatter() {
    this(new FormatsCache(), new FormatterCreator());
  }

  public BaseObjectFormatter(@NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
    myFormatsCache = formatsCache;
    myFormatterCreator = formatterCreator;
  }

  {
    myToString.register(String.class, new Converter<>() {
      @Override
      public String convert(String o, GridColumn column, ObjectFormatterConfig config) {
        return o;
      }
    });
    myToString.register(Object[].class, new Converter<>() {
      @Override
      public String convert(Object[] o, GridColumn column, ObjectFormatterConfig config) {
        return arrayToString(o, column, config);
      }
    });
    myToString.register(char[].class, new Converter<>() {
      @Override
      public String convert(char[] o, GridColumn column, ObjectFormatterConfig config) {
        return new String(o);
      }
    });
    myToString.register(Timestamp.class, new Converter<>() {
      @Override
      public String convert(Timestamp o, GridColumn column, ObjectFormatterConfig config) {
        return myFormatsCache.get(getTimestampFormatProvider(column, config), myFormatterCreator, cacheColumnFormats()).format(o);
      }
    });
    myToString.register(Time.class, new Converter<>() {
      @Override
      public String convert(Time o, GridColumn column, ObjectFormatterConfig config) {
        return myFormatsCache.get(getTimeFormatProvider(column, config), myFormatterCreator, cacheColumnFormats()).format(o);
      }
    });
    myToString.register(LocalTime.class, new Converter<>() {
      @Override
      public String convert(LocalTime o, GridColumn column, ObjectFormatterConfig config) {
        return myFormatsCache.get(getTimeFormatProvider(column, config), myFormatterCreator, cacheColumnFormats()).format(o);
      }
    });
    myToString.register(Date.class, new Converter<>() {
      @Override
      public String convert(Date o, GridColumn column, ObjectFormatterConfig config) {
        return dateToString(o, column, config);
      }
    });
    myToString.register(Number.class, new Converter<>() {
      @Override
      public String convert(Number o, GridColumn column, ObjectFormatterConfig config) {
        return numberToString(o, column, config);
      }
    });
    myToString.register(BigInteger.class, new Converter<>() {
      @Override
      public String convert(BigInteger o, GridColumn column, ObjectFormatterConfig config) {
        return myFormatsCache.get(getLongFormatProvider(config), myFormatterCreator).format(o);
      }
    });
    myToString.register(Map.class, new Converter<>() {
      @Override
      public String convert(Map o, GridColumn column, ObjectFormatterConfig config) {
        ObjectFormatterMode itemsMode = config.getMode() == ObjectFormatterMode.DISPLAY || config.getMode() == ObjectFormatterMode.JS_SCRIPT
                                        ? ObjectFormatterMode.JS_SCRIPT
                                        : ObjectFormatterMode.JSON;
        //noinspection unchecked
        return JsonUtilKt.toJson((Map<Object, Object>)o, BaseObjectFormatter.this, itemsMode, false, true, false);
      }
    });
    myToString.register(List.class, new Converter<>() {
      @Override
      public String convert(List o, GridColumn column, ObjectFormatterConfig config) {
        ObjectFormatterMode itemsMode = config.getMode() == ObjectFormatterMode.DISPLAY || config.getMode() == ObjectFormatterMode.JS_SCRIPT
                                        ? ObjectFormatterMode.JS_SCRIPT
                                        : ObjectFormatterMode.JSON;
        //noinspection unchecked
        return JsonUtilKt.toJson((List<Object>)o, BaseObjectFormatter.this, itemsMode, false, false, false);
      }
    });
    myToString.register(Boolean.class, new Converter<>() {
      @Override
      public String convert(Boolean o, GridColumn column, ObjectFormatterConfig config) {
        return String.valueOf(o);
      }
    });
    myToString.register(UUID.class, new Converter<>() {
      @Override
      public String convert(UUID o, GridColumn column, ObjectFormatterConfig config) {
        return o.toString();
      }
    });
    myToString.register(ReservedCellValue.class, new Converter<>() {
      @Override
      public String convert(ReservedCellValue o, GridColumn column, ObjectFormatterConfig config) {
        return reservedCellValueToString(o, config);
      }

      @Override
      public boolean nullValueIndicatesFailedConversion() {
        return false;
      }
    });
  }

  protected @Nullable String reservedCellValueToString(@Nullable ReservedCellValue o, ObjectFormatterConfig config) {
    return o == null || (config.getMode() != ObjectFormatterMode.DISPLAY && (o == ReservedCellValue.NULL || o == ReservedCellValue.UNSET))
           ? null
           : config.getMode() == ObjectFormatterMode.DISPLAY
             ? o.getDisplayName()
             : StringUtil.toLowerCase(String.valueOf(o));
  }

  protected @NotNull String numberToString(@NotNull Number o, GridColumn column, @NotNull ObjectFormatterConfig config) {
    return myFormatsCache.get(getDecimalFormatProvider(column, config), myFormatterCreator, cacheColumnFormats()).format(o);
  }

  protected @NotNull String dateToString(Date o, GridColumn column, @NotNull ObjectFormatterConfig config) {
    String className = column instanceof JdbcColumnDescriptor ? ((JdbcColumnDescriptor)column).getJavaClassName() : null;
    Formatter format = className == null
                       ? myFormatsCache.get(getDateFormatProvider(column, config), myFormatterCreator, cacheColumnFormats())
                       : className.endsWith("Timestamp")
                         ? myFormatsCache.get(getTimestampFormatProvider(column, config), myFormatterCreator, cacheColumnFormats())
                         : className.endsWith("Time")
                           ? myFormatsCache.get(getTimeFormatProvider(column, config), myFormatterCreator)
                           : myFormatsCache.get(getDateFormatProvider(column, config), myFormatterCreator, cacheColumnFormats());
    return format.format(o);
  }

  protected boolean cacheColumnFormats() {
    return true;
  }

  @Override
  public @Nullable @NonNls String objectToString(@Nullable Object o, GridColumn column, @NotNull ObjectFormatterConfig config) {
    if (o == null) return null;
    Converter<Object, String> converter = myToString.get(o.getClass());
    String result = null;
    try {
      result = converter != null ? converter.convert(o, column, config) : null;
    } catch (Exception e) {
      LOG.warn(e);
    }
    result = result != null || converter != null && !converter.nullValueIndicatesFailedConversion()
             ? result
             : Objects.requireNonNull(objectToString(String.valueOf(o), column, config));
    return result;
  }

  @Override
  public boolean isStringLiteral(@Nullable GridColumn column, @Nullable Object value, @NotNull ObjectFormatterMode mode) {
    if (mode == ObjectFormatterMode.JSON) {
      return !(value instanceof Number || value instanceof Boolean || value instanceof Map || value instanceof List);
    }

    return value instanceof String;
  }

  @Override
  public @NotNull String getStringLiteral(@NotNull String value, GridColumn column, @NotNull ObjectFormatterMode mode) {
    return "'" + StringUtil.escapeChars(value, '\'', '\\') + "'";
  }

  protected @NotNull <T> String arrayToString(T[] o, GridColumn column, ObjectFormatterConfig config) {
    if (o.length == 0) {
      return "{}";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    int len = Math.min(o.length, config.getMode() == ObjectFormatterMode.DISPLAY ? LobInfoHelper.MAX_ARRAY_SIZE : o.length);
    DatabaseObjectFormatterConfig.DatabaseDisplayObjectFormatterConfig itemConfig =
      new DatabaseObjectFormatterConfig.DatabaseDisplayObjectFormatterConfig(
        null, false, null, config.getSettings()
      );
    for (int i = 0; i < len; i++) {
      if (i > 0) sb.append(",");
      appendArrayItem(sb, o[i], column, itemConfig);
    }
    if (len < o.length) sb.append(",...");
    sb.append("}");
    return sb.toString();
  }

  protected <T> void appendArrayItem(StringBuilder sb, T o, GridColumn column, ObjectFormatterConfig config) {
    sb.append(objectToString(o, column, config));
  }

  public interface Converter<X, V> {
    V convert(X o, GridColumn column, ObjectFormatterConfig config);
    default boolean nullValueIndicatesFailedConversion() {
      return true;
    }
  }

  protected static final class MyMap<T> extends ClassMap<Converter<Object, T>> {
    private MyMap() {
      super(new ConcurrentHashMap<>());
    }

    public <X> void register(@NotNull Class<X> aClass, Converter<X, T> value) {
      //noinspection unchecked
      super.put(aClass, (Converter<Object, T>)value);
    }
  }
}
