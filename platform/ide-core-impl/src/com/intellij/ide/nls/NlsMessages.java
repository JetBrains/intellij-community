// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.nls;

import com.ibm.icu.number.FormattedNumber;
import com.ibm.icu.number.IntegerWidth;
import com.ibm.icu.number.LocalizedNumberFormatter;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.text.ListFormatter;
import com.ibm.icu.text.MeasureFormat;
import com.ibm.icu.util.Measure;
import com.ibm.icu.util.MeasureUnit;
import com.intellij.DynamicBundle;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Utility methods to produce localized messages
 */
public class NlsMessages {
  private static final MeasureUnit[] TIME_UNITS =
    {MeasureUnit.NANOSECOND, MeasureUnit.MICROSECOND, MeasureUnit.MILLISECOND, MeasureUnit.SECOND, MeasureUnit.MINUTE, MeasureUnit.HOUR,
      MeasureUnit.DAY, MeasureUnit.WEEK};
  private static final long[] TIME_MULTIPLIERS = {1, 1000, 1000, 1000, 60, 60, 24, 7};
  private static final int[] PADDED_FORMAT = {3, 3, 3, 2, 2, 2, 1, 1};

  /**
   * @param list list of items
   * @return localized string representation of all items in the list with conjunction formatting.
   * E.g. formatAndList(List.of("X", "Y", "Z")) will produce "X, Y, and Z" in English locale.
   */
  public static @NotNull @Nls String formatAndList(Collection<?> list) {
    return ListFormatter.getInstance(DynamicBundle.getLocale(), ListFormatter.Type.AND, ListFormatter.Width.WIDE).format(list);
  }

  /**
   * @param list list of items
   * @return localized narrow string representation of all items in the list with conjunction formatting.
   * In narrow representation the conjunction could be omitted.
   * E.g. formatAndList(List.of("X", "Y", "Z")) will produce "X, Y, Z" in English locale.
   */
  public static @NotNull @Nls String formatNarrowAndList(Collection<?> list) {
    return ListFormatter.getInstance(DynamicBundle.getLocale(), ListFormatter.Type.AND, ListFormatter.Width.NARROW).format(list);
  }

  /**
   * @param list list of items
   * @return localized narrow string representation of all items in the list with disjunction formatting.
   * E.g. formatAndList(List.of("X", "Y", "Z")) will produce "X, Y, or Z" in English locale.
   */
  public static @NotNull @Nls String formatOrList(Collection<?> list) {
    return ListFormatter.getInstance(DynamicBundle.getLocale(), ListFormatter.Type.OR, ListFormatter.Width.WIDE).format(list);
  }

  /**
   * @return a collector that collects a stream into the localized string that joins the stream elements using conjunction formatting.
   * E.g. Stream.of("X", "Y", "Z").collect(joiningAnd()) will produce "X, Y, and Z" in English locale.
   */
  public static <T> @NotNull Collector<T, ?, @Nls String> joiningAnd() {
    return Collectors.collectingAndThen(Collectors.toList(), NlsMessages::formatAndList);
  }

  /**
   * @return a collector that collects a stream into the localized string that joins the stream elements using narrow conjunction formatting.
   * E.g. Stream.of("X", "Y", "Z").collect(joiningNarrowAnd()) will produce "X, Y, Z" in English locale.
   */
  public static <T> @NotNull Collector<T, ?, @Nls String> joiningNarrowAnd() {
    return Collectors.collectingAndThen(Collectors.toList(), NlsMessages::formatNarrowAndList);
  }

  /**
   * @return a collector that collects a stream into the localized string that joins the stream elements using disjunction formatting.
   * E.g. Stream.of("X", "Y", "Z").collect(joiningOr()) will produce "X, Y, or Z" in English locale.
   */
  public static <T> @NotNull Collector<T, ?, @Nls String> joiningOr() {
    return Collectors.collectingAndThen(Collectors.toList(), NlsMessages::formatOrList);
  }

  /**
   * Formats duration given in milliseconds as a sum of time units with at most two units
   * (example: {@code formatDuration(123456) = "2 m, 3 s"}).
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDurationApproximate(long duration) {
    return formatDuration(duration, 2, false);
  }

  /**
   * Formats duration given in milliseconds as a sum of time units with at most two units
   * (example: {@code formatDuration(123456) = "2 m 3 s"}).
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDurationApproximateNarrow(long duration) {
    return formatDuration(duration, 2, true);
  }

  /**
   * Formats duration given in milliseconds as a sum of time units (example: {@code formatDuration(123456, "") = "2m 3s 456ms"}).
   * The result is localized according to the currently used language pack.
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDuration(long duration) {
    return formatDuration(duration, Integer.MAX_VALUE, false);
  }

  /**
   * <p>Note: Can return null if the given unit of measurement is not included in the considered ones (see {@code returns}). However, such a case can only arise if a new value is added to TimeUnit.</p>
   * <p>
   * Converts {@link TimeUnit} to {@link MeasureUnit}
   *
   * @param timeUnit input timeunit
   * @return if timeunit is {@code NANOSECONDS or MICROSECONDS or MILLISECONDS or SECONDS or MINUTES or HOURS or DAYS}, then we return the corresponding unit of measurement, otherwise we return null
   */
  private static MeasureUnit convert(TimeUnit timeUnit) {
    switch (timeUnit) {
      case NANOSECONDS:
        return MeasureUnit.NANOSECOND;
      case MICROSECONDS:
        return MeasureUnit.MICROSECOND;
      case MILLISECONDS:
        return MeasureUnit.MILLISECOND;
      case SECONDS:
        return MeasureUnit.SECOND;
      case MINUTES:
        return MeasureUnit.MINUTE;
      case HOURS:
        return MeasureUnit.HOUR;
      case DAYS:
        return MeasureUnit.DAY;
      default:
        return null;
    }
  }

