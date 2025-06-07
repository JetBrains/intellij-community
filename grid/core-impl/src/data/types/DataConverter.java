package com.intellij.database.data.types;

import com.intellij.database.datagrid.AutoValueDescriptor.DelegateDescriptor;
import com.intellij.database.datagrid.DataConsumer;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.mutating.ColumnDescriptor;
import com.intellij.database.extractors.*;
import com.intellij.database.run.ui.grid.editors.DataGridFormattersUtilCore;
import com.intellij.database.run.ui.grid.editors.FormatsCache;
import com.intellij.database.run.ui.grid.editors.Formatter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Supplier;

public abstract class DataConverter<V, T> {
  private final PointSet<V> myStart;
  private final PointSet<T> myEnd;

  protected DataConverter(@NotNull PointSet<V> start, @NotNull PointSet<T> end) {
    myStart = start;
    myEnd = end;
  }

  public final @Nullable T convert(@Nullable V value) {
    return value == null ? null : notNull(value);
  }

  public final @Nullable V convertReverse(@Nullable T value) {
    return value == null ? null : reverseNotNull(value);
  }

  protected abstract @Nullable V reverseNotNull(@NotNull T value);

  protected abstract @Nullable T notNull(@NotNull V value);

  public @NotNull PointSet<V> getStart() {
    return myStart;
  }

  public @NotNull PointSet<T> getEnd() {
    return myEnd;
  }

  public static class BitStringToText extends DataConverter<String, String> {
    public BitStringToText() {
      super(PointSet.BIT_STRING, PointSet.TEXT);
    }

    @Override
    protected @Nullable String reverseNotNull(@NotNull String value) {
      return value;
    }

    @Override
    protected @Nullable String notNull(@NotNull String value) {
      return value;
    }
  }

  public static class BinaryTextToText extends DataConverter<String, String> {
    public BinaryTextToText() {
      super(PointSet.BINARY_STRING, PointSet.TEXT);
    }

    @Override
    protected @Nullable String reverseNotNull(@NotNull String text) {
      StringBuilder sb = new StringBuilder();
      for (char aChar : text.toCharArray()) {
        int intValue = aChar;
        for (int i = 0; i < 8; i++) {
          sb.append((intValue & 128) == 0 ? 0 : 1);
          intValue <<= 1;
        }
      }
      return sb.toString();
    }

    @Override
    protected @Nullable String notNull(@NotNull String value) {
      List<String> strings = split(value);
      if (strings.isEmpty()) return value;
      try {
        StringBuilder sb = new StringBuilder();
        for (String string : strings) {
          sb.append((char)Integer.parseInt(string, 2));
        }
        return sb.toString();
      }
      catch (Exception ignore) {
      }
      return value;
    }

    private static @NotNull List<String> split(@NotNull String s) {
      int currentIdx = 0;
      List<String> strings = new ArrayList<>();
      while (currentIdx < s.length()) {
        String substring = s.substring(currentIdx, Math.min(s.length(), currentIdx + 8));
        strings.add(substring);
        currentIdx += 8;
      }
      return strings;
    }
  }

  public static class BinaryToText extends DataConverter<byte[], String> {
    public BinaryToText() {
      super(PointSet.BINARY, PointSet.TEXT);
    }

    @Override
    protected @Nullable String notNull(byte @NotNull [] data) {
      TextInfo info = TextInfo.tryDetectString(data);
      return info == null ? StringUtil.toHexString(data) : info.text;
    }

    @Override
    protected byte @Nullable [] reverseNotNull(@NotNull String data) {
      return data.getBytes(StandardCharsets.UTF_8);
    }
  }

  public static class BooleanToBinary extends DataConverter<Boolean, byte[]> {
    public BooleanToBinary() {
      super(PointSet.BOOLEAN, PointSet.BINARY);
    }

    @Override
    protected byte @Nullable [] notNull(@NotNull Boolean data) {
      return new byte[]{data ? (byte)1 : 0};
    }

    @Override
    protected @Nullable Boolean reverseNotNull(byte @NotNull [] bytes) {
      return bytes[0] == 0 ? Boolean.FALSE : Boolean.TRUE;
    }
  }

  public static class BooleanToNumber extends DataConverter<Boolean, Number> {
    public BooleanToNumber() {
      super(PointSet.BOOLEAN, PointSet.NUMBER);
    }

    @Override
    protected @Nullable Number notNull(@NotNull Boolean data) {
      return data ? 1 : 0;
    }

    @Override
    protected @Nullable Boolean reverseNotNull(@NotNull Number data) {
      return Double.compare(data.doubleValue(), 1) >= 0;
    }
  }

