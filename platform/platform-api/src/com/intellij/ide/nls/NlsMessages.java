// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Utility methods to produce localized messages
 */
public class NlsMessages {
  private static final MeasureUnit[] TIME_UNITS =
    {MeasureUnit.MILLISECOND, MeasureUnit.SECOND, MeasureUnit.MINUTE, MeasureUnit.HOUR, MeasureUnit.DAY};
  private static final long[] TIME_MULTIPLIERS = {1, 1000, 60, 60, 24};

  /**
   * @param list list of items
   * @return localized string representation of all items in the list semantically joined via 'AND'
   */
  public static @NotNull @Nls String formatAndList(Collection<?> list) {
    return ListFormatter.getInstance(DynamicBundle.getLocale(), ListFormatter.Type.AND, ListFormatter.Width.WIDE).format(list);
  }

  /**
   * @param list list of items
   * @return localized string representation of all items in the list semantically joined via 'OR'
   */
  public static @NotNull @Nls String formatOrList(Collection<?> list) {
    return ListFormatter.getInstance(DynamicBundle.getLocale(), ListFormatter.Type.OR, ListFormatter.Width.WIDE).format(list);
  }

  /**
   * @return a collector that collects a stream into the localized string that joins the stream elements into the and-list
   */
  public static <T> @NotNull Collector<T, ?, @Nls String> joiningAnd() {
    return Collectors.collectingAndThen(Collectors.toList(), NlsMessages::formatAndList);
  }

  /**
   * @return a collector that collects a stream into the localized string that joins the stream elements into the or-list
   */
  public static <T> @NotNull Collector<T, ?, @Nls String> joiningOr() {
    return Collectors.collectingAndThen(Collectors.toList(), NlsMessages::formatOrList);
  } 
  
  /**
   * Formats duration given in milliseconds as a sum of time units with at most two units
   * (example: {@code formatDuration(123456) = "2 m 3 s"}).
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDurationApproximate(long duration) {
    return formatDuration(duration, 2);
  }

  /** 
   * Formats duration given in milliseconds as a sum of time units (example: {@code formatDuration(123456, "") = "2m 3s 456ms"}).
   * The result is localized according to the currently used language pack.
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDuration(long duration) {
    return formatDuration(duration, Integer.MAX_VALUE);
  }

  @Contract(pure = true)
  private static @NotNull @Nls String formatDuration(long duration, int maxFragments) {
    TLongArrayList unitValues = new TLongArrayList();
    TIntArrayList unitIndices = new TIntArrayList();

    long count = duration;
    int i = 1;
    for (; i < TIME_UNITS.length && count > 0; i++) {
      long multiplier = TIME_MULTIPLIERS[i];
      if (count < multiplier) break;
      long remainder = count % multiplier;
      count /= multiplier;
      if (remainder != 0 || !unitValues.isEmpty()) {
        unitValues.insert(0, remainder);
        unitIndices.insert(0, i - 1);
      }
    }
    unitValues.insert(0, count);
    unitIndices.insert(0, i - 1);

    if (unitValues.size() > maxFragments) {
      int lastUnitIndex = unitIndices.get(maxFragments - 1);
      long lastMultiplier = TIME_MULTIPLIERS[lastUnitIndex];
      // Round up if needed
      if (unitValues.get(maxFragments) > lastMultiplier / 2) {
        long increment = lastMultiplier - unitValues.get(maxFragments);
        for (int unit = lastUnitIndex - 1; unit > 0; unit--) {
          increment *= TIME_MULTIPLIERS[unit];
        }
        return formatDuration(duration + increment, maxFragments);
      }
    }

    MeasureFormat format = MeasureFormat.getInstance(DynamicBundle.getLocale(), MeasureFormat.FormatWidth.SHORT);
    Measure[] measures = new Measure[Math.min(unitValues.size(), maxFragments)];
    for (i = 0; i < measures.length; i++) {
      measures[i] = new Measure(unitValues.get(i), TIME_UNITS[unitIndices.get(i)]);
    }
    return format.formatMeasures(measures);
  }

  private static final int[] PADDED_FORMATS = {3, 2, 2, 2, 1};
  /**
   * Formats duration given in milliseconds as a sum of padded time units, except the most significant unit
   * E.g. 234523598 padded as "2d 03h 11m 04s 004ms" accordingly with zeros except "days" here.
   * @param millis milliseconds
   */
  @Contract(pure = true)
  public static @NotNull @Nls String formatDurationPadded(long millis) {
    long millisIn = 1;
    int i;
    for (i=1; i < TIME_MULTIPLIERS.length; i++) {
      long multiplier = TIME_MULTIPLIERS[i];
      millisIn *= multiplier;
      if (millis < millisIn) {
        break;
      }
    }
    long d = millis;
    LocalizedNumberFormatter formatter = NumberFormatter.withLocale(DynamicBundle.getLocale()).unitWidth(NumberFormatter.UnitWidth.NARROW);
    List<FormattedNumber> result = new ArrayList<>();
    for (i-=1; i >= 0; i--) {
      long multiplier = i==TIME_MULTIPLIERS.length-1 ? 1 : TIME_MULTIPLIERS[i+1];
      millisIn /= multiplier;
      long value = d / millisIn;
      d = d % millisIn;
      IntegerWidth style = IntegerWidth.zeroFillTo(result.isEmpty() ? 1 : PADDED_FORMATS[i]); // do not pad the most significant unit
      LocalizedNumberFormatter unitFormatter = formatter.unit(TIME_UNITS[i]).integerWidth(style);
      result.add(unitFormatter.format(value));
    }
    return ListFormatter.getInstance(Locale.getDefault(), ListFormatter.Type.UNITS, ListFormatter.Width.NARROW).format(result);
  }

}