  /**
   * Formats duration given in some duration time unit (example: {@code formatDuration(123456, MILLISECONDS) = "2m 3s 456ms"})
   *
   * @param duration         duration in time unit
   * @param durationTimeUnit the unit of time in which the number is given. If timeunit is NOT {@code NANOSECONDS or MICROSECONDS or MILLISECONDS or SECONDS or MINUTES or HOURS or DAYS}, duration and timeunit will be converted to nanoseconds
   * @return format string
   */
  public static @NotNull @Nls String formatDuration(long duration, TimeUnit durationTimeUnit) {
    return formatDuration(duration, Integer.MAX_VALUE, false, durationTimeUnit);
  }

  /**
   * Formats duration given in some duration time unit (example: {@code formatDuration(123456, 4, MILLISECONDS) = "2m 3s 456ms"})
   *
   * @param duration         duration in time unit
   * @param maxFragments     count of fragments tie we need
   * @param durationTimeUnit the unit of time in which the number is given. If timeunit is NOT {@code NANOSECONDS or MICROSECONDS or MILLISECONDS or SECONDS or MINUTES or HOURS or DAYS}, duration and timeunit will be converted to nanoseconds
   * @return format string
   */
  public static @NotNull @Nls String formatDuration(long duration, int maxFragments, TimeUnit durationTimeUnit) {
    return formatDuration(duration, maxFragments, false, durationTimeUnit);
  }

  /**
   * Format duration given in durationTimeUnit as a sum of time units
   *
   * @param duration     duration
   * @param maxFragments count of fragments (example: {@code for maxFragments = 1 formatDuration(61, 1, ....) = "1m"}
   * @param narrow       is narrow on output
   * @param timeUnit     the unit of time in which the number is given. If timeunit is NOT {@code NANOSECONDS or MICROSECONDS or MILLISECONDS or SECONDS or MINUTES or HOURS or DAYS}, duration and timeunit will be converted to nanoseconds
   * @return format duration
   */
  public static @NotNull @Nls String formatDuration(long duration, int maxFragments, boolean narrow, TimeUnit timeUnit) {
    MeasureUnit measureUnit = convert(timeUnit);
    if (measureUnit == null) {
      measureUnit = MeasureUnit.NANOSECOND;
      duration = timeUnit.toNanos(duration);
    }
    return formatDuration(duration, maxFragments, narrow, measureUnit);
  }

  /**
   * Format duration given in durationTimeUnit as a sum of time units
   *
   * @param duration         duration
   * @param maxFragments     count of fragments (example: {@code for maxFragments = 1 formatDuration(61, 1, ....) = "1m"}
   * @param narrow           is narrow on output
   * @param durationTimeUnit measure unit for duration
   * @return format duration
   */
  private static @NotNull @Nls String formatDuration(long duration,
                                                     int maxFragments,
                                                     boolean narrow,
                                                     MeasureUnit durationTimeUnit) {
    LongArrayList unitValues = new LongArrayList();
    IntList unitIndices = new IntArrayList();

    long count = duration;
    int i = 0;
    while (TIME_UNITS[i] != durationTimeUnit) {
      i++;
      if (i == TIME_UNITS.length) {
        // Will never be called in a practical case, since the converter produces only those time units that are already in the array
        // However, it can be called theoretically, since the converter can be changed
        throw new IllegalArgumentException("Duration time unit doesn't exists in all time units");
      }
    }
    int startPosition = i;
    i++;
    for (; i < TIME_UNITS.length && count > 0; i++) {
      long multiplier = TIME_MULTIPLIERS[i];
      if (count < multiplier) break;
      long remainder = count % multiplier;
      count /= multiplier;
      if (remainder != 0 || !unitValues.isEmpty()) {
        unitValues.add(0, remainder);
        unitIndices.add(0, i - 1);
      }
    }
    unitValues.add(0, count);
    unitIndices.add(0, i - 1);

    if (unitValues.size() > maxFragments) {
      int lastUnitIndex = unitIndices.getInt(maxFragments - 1);
      long lastMultiplier = TIME_MULTIPLIERS[lastUnitIndex];
      // Round up if needed
      if (unitValues.getLong(maxFragments) > lastMultiplier / 2) {
        long increment = lastMultiplier - unitValues.getLong(maxFragments);
        for (int unit = lastUnitIndex - 1; unit > startPosition; unit--) {
          increment *= TIME_MULTIPLIERS[unit];
        }
        return formatDuration(duration + increment, maxFragments, narrow);
      }
    }

    int finalCount = Math.min(unitValues.size(), maxFragments);
    if (narrow) {
      List<String> fragments = new ArrayList<>();
      LocalizedNumberFormatter formatter = NumberFormatter.withLocale(DynamicBundle.getLocale()).unitWidth(NumberFormatter.UnitWidth.SHORT);
      for (i = 0; i < finalCount; i++) {
        fragments.add(formatter.unit(
          TIME_UNITS[unitIndices.getInt(i)]).format(unitValues.getLong(i)).toString().replace(' ', '\u2009'));
      }
      return StringUtil.join(fragments, " ");
    }
    MeasureFormat format = MeasureFormat.getInstance(DynamicBundle.getLocale(), MeasureFormat.FormatWidth.SHORT);
    Measure[] measures = new Measure[finalCount];
    for (i = 0; i < finalCount; i++) {
      measures[i] = new Measure(unitValues.getLong(i), TIME_UNITS[unitIndices.getInt(i)]);
    }
    return format.formatMeasures(measures);
  }