  public static class BooleanToText extends DataConverter<Boolean, String> {
    public BooleanToText() {
      super(PointSet.BOOLEAN, PointSet.TEXT);
    }

    @Override
    protected @Nullable String notNull(@NotNull Boolean data) {
      return String.valueOf(data);
    }

    @Override
    protected @Nullable Boolean reverseNotNull(@NotNull String data) {
      return StringUtil.findIgnoreCase(StringUtil.trim(data), "yes", "true", "1");
    }
  }

  public static class BooleanNumberToText extends DataConverter<Number, String> {
    public BooleanNumberToText() {
      super(PointSet.BOOLEAN_NUMBER, PointSet.TEXT);
    }

    @Override
    protected @Nullable String notNull(@NotNull Number data) {
      return data instanceof Integer ? (Integer)data == 0 ? "false" : "true" :
             data instanceof Double ? Double.compare((Double)data, 0) == 0 ? "false" : "true" :
             "false";
    }

    @Override
    protected @Nullable Number reverseNotNull(@NotNull String data) {
      return StringUtil.equalsIgnoreCase(data, "false") ||
             StringUtil.equalsIgnoreCase(data, "0") ||
             StringUtil.isEmptyOrSpaces(data) ? 0 : 1;
    }
  }

  public static class DateToNumber extends DataConverter<Date, Number> {
    public DateToNumber() {
      super(PointSet.DATE, PointSet.NUMBER);
    }

    @Override
    protected @Nullable Number notNull(@NotNull Date data) {
      return data.getTime();
    }

    @Override
    protected @Nullable Date reverseNotNull(@NotNull Number data) {
      return new Date(data.longValue());
    }
  }

  public static class DateToText extends DataConverter<Date, String> {
    private final Formatter myFormatter;

    public DateToText(@NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
      super(PointSet.DATE, PointSet.TEXT);
      myFormatter = ConverterSupport.getDateFormatter(formatsCache, formatterCreator);
    }

    @Override
    protected @Nullable String notNull(@NotNull Date data) {
      return myFormatter.format(data);
    }

    @Override
    protected @Nullable Date reverseNotNull(@NotNull String data) {
      try {
        return (Date)myFormatter.parse(data);
      }
      catch (ParseException ignore) {
      }
      return null;
    }
  }

  public static class DateToTimestamp extends DataConverter<Date, TemporalAccessor> {
    public DateToTimestamp() {
      super(PointSet.DATE, PointSet.TEMPORAL_TIMESTAMP);
    }

    @Override
    protected @Nullable Date reverseNotNull(@NotNull TemporalAccessor data) {
      return java.sql.Date.valueOf(LocalDate.from(data));
    }

    @Override
    protected @Nullable TemporalAccessor notNull(@NotNull Date data) {
      return data instanceof java.sql.Date ?
             ((java.sql.Date)data).toLocalDate()
               .atStartOfDay()
               .atOffset(ZoneOffset.UTC)
               .atZoneSameInstant(ZoneId.systemDefault())
               .toLocalDateTime() :
             LocalDateTime.ofInstant(Instant.ofEpochMilli(data.getTime()), ZoneId.systemDefault());
    }
  }

  public static class NumberToText extends DataConverter<Number, String> {
    public NumberToText() {
      super(PointSet.NUMBER, PointSet.TEXT);
    }

    @Override
    protected @Nullable String notNull(@NotNull Number data) {
      return data.toString();
    }

    @Override
    protected @Nullable Number reverseNotNull(@NotNull String data) {
      try {
        return StringUtil.equalsIgnoreCase("true", data) ? 1 :
               StringUtil.equalsIgnoreCase("false", data) ? 0 :
               Long.parseLong(data);
      }
      catch (NumberFormatException ignore) {
      }
      try {
        return Double.parseDouble(data);
      }
      catch (NumberFormatException ignore) {
      }
      return null;
    }
  }

  public static class MoneyToText extends DataConverter<Number, String> {
    private static final NumberFormat FORMAT = NumberFormat.getNumberInstance(Locale.US);

    static {
      if (FORMAT instanceof DecimalFormat) ((DecimalFormat)FORMAT).setParseBigDecimal(true);
    }

    public MoneyToText() {
      super(PointSet.MONEY, PointSet.TEXT);
    }

    @Override
    protected @Nullable Number reverseNotNull(@NotNull String value) {
      if (value.isEmpty()) return null;
      try {
        return FORMAT.parse(value);
      }
      catch (Exception ignore) {
      }
      return parseCurrency(value);
    }

    @Override
    protected @Nullable String notNull(@NotNull Number value) {
      return value.toString();
    }

