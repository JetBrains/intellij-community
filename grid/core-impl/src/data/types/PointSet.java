package com.intellij.database.data.types;

import com.intellij.database.remote.jdbc.GeoWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;
import java.util.*;

public class PointSet<T> {
  private static final List<PointSet<?>> ourGroups = new ArrayList<>();

  public static final PointSet<Object> UNKNOWN = new PointSet<>("UNKNOWN", Object.class, ConversionPoint.UNKNOWN);
  public static final PointSet<String> UUID_TEXT = new PointSet<>("UUID_TEXT", String.class, ConversionPoint.UUID_TEXT);
  public static final PointSet<UUID> UUID = new PointSet<>("UUID", java.util.UUID.class, ConversionPoint.UUID);
  public static final PointSet<byte[]> BINARY = new PointSet<>(
    "BINARY",
    byte[].class,
    ConversionPoint.BINARY,
    ConversionPoint.BINARY_ID,
    ConversionPoint.VARBINARY,
    ConversionPoint.BLOB,
    ConversionPoint.GRAPHIC
  );
  public static final PointSet<String> TEXT = new PointSet<>(
    "TEXT",
    String.class,
    ConversionPoint.CLOB,
    ConversionPoint.INTERVAL,
    ConversionPoint.JSON,
    ConversionPoint.POINT,
    ConversionPoint.GEOMETRY,
    ConversionPoint.GEOGRAPHY,
    ConversionPoint.TEXT,
    ConversionPoint.NTEXT,
    ConversionPoint.TEXT_TIMESTAMP,
    ConversionPoint.TEXT_DATE,
    ConversionPoint.BLOB_TEXT,
    ConversionPoint.TEXT_ID,
    ConversionPoint.TEXT_GRAPHIC,
    ConversionPoint.CHAR,
    ConversionPoint.VARCHAR,
    ConversionPoint.NCHAR,
    ConversionPoint.NVARCHAR,
    ConversionPoint.XML,
    ConversionPoint.INET,
    ConversionPoint.TSVECTOR
  );
  public static final PointSet<Map> MAP = new PointSet<>(
    "MAP",
    Map.class,
    ConversionPoint.MAP
  );

  public static final PointSet<String> BINARY_STRING = new PointSet<>(
    "BINARY_STRING",
    String.class,
    ConversionPoint.BINARY_STRING
  );
  public static final PointSet<String> BIT_STRING = new PointSet<>(
    "BIT_STRING",
    String.class,
    ConversionPoint.BIT_STRING
  );
  public static final PointSet<Boolean> BOOLEAN = new PointSet<>(
    "BOOLEAN",
    Boolean.class,
    ConversionPoint.BOOLEAN
  );
  public static final PointSet<Number> BOOLEAN_NUMBER = new PointSet<>(
    "NUMBER",
    Number.class,
    ConversionPoint.BOOLEAN_NUMBER
  );
  public static final PointSet<Date> DATE = new PointSet<>("DATE", Date.class, ConversionPoint.DATE);
  public static final PointSet<Time> TIME = new PointSet<>("TIME", Time.class, ConversionPoint.TIME);
  public static final PointSet<String> NUMBER_RANGE = new PointSet<>("NUMBER_RANGE", String.class, ConversionPoint.NUMBER_RANGE);
  public static final PointSet<String> DATE_RANGE = new PointSet<>("DATE_RANGE", String.class, ConversionPoint.DATE_RANGE);
  public static final PointSet<String> TIMESTAMP_RANGE = new PointSet<>(
    "TIMESTAMP_RANGE",
    String.class,
    ConversionPoint.TIMESTAMP_RANGE,
    ConversionPoint.TIMESTAMP_TZ_RANGE
  );
  public static final PointSet<Timestamp> TIMESTAMP = new PointSet<>(
    "TIMESTAMP",
    Timestamp.class,
    ConversionPoint.TIMESTAMP
  );
  public static final PointSet<TemporalAccessor> TEMPORAL_TIMESTAMP = new PointSet<>(
    "TEMPORAL_TIMESTAMP",
    TemporalAccessor.class,
    ConversionPoint.TEMPORAL_TIMESTAMP
  );
  public static final PointSet<TemporalAccessor> TEMPORAL_TIME = new PointSet<>(
    "TEMPORAL_TIME",
    TemporalAccessor.class,
    ConversionPoint.TEMPORAL_TIME
  );
  public static final PointSet<Number> NUMBER = new PointSet<>(
    "NUMBER",
    Number.class,
    ConversionPoint.NUMBER,
    ConversionPoint.UNSIGNED_NUMBER,
    ConversionPoint.SERIAL_NUMBER,
    ConversionPoint.BIG_DECIMAL,
    ConversionPoint.DOUBLE_PRECISION,
    ConversionPoint.SINGLE_PRECISION
  );
  public static final PointSet<Number> MONEY = new PointSet<>(
    "MONEY",
    Number.class,
    ConversionPoint.MONEY
  );

  public static final PointSet<GeoWrapper> GEOMETRY = new PointSet<>(
    "GEOMETRY",
    GeoWrapper.class,
    ConversionPoint.GEOMETRY_GEOWRAPPER
  );

  private final String myName;
  private final ConversionPoint<T>[] myTypes;
  private final Class<T> myClass;

  public PointSet(@NotNull String name, @NotNull Class<T> aClass, @NotNull ConversionPoint<T>... types) {
    myName = name;
    myTypes = types;
    myClass = aClass;
    ourGroups.add(this);
  }

  public @NotNull Class<T> getObjectClass() {
    return myClass;
  }

  public boolean contains(@NotNull ConversionPoint type) {
    return ContainerUtil.exists(
      myTypes,
      desc -> desc.getLogicalType() == type.getLogicalType() &&
              StringUtil.equals(desc.toString(), type.toString()) &&
              desc.getObjectClass().isAssignableFrom(type.getObjectClass())
    );
  }

  public @NotNull ConversionPoint<T>[] getTypes() {
    return myTypes;
  }

  public static @NotNull PointSet of(@NotNull ConversionPoint type) {
    for (PointSet group : ourGroups) {
      if (group.contains(type)) return group;
    }
    return UNKNOWN;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof PointSet &&
           StringUtil.equals(myName, ((PointSet<?>)obj).myName) &&
           Comparing.equal(myClass, ((PointSet)obj).myClass) &&
           Arrays.equals(myTypes, ((PointSet)obj).myTypes);
  }

  @Override
  public int hashCode() {
    return myName.hashCode() + myClass.hashCode() + Arrays.hashCode(myTypes);
  }

  @Override
  public String toString() {
    return myName;
  }
}