  /**
   * Format duration given in milliseconds as a sum of time units
   *
   * @param duration     duration
   * @param maxFragments count of fragments (example: {@code for maxFragments = 1 formatDuration(61, 1, ....) = "1m"}
   * @param narrow       is narrow on output
   * @return format duration
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDuration(long duration, int maxFragments, boolean narrow) {
    return formatDuration(duration, maxFragments, narrow, TimeUnit.MILLISECONDS);
  }

  private static @NotNull @Nls String formatDurationPaddedMeasure(long millis, MeasureUnit durationTimeUnit) {
    long millisIn = 1;
    int i = 0;
    while (TIME_UNITS[i] != durationTimeUnit) {
      i++;
      // Will never be called in a practical case, since the converter produces only those time units that are already in the array
      // However, it can be called theoretically, since the converter can be changed
      if (i == TIME_UNITS.length) throw new IllegalArgumentException("Duration time unit doesn't exists in all time units");
    }
    i++;
    int startPosition = i;
    for (; i < TIME_MULTIPLIERS.length; i++) {
      long multiplier = TIME_MULTIPLIERS[i];
      millisIn *= multiplier;
      if (millis < millisIn) {
        break;
      }
    }
    long d = millis;
    LocalizedNumberFormatter formatter = NumberFormatter.withLocale(DynamicBundle.getLocale()).unitWidth(NumberFormatter.UnitWidth.NARROW);
    List<FormattedNumber> result = new ArrayList<>();
    for (i -= 1; i >= startPosition - 1; i--) {
      long multiplier = i == TIME_MULTIPLIERS.length - 1 ? 1 : TIME_MULTIPLIERS[i + 1];
      millisIn /= multiplier;
      long value = d / millisIn;
      d = d % millisIn;
      IntegerWidth style = IntegerWidth.zeroFillTo(result.isEmpty() ? 1 : PADDED_FORMAT[i]); // do not pad the most significant unit
      LocalizedNumberFormatter unitFormatter = formatter.unit(TIME_UNITS[i]).integerWidth(style);
      result.add(unitFormatter.format(value));
    }
    return ListFormatter.getInstance(Locale.getDefault(), ListFormatter.Type.UNITS, ListFormatter.Width.NARROW).format(result);
  }

  /**
   * Formats duration given in some unit of time as a sum of padded time units, except the most significant unit
   * E.g. {@code formatDurationPaddedTimeUnit(234523598, MILLISECONDS) = "2d 03h 11m 04s 004ms"}.
   *
   * @param duration         duration in time unit
   * @param durationTimeUnit the unit of time in which the number is given. If timeunit is NOT {@code NANOSECONDS or MICROSECONDS or MILLISECONDS or SECONDS or MINUTES or HOURS or DAYS}, duration and timeunit will be converted to nanoseconds
   * @return format string
   */
  public static @NotNull @Nls String formatDurationPadded(long duration, TimeUnit durationTimeUnit) {
    MeasureUnit measureUnit = convert(durationTimeUnit);
    if (measureUnit == null) {
      measureUnit = MeasureUnit.NANOSECOND;
      duration = durationTimeUnit.toNanos(duration);
    }
    return formatDurationPaddedMeasure(duration, measureUnit);
  }

  /**
   * Formats duration given in milliseconds as a sum of padded time units, except the most significant unit
   * E.g. 234523598 padded as "2d 03h 11m 04s 004ms" accordingly with zeros except "days" here.
   *
   * @param millis milliseconds
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDurationPadded(long millis) {
    return formatDurationPadded(millis, TimeUnit.MILLISECONDS);
  }

  /**
   * @param date date to format
   * @return date in human-readable format localized according to the current language pack used
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDateLong(Date date) {
    return DateFormat.getDateInstance(DateFormat.LONG, DynamicBundle.getLocale()).format(date);
  }
}