    private static @Nullable Number parseCurrency(@NotNull String value) {
      try {
        return FORMAT.parse(value.substring(1));
      }
      catch (Exception ignore) {
      }
      return null;
    }
  }

  public static class NumberToTimestamp extends DataConverter<Number, TemporalAccessor> {
    public NumberToTimestamp() {
      super(PointSet.NUMBER, PointSet.TEMPORAL_TIMESTAMP);
    }

    @Override
    protected @Nullable TemporalAccessor notNull(@NotNull Number data) {
      long l = data.longValue();
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.systemDefault());
    }

    @Override
    protected @Nullable Number reverseNotNull(@NotNull TemporalAccessor data) {
      return data instanceof LocalDateTime ?
             Timestamp.valueOf((LocalDateTime)data).getTime() :
             Timestamp.valueOf(((OffsetDateTime)data).toLocalDateTime()).getTime();
    }
  }

  public static class TimeToTemporal extends DataConverter<Time, TemporalAccessor> {
    public TimeToTemporal() {
      super(PointSet.TIME, PointSet.TEMPORAL_TIME);
    }

    @Override
    protected @Nullable Time reverseNotNull(@NotNull TemporalAccessor value) {
      return Time.valueOf(
        value instanceof OffsetTime ?
        ((OffsetTime)value).withOffsetSameInstant(DataGridFormattersUtilCore.getLocalTimeOffset()).toLocalTime() :
        ((LocalTime)value)
      );
    }

    @Override
    protected @Nullable TemporalAccessor notNull(@NotNull Time value) {
      return value.toLocalTime();
    }
  }

  public static class TimestampToTemporal extends DataConverter<Timestamp, TemporalAccessor> {
    private final FormatsCache myFormatsCache;
    private final FormatterCreator myFormatterCreator;

    public TimestampToTemporal(@NotNull FormatsCache formatsCache, @NotNull FormatterCreator formatterCreator) {
      super(PointSet.TIMESTAMP, PointSet.TEMPORAL_TIMESTAMP);
      myFormatsCache = formatsCache;
      myFormatterCreator = formatterCreator;
    }

    @Override
    protected @Nullable Timestamp reverseNotNull(@NotNull TemporalAccessor value) {
      return value instanceof OffsetDateTime ?
             DataGridFormattersUtilCore.fromOffsetDateTime((OffsetDateTime)value, myFormatsCache, myFormatterCreator, null) :
             Timestamp.valueOf((LocalDateTime)value);
    }

    @Override
    public @Nullable TemporalAccessor notNull(@NotNull Timestamp value) {
      return value.toLocalDateTime();
    }
  }

  public static class TimestampToText extends DataConverter<TemporalAccessor, String> {
    private final Formatter myFormatter;

    public TimestampToText(@NotNull FormatterCreator formatterCreator) {
      super(PointSet.TEMPORAL_TIMESTAMP, PointSet.TEXT);
      myFormatter = ConverterSupport.createTimestampFormatter(formatterCreator);
    }

    @Override
    protected @Nullable String notNull(@NotNull TemporalAccessor data) {
      return myFormatter.format(data);
    }

    @Override
    protected @Nullable TemporalAccessor reverseNotNull(@NotNull String data) {
      try {
        return (TemporalAccessor)myFormatter.parse(data);
      }
      catch (ParseException ignore){
      }
      return null;
    }
  }

  public static class TimeToNumber extends DataConverter<TemporalAccessor, Number> {

    public TimeToNumber() {
      super(PointSet.TEMPORAL_TIME, PointSet.NUMBER);
    }

    @Override
    protected @Nullable Number notNull(@NotNull TemporalAccessor data) {
      LocalTime from = LocalTime.from(data);
      return Time.valueOf(from).getTime();
    }

    @Override
    protected @Nullable TemporalAccessor reverseNotNull(@NotNull Number data) {
      return new Time(data.longValue()).toLocalTime();
    }
  }

  public static class TimeToText extends DataConverter<TemporalAccessor, String> {
    private final Formatter myFormatter;

    public TimeToText(@NotNull FormatterCreator formatterCreator) {
      super(PointSet.TEMPORAL_TIME, PointSet.TEXT);
      myFormatter = ConverterSupport.getTimeFormatter(formatterCreator);
    }

    @Override
    protected @Nullable String notNull(@NotNull TemporalAccessor data) {
      return myFormatter.format(data);
    }

    @Override
    protected @Nullable TemporalAccessor reverseNotNull(@NotNull String data) {
      try {
        return (TemporalAccessor)myFormatter.parse(data);
      }
      catch (ParseException ignore) {
      }
      return null;
    }
  }

  public static class TemporalTimeToTemporalTimestamp extends DataConverter<TemporalAccessor, TemporalAccessor> {

    public TemporalTimeToTemporalTimestamp() {
      super(PointSet.TEMPORAL_TIME, PointSet.TEMPORAL_TIMESTAMP);
    }

    @Override
    protected @Nullable TemporalAccessor notNull(@NotNull TemporalAccessor data) {
      return data instanceof OffsetTime ?
             ((OffsetTime)data).atDate(DataGridFormattersUtilCore.START_DATE) :
             ((LocalTime)data).atOffset(DataGridFormattersUtilCore.getLocalTimeOffset())
               .atDate(DataGridFormattersUtilCore.START_DATE)
               .atZoneSameInstant(ZoneId.systemDefault())
               .toLocalDateTime();
    }

    @Override
    protected @Nullable TemporalAccessor reverseNotNull(@NotNull TemporalAccessor data) {
      return data instanceof OffsetDateTime ?
             ((OffsetDateTime)data).toOffsetTime() :
             ((LocalDateTime)data).atZone(ZoneId.systemDefault()).toOffsetDateTime()
               .withOffsetSameInstant(DataGridFormattersUtilCore.getLocalTimeOffset())
               .toLocalTime();
    }
  }

  public static class MapToText extends DataConverter<Map, String> {
    private final @NotNull Supplier<ObjectFormatter> myObjectFormatter;

    protected MapToText(@NotNull Supplier<ObjectFormatter> objectFormatter) {
      super(PointSet.MAP, PointSet.TEXT);
      myObjectFormatter = objectFormatter;
    }

    @Override
    protected @Nullable Map reverseNotNull(@NotNull String value) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected @Nullable String notNull(@NotNull Map value) {
      return JsonUtilKt.toJson(value, myObjectFormatter.get(), ObjectFormatterMode.JSON, false, false, false);
    }
  }

  public static class ObjectToText extends DataConverter<Object, String> {
    private final @NotNull Supplier<ObjectFormatter> myObjectFormatter;

    protected ObjectToText(@NotNull Supplier<ObjectFormatter> objectFormatter) {
      super(PointSet.UNKNOWN, PointSet.TEXT);
      myObjectFormatter = objectFormatter;
    }

    @Override
    protected @Nullable Object reverseNotNull(@NotNull String value) {
      return null;
    }

    @Override
    protected @Nullable String notNull(@NotNull Object value) {
      return value instanceof ContextDescriptor ? convertToString((ContextDescriptor)value, myObjectFormatter.get()) :
             value instanceof String ? ((String)value) :
             null;
    }

    private static @Nullable String convertToString(@NotNull ContextDescriptor cd, @NotNull ObjectFormatter objectFormatter) {
      GridColumn column = getColumn(cd.myDescriptor);
      return objectFormatter.objectToString(cd.myValue, column, new DatabaseObjectFormatterConfig(ObjectFormatterMode.DEFAULT));
    }

    public static @NotNull Function<Object, Object> tweak(@NotNull Function<Object, Object> function, @NotNull ColumnDescriptor descriptor) {
      return o -> function.fun(new ContextDescriptor(descriptor, o));
    }

    private static @Nullable GridColumn getColumn(@NotNull ColumnDescriptor cd) {
      return cd instanceof DataConsumer.Column c ? c :
             cd instanceof DelegateDescriptor dd ? getColumn(dd.getDelegate()) :
             cd instanceof GridColumn gc ? gc :
             null;
    }

    private static class ContextDescriptor {
      private final ColumnDescriptor myDescriptor;
      private final Object myValue;

      ContextDescriptor(@NotNull ColumnDescriptor descriptor, Object value) {
        myDescriptor = descriptor;
        myValue = value;
      }
    }
  }

  public static class StringUuidToText extends DataConverter<String, String> {

    protected StringUuidToText() {
      super(PointSet.UUID_TEXT, PointSet.TEXT);
    }

    @Override
    protected @Nullable String reverseNotNull(@NotNull String value) {
      try {
        return UUID.fromString(value).toString();
      }
      catch (Exception ignore) {
      }
      return UUID.randomUUID().toString();
    }

    @Override
    protected @Nullable String notNull(@NotNull String value) {
      return value;
    }
  }

  public static class UuidToText extends DataConverter<UUID, String> {

    protected UuidToText() {
      super(PointSet.UUID, PointSet.TEXT);
    }

    @Override
    protected @Nullable UUID reverseNotNull(@NotNull String value) {
      try {
        return UUID.fromString(value);
      }
      catch (Exception ignore) {
      }
      return UUID.randomUUID();
    }

    @Override
    protected @Nullable String notNull(@NotNull UUID value) {
      return value.toString();
    }
  }
}
