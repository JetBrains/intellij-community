package com.intellij.database.data.types;

import com.intellij.database.remote.jdbc.GeoWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public final class ConversionPoint<T> {
  public static final ConversionPoint<Number> BIG_DECIMAL = new ConversionPoint<>(
    "BIG_DECIMAL",
    LogicalType.FIXED_POINT_NUMBER,
    Number.class
  );
  public static final ConversionPoint<Number> MONEY = new ConversionPoint<>("MONEY", LogicalType.MONEY, Number.class);
  public static final ConversionPoint<Timestamp> TIMESTAMP = new ConversionPoint<>("TIMESTAMP", LogicalType.TIMESTAMP, Timestamp.class);
  public static final ConversionPoint<TemporalAccessor> TEMPORAL_TIMESTAMP = new ConversionPoint<>(
    "TEMPORAL_TIMESTAMP",
    LogicalType.TIMESTAMP,
    TemporalAccessor.class
  );
  public static final ConversionPoint<TemporalAccessor> TEMPORAL_TIME = new ConversionPoint<>(
    "TEMPORAL_TIME",
    LogicalType.TIME,
    TemporalAccessor.class
  );
  public static final ConversionPoint<String> BINARY_STRING = new ConversionPoint<>(
    "BINARY STRING",
    LogicalType.BINARY_STRING,
    String.class
  );
  public static final ConversionPoint<String> BIT_STRING = new ConversionPoint<>(
    "BIT STRING",
    LogicalType.BINARY_STRING,
    String.class
  );
  public static final ConversionPoint<Number> DOUBLE_PRECISION = new ConversionPoint<>(
    "DOUBLE_PRECISION",
    LogicalType.DOUBLE_PRECISION,
    Number.class
  );
  public static final ConversionPoint<Number> SINGLE_PRECISION = new ConversionPoint<>(
    "SINGLE_PRECISION",
    LogicalType.SINGLE_PRECISION,
    Number.class
  );
  public static final ConversionPoint<String> TEXT = new ConversionPoint<>("TEXT", LogicalType.TEXT, String.class);
  public static final ConversionPoint<String> TEXT_DATE = new ConversionPoint<>("TEXT_DATE", LogicalType.DATE, String.class);
  public static final ConversionPoint<String> TEXT_TIMESTAMP = new ConversionPoint<>("TEXT_TIMESTAMP", LogicalType.TIMESTAMP, String.class);
  public static final ConversionPoint<String> NTEXT = new ConversionPoint<>("NTEXT", LogicalType.NTEXT, String.class);
  public static final ConversionPoint<String> BLOB_TEXT = new ConversionPoint<>("BLOB_TEXT", LogicalType.BLOB, String.class);
  public static final ConversionPoint<String> TEXT_ID = new ConversionPoint<>("TEXT_ID", LogicalType.TEXT_ID, String.class);
  public static final ConversionPoint<String> CLOB = new ConversionPoint<>("CLOB", LogicalType.CLOB, String.class);
  public static final ConversionPoint<String> CHAR = new ConversionPoint<>("CHAR", LogicalType.CHAR, String.class);
  public static final ConversionPoint<String> NCHAR = new ConversionPoint<>("NCHAR", LogicalType.NCHAR, String.class);
  public static final ConversionPoint<String> VARCHAR = new ConversionPoint<>("VARCHAR", LogicalType.VARCHAR, String.class);
  public static final ConversionPoint<String> NVARCHAR = new ConversionPoint<>("NVARCHAR", LogicalType.NVARCHAR, String.class);
  public static final ConversionPoint<byte[]> BINARY = new ConversionPoint<>("BINARY", LogicalType.BINARY, byte[].class);
  public static final ConversionPoint<byte[]> BINARY_ID = new ConversionPoint<>("BINARY_ID", LogicalType.BINARY_ID, byte[].class);
  public static final ConversionPoint<byte[]> VARBINARY = new ConversionPoint<>("VARBINARY", LogicalType.VARBINARY, byte[].class);
  public static final ConversionPoint<byte[]> BLOB = new ConversionPoint<>("BLOB", LogicalType.BLOB, byte[].class);
  public static final ConversionPoint<byte[]> GRAPHIC = new ConversionPoint<>("GRAPHIC", LogicalType.GRAPHIC, byte[].class);
  public static final ConversionPoint<String> TEXT_GRAPHIC = new ConversionPoint<>("TEXT_GRAPHIC", LogicalType.GRAPHIC, String.class);
  public static final ConversionPoint<Boolean> BOOLEAN = new ConversionPoint<>("BOOLEAN", LogicalType.BOOLEAN, Boolean.class);
  public static final ConversionPoint<Number> BOOLEAN_NUMBER = new ConversionPoint<>("BOOLEAN_NUMBER", LogicalType.BOOLEAN, Number.class);
  public static final ConversionPoint<Date> DATE = new ConversionPoint<>("DATE", LogicalType.DATE, Date.class);
  public static final ConversionPoint<Number> YEAR = new ConversionPoint<>("YEAR", LogicalType.YEAR, Number.class);
  public static final ConversionPoint<Time> TIME = new ConversionPoint<>("TIME", LogicalType.TIME, Time.class);
  public static final ConversionPoint<Number> NUMBER = new ConversionPoint<>("NUMBER", LogicalType.NUMBER, Number.class);
  public static final ConversionPoint<Number> UNSIGNED_NUMBER = new ConversionPoint<>("UNSIGNED_NUMBER", LogicalType.UNSIGNED_NUMBER, Number.class);
  public static final ConversionPoint<Number> SERIAL_NUMBER = new ConversionPoint<>("SERIAL_NUMBER", LogicalType.SERIAL, Number.class);
  public static final ConversionPoint<String> XML = new ConversionPoint<>("XML", LogicalType.XML, String.class);
  public static final ConversionPoint<String> UUID_TEXT = new ConversionPoint<>("UUID_TEXT", LogicalType.TEXT_UUID, String.class);
  public static final ConversionPoint<UUID> UUID = new ConversionPoint<>("UUID", LogicalType.UUID, java.util.UUID.class);
  public static final ConversionPoint<String> TSVECTOR = new ConversionPoint<>("TSVECTOR", LogicalType.TSVECTOR, String.class);
  public static final ConversionPoint<String> NUMBER_RANGE = new ConversionPoint<>("NUMBER_RANGE", LogicalType.NUMBER_RANGE, String.class);
  public static final ConversionPoint<String> DATE_RANGE = new ConversionPoint<>("DATE_RANGE", LogicalType.DATE_RANGE, String.class);
  public static final ConversionPoint<String> TIMESTAMP_RANGE = new ConversionPoint<>("TIMESTAMP_RANGE", LogicalType.TIMESTAMP_RANGE, String.class);
  public static final ConversionPoint<String> TIMESTAMP_TZ_RANGE = new ConversionPoint<>("TIMESTAMP_TZ_RANGE", LogicalType.TIMESTAMP_TZ_RANGE, String.class);
  public static final ConversionPoint<String> POINT = new ConversionPoint<>("POINT", LogicalType.POINT, String.class);
  public static final ConversionPoint<String> INTERVAL = new ConversionPoint<>("INTERVAL", LogicalType.INTERVAL, String.class);
  public static final ConversionPoint<String> JSON = new ConversionPoint<>("JSON", LogicalType.JSON, String.class);
  public static final ConversionPoint<Map>    MAP = new ConversionPoint<>("MAP", LogicalType.JSON, Map.class);
  public static final ConversionPoint<String> GEOMETRY = new ConversionPoint<>("GEOMETRY", LogicalType.GEOMETRY, String.class);
  public static final ConversionPoint<GeoWrapper> GEOMETRY_GEOWRAPPER = new ConversionPoint<>("GEOMETRY", LogicalType.GEOMETRY, GeoWrapper.class);
  public static final ConversionPoint<String> GEOGRAPHY = new ConversionPoint<>("GEOGRAPHY", LogicalType.GEOGRAPHY, String.class);
  public static final ConversionPoint<String> INET = new ConversionPoint<>("INET", LogicalType.INET, String.class);
  public static final ConversionPoint<Object> UNKNOWN = new ConversionPoint<>("UNKNOWN", LogicalType.UNKNOWN, Object.class);


  private final String myName;
  private final LogicalType myLogicalType;
  private final Class<T> myClass;

  public ConversionPoint(@NotNull String name, LogicalType logicalType, Class<T> aClass) {
    myName = name;
    myLogicalType = logicalType;
    myClass = aClass;
  }

  public @NotNull LogicalType getLogicalType() {
    return myLogicalType;
  }

  @Override
  public String toString() {
    return myName;
  }

  public @NotNull ConversionPoint<?> withClass(@NotNull Class<?> clazz) {
    return new ConversionPoint<>(myName, myLogicalType, clazz);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ConversionPoint &&
           StringUtil.equals(myName, ((ConversionPoint<?>)obj).myName) &&
           Comparing.equal(myLogicalType, ((ConversionPoint<?>)obj).myLogicalType) &&
           Comparing.equal(myClass, ((ConversionPoint)obj).myClass);
  }

  public @NotNull Class<T> getObjectClass() {
    return myClass;
  }

  @Override
  public int hashCode() {
    return myName.hashCode() + myLogicalType.hashCode() + myClass.hashCode();
  }
}
