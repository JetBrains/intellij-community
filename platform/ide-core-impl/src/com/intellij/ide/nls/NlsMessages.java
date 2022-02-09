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
    return new NlsDurationFormatter()
      .setMaxFragments(2)
      .setNarrow(false)
      .formatDuration(duration);
  }

  /**
   * Formats duration given in milliseconds as a sum of time units with at most two units
   * (example: {@code formatDuration(123456) = "2 m 3 s"}).
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDurationApproximateNarrow(long duration) {
    return new NlsDurationFormatter()
      .setMaxFragments(2)
      .formatDuration(duration);
  }

  /**
   * Formats duration given in milliseconds as a sum of time units (example: {@code formatDuration(123456, "") = "2m 3s 456ms"}).
   * The result is localized according to the currently used language pack.
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDuration(long duration) {
    return new NlsDurationFormatter()
      .setNarrow(false)
      .formatDuration(duration);
  }

  /**
   * Converts {@link TimeUnit} to {@link MeasureUnit}
   *
   * @param timeUnit input timeunit
   * @return the corresponding unit of measurement
   */
  private static @NotNull MeasureUnit convert(@NotNull TimeUnit timeUnit) {
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
        throw new AssertionError("Probably a new type of time measurement has been added in the given JDK. Can't convert this type");
    }
  }

  /**
   * Format duration given in durationTimeUnit as a sum of time units
   *
   * @param duration         duration
   * @param durationTimeUnit measure unit for duration
   * @param maxFragments     count of fragments (example: {@code for maxFragments = 1 formatDuration(61, 1, ....) = "1m"}
   * @param narrow           is narrow on output
   * @return formatted duration
   */
  private static @NotNull @Nls String formatDuration(long duration,
                                                     MeasureUnit durationTimeUnit, int maxFragments,
                                                     boolean narrow) {
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
   * @return formatted duration
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDuration(long duration, int maxFragments, boolean narrow) {
    return new NlsDurationFormatter()
      .setDurationTimeUnit(TimeUnit.MILLISECONDS)
      .setNarrow(narrow)
      .setMaxFragments(maxFragments)
      .formatDuration(duration);
  }

  private static @NotNull @Nls String formatDurationPaddedMeasure(long duration, @NotNull MeasureUnit durationTimeUnit) {
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
      if (duration < millisIn) {
        break;
      }
    }
    long d = duration;
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
   * Formats duration given in milliseconds as a sum of padded time units, except the most significant unit
   * E.g. 234523598 padded as "2d 03h 11m 04s 004ms" accordingly with zeros except "days" here.
   *
   * @param millis milliseconds
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDurationPadded(long millis) {
    return new NlsDurationFormatter().setPadded(true).formatDuration(millis);
  }

  /**
   * Class that allows to get localized strings by duration
   *
   * <p>Note: follows the builder pattern
   */
  public static final class NlsDurationFormatter {
    private boolean padded = false;
    private boolean narrow = true;
    private int maxFragments = Integer.MAX_VALUE;
    private @NotNull MeasureUnit durationTimeUnit = MeasureUnit.MILLISECOND;

    /**
     * Padding each value with leading zeros to the maximum size. Example {@code padded 1s = 01s, padded 0ms = 000ms}
     *
     * <p>Default value: {@code false}, which means that there will be no padding
     *
     * @param padded is padding enable
     * @return formatter
     */
    public @NotNull NlsDurationFormatter setPadded(boolean padded) {
      this.padded = padded;
      return this;
    }

    /**
     * The distance between the number and the corresponding time unit becomes small
     *
     * <p>Default value: {@code true}, which means that narrowing there will be
     *
     * @param narrow is narrowing enable
     * @return formatter
     */
    public @NotNull NlsDurationFormatter setNarrow(boolean narrow) {
      this.narrow = narrow;
      return this;
    }

    /**
     * Sets the maximum number of values in a row. Example: if maxFragments = 1, then 1 minute 2 seconds will be rounded to 1 minute
     *
     * <p>Default value: {@code Integer}, which means that there will be no rounding
     *
     * @param maxFragments maximum of values in a row
     * @return formatter
     */
    public @NotNull NlsDurationFormatter setMaxFragments(int maxFragments) {
      this.maxFragments = maxFragments;
      return this;
    }

    /**
     * Sets the unit of measurement in which the conversion will be performed. If give it seconds, then the next conversion will convert seconds to string
     *
     * <p>Default value: {@code TimeUnit.MILLISECONDS}, which means that all formatting will be in milliseconds
     *
     * @param durationTimeUnit unit of measurement
     * @return formatter
     */
    public @NotNull NlsDurationFormatter setDurationTimeUnit(@NotNull TimeUnit durationTimeUnit) {
      this.durationTimeUnit = convert(durationTimeUnit);
      return this;
    }

    /**
     * Formats a number to a string according to the options specified by the builder pattern
     *
     * @param duration duration
     * @return formatted string
     */
    public @NotNull @Nls String formatDuration(long duration) {
      if (padded) {
        return formatDurationPaddedMeasure(duration, durationTimeUnit);
      }
      else {
        return NlsMessages.formatDuration(duration, durationTimeUnit, maxFragments, narrow);
      }
    }
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
