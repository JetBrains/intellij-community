// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.DynamicBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.UtilBundle;
import com.intellij.util.system.OS;
import com.intellij.util.system.WindowsSystemLibraries;
import com.intellij.util.text.DateTimeFormatManager.Formats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.util.Objects.requireNonNullElse;

/**
 * Formats date/time according to a system (OS) format or to the IDE settings.
 * Values to format are expected to be in UTC; they are converted to the local timezone on formatting.
 * <p/>
 * Please note that formatted strings may include special characters (e.g., Narrow No-Break Space),
 * so take care on inserting them into documents.
 */
public final class DateFormatUtil {
  private static final Logger LOG = Logger.getInstance(DateFormatUtil.class);

  private static final long SECOND = 1_000L;
  private static final long MINUTE = 60 * SECOND;
  private static final long HOUR = 60 * MINUTE;
  private static final long DAY = 24 * HOUR;
  private static final long WEEK = 7 * DAY;
  private static final long MONTH = 30 * DAY;
  private static final long YEAR = 365 * DAY;

  public static final String TIME_SHORT_12H = "h:mm\u202Fa";
  public static final String TIME_SHORT_24H = "HH:mm";

  private static final String TIME_MEDIUM_12H = "h:mm:ss\u202Fa";
  private static final String TIME_MEDIUM_24H = "HH:mm:ss";

  private static final long[] DENOMINATORS = {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE};
  private enum Period {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE}
  private static final Period[] PERIODS = {Period.YEAR, Period.MONTH, Period.WEEK, Period.DAY, Period.HOUR, Period.MINUTE};

  private DateFormatUtil() { }

  /** @deprecated use {@link #formatDate} */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("removal")
  public static @NotNull SyncDateFormat getDateFormat() {
    return new SyncDateFormat(formats().dateFmt());
  }

  /** @deprecated use {@link #formatDateTime} */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("removal")
  public static @NotNull SyncDateFormat getDateTimeFormat() {
    return new SyncDateFormat(formats().dateTimeFmt());
  }

  public static @NlsSafe @NotNull String formatTime(@NotNull Date time) {
    return formats().timeShort().format(toZoned(time));
  }

  public static @NlsSafe @NotNull String formatTime(long time) {
    return formats().timeShort().format(toZoned(time));
  }

  public static @NlsSafe @NotNull String formatTimeWithSeconds(@NotNull Date time) {
    return formats().timeMedium().format(toZoned(time));
  }

  public static @NlsSafe @NotNull String formatTimeWithSeconds(long time) {
    return formats().timeMedium().format(toZoned(time));
  }

  public static @NlsSafe @NotNull String formatDate(@NotNull Date time) {
    return formats().date().format(toZoned(time));
  }

  public static @NlsSafe @NotNull String formatDate(long time) {
    return formats().date().format(toZoned(time));
  }

  public static @NlsSafe @NotNull String formatPrettyDate(@NotNull Date date) {
    return formatPrettyDate(date.getTime());
  }

  public static @NlsSafe @NotNull String formatPrettyDate(long time) {
    var pretty = doFormatPretty(time, false);
    return pretty != null ? pretty : formatDate(time);
  }

  public static @NlsSafe @NotNull String formatDateTime(@NotNull Date date) {
    return formats().dateTime().format(toZoned(date));
  }

  public static @NlsSafe @NotNull String formatDateTime(long time) {
    return formats().dateTime().format(toZoned(time));
  }

  public static @NlsSafe @NotNull String formatPrettyDateTime(@NotNull Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  public static @NlsSafe @NotNull String formatPrettyDateTime(long time) {
    var pretty = doFormatPretty(time, true);
    return pretty != null ? pretty : formatDateTime(time);
  }

  private static @Nullable String doFormatPretty(long time, boolean formatTime) {
    if (!DateTimeFormatManager.getInstance().isPrettyFormattingAllowed()) return null;

    long currentTime = Clock.getTime();
    Calendar c = Calendar.getInstance();

    c.setTimeInMillis(currentTime);
    int currentYear = c.get(Calendar.YEAR);
    int currentDayOfYear = c.get(Calendar.DAY_OF_YEAR);

    c.setTimeInMillis(time);
    int year = c.get(Calendar.YEAR);
    int dayOfYear = c.get(Calendar.DAY_OF_YEAR);

    if (LOG.isTraceEnabled()) {
      LOG.trace("now=" + currentTime + " t=" + time + " z=" + c.getTimeZone());
    }

    if (formatTime) {
      long delta = currentTime - time;
      if (delta >= 0 && delta <= HOUR + MINUTE) {
        return UtilBundle.message("date.format.minutes.ago", (int)Math.rint(delta / (double)MINUTE));
      }
    }

    boolean isToday = currentYear == year && currentDayOfYear == dayOfYear;
    if (isToday) {
      String result = UtilBundle.message("date.format.today");
      return formatTime ? result + " " + formatTime(time) : result;
    }

    boolean isYesterdayOnPreviousYear =
      currentYear == year + 1 && currentDayOfYear == 1 && dayOfYear == c.getActualMaximum(Calendar.DAY_OF_YEAR);
    boolean isYesterday = isYesterdayOnPreviousYear || currentYear == year && currentDayOfYear == dayOfYear + 1;
    if (isYesterday) {
      String result = UtilBundle.message("date.format.yesterday");
      return formatTime ? result + " " + formatTime(time) : result;
    }

    return null;
  }

  public static @NlsSafe @NotNull String formatFrequency(long time) {
    return UtilBundle.message("date.frequency", formatBetweenDates(time, 0));
  }

  public static @NlsSafe @NotNull String formatBetweenDates(long d1, long d2) {
    long delta = Math.abs(d1 - d2);
    if (delta == 0) return UtilBundle.message("date.format.right.now");

    int n = -1;
    int i;
    for (i = 0; i < DENOMINATORS.length; i++) {
      long denominator = DENOMINATORS[i];
      if (delta >= denominator) {
        n = (int)(delta / denominator);
        break;
      }
    }

    if (d2 > d1) {
      if (n <= 0) {
        return UtilBundle.message("date.format.a.few.moments.ago");
      }
      else {
        return someTimeAgoMessage(PERIODS[i], n);
      }
    }
    else if (d2 < d1) {
      if (n <= 0) {
        return UtilBundle.message("date.format.in.a.few.moments");
      }
      else {
        return composeInSomeTimeMessage(PERIODS[i], n);
      }
    }

    return "";
  }

  /** @deprecated use {@link com.intellij.ide.nls.NlsMessages#formatDateLong} */
  @Deprecated(forRemoval = true)
  public static @NlsSafe @NotNull String formatAboutDialogDate(@NotNull Date date) {
    return DateFormat.getDateInstance(DateFormat.LONG, Locale.US).format(date);
  }

  /**
   * Return sample date that can be used to determine preferred string width.
   * <p>
   * We should not use {@code new Date()} to ensure results are reproducible (and to avoid "Today" for pretty formats).
   * Returned date is expected to return maximum width string for date formats like "d.m.yy H:M".
   */
  public static @NotNull Date getSampleDateTime() {
    return Date.from(LocalDateTime.of(2100, Month.DECEMBER, 31, 23, 59).atZone(ZoneId.systemDefault()).toInstant());
  }

  public static @NotNull ZonedDateTime toZoned(@NotNull Date date) {
    return date.toInstant().atZone(ZoneId.systemDefault());
  }

  public static @NotNull ZonedDateTime toZoned(long date) {
    return Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault());
  }

  //<editor-fold desc="Helpers">
  private static Formats formats() {
    return DateTimeFormatManager.getInstance().getFormats();
  }

  private static String someTimeAgoMessage(Period period, int n) {
    return switch (period) {
      case DAY -> UtilBundle.message("date.format.n.days.ago", n);
      case MINUTE -> UtilBundle.message("date.format.n.minutes.ago", n);
      case HOUR -> UtilBundle.message("date.format.n.hours.ago", n);
      case MONTH -> UtilBundle.message("date.format.n.months.ago", n);
      case WEEK -> UtilBundle.message("date.format.n.weeks.ago", n);
      default -> UtilBundle.message("date.format.n.years.ago", n);
    };
  }

  private static String composeInSomeTimeMessage(Period period, int n) {
    return switch (period) {
      case DAY -> UtilBundle.message("date.format.in.n.days", n);
      case MINUTE -> UtilBundle.message("date.format.in.n.minutes", n);
      case HOUR -> UtilBundle.message("date.format.in.n.hours", n);
      case MONTH -> UtilBundle.message("date.format.in.n.months", n);
      case WEEK -> UtilBundle.message("date.format.in.n.weeks", n);
      default -> UtilBundle.message("date.format.in.n.years", n);
    };
  }

  static @NotNull Formats getCustomFormats(DateTimeFormatManager settings) {
    var date = settings.getDateFormatPattern();
    var timeShort = settings.isUse24HourTime() ? TIME_SHORT_24H : TIME_SHORT_12H;
    var timeMedium = settings.isUse24HourTime() ? TIME_MEDIUM_24H : TIME_MEDIUM_12H;
    var locale = requireNonNullElse(getDynamicLocale(), Locale.getDefault(Locale.Category.FORMAT));
    return makeFormats(date, timeShort, timeMedium, locale);
  }

  static @NotNull Formats getSystemFormats() {
    var locale = getDynamicLocale();

    if (locale == null) {
      try {
        if (OS.CURRENT == OS.macOS) return getMacFormats();
        if (OS.CURRENT == OS.Windows) return getWindowsFormats();
      }
      catch (Throwable t) {
        LOG.error(t);
      }

      locale = requireNonNullElse(getUnixLocale(), Locale.getDefault(Locale.Category.FORMAT));
    }

    return new Formats(
      DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale),
      DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
      DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale),
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(locale),
      DateFormat.getDateInstance(DateFormat.SHORT, locale),
      DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale));
  }

  private static @Nullable Locale getDynamicLocale() {
    var locale = DynamicBundle.getLocale();
    if (LOG.isTraceEnabled()) LOG.trace("dyn.locale=" + locale);
    return locale.equals(Locale.ENGLISH) ? null : locale;
  }

  private static final class CF {
    static final long kCFDateFormatterNoStyle = 0;
    static final long kCFDateFormatterShortStyle = 1;
    static final long kCFDateFormatterMediumStyle = 2;

    static final StructLayout RANGE = MemoryLayout.structLayout(JAVA_LONG, JAVA_LONG);
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIBRARY =
      SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());
    static final MethodHandle LOCALE_COPY_CURRENT =
      LINKER.downcallHandle(LIBRARY.findOrThrow("CFLocaleCopyCurrent"), FunctionDescriptor.of(ADDRESS));
    static final MethodHandle LOCALE_GET_IDENTIFIER =
      LINKER.downcallHandle(LIBRARY.findOrThrow("CFLocaleGetIdentifier"), FunctionDescriptor.of(ADDRESS, ADDRESS));
    static final MethodHandle DATE_FORMATTER_CREATE = LINKER.downcallHandle(
      LIBRARY.findOrThrow("CFDateFormatterCreate"), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG));
    static final MethodHandle DATE_FORMATTER_GET_FORMAT =
      LINKER.downcallHandle(LIBRARY.findOrThrow("CFDateFormatterGetFormat"), FunctionDescriptor.of(ADDRESS, ADDRESS));
    static final MethodHandle STRING_GET_LENGTH =
      LINKER.downcallHandle(LIBRARY.findOrThrow("CFStringGetLength"), FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    static final MethodHandle STRING_GET_CHARACTERS =
      LINKER.downcallHandle(LIBRARY.findOrThrow("CFStringGetCharacters"), FunctionDescriptor.ofVoid(ADDRESS, RANGE, ADDRESS));
    static final MethodHandle RELEASE =
      LINKER.downcallHandle(LIBRARY.findOrThrow("CFRelease"), FunctionDescriptor.ofVoid(ADDRESS));
  }

  static Formats getMacFormats() throws Throwable {
    var localeRef = (MemorySegment)CF.LOCALE_COPY_CURRENT.invokeExact();
    if (localeRef.address() == 0) throw new IllegalStateException("CFLocaleCopyCurrent: null");
    try {
      var date = getMacFormat(localeRef, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterNoStyle);
      var timeShort = getMacFormat(localeRef, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterShortStyle);
      var timeMedium = getMacFormat(localeRef, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterMediumStyle);
      var dateTime = getMacFormat(localeRef, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterShortStyle);
      var localeId = getMacString((MemorySegment)CF.LOCALE_GET_IDENTIFIER.invokeExact(localeRef));
      if (LOG.isTraceEnabled()) LOG.trace("id=" + localeId);
      var locale = getLocaleById(localeId);
      return makeFormats(date, timeShort, timeMedium, dateTime, locale);
    }
    finally {
      CF.RELEASE.invokeExact(localeRef);
    }
  }

  private static String getMacFormat(MemorySegment localeRef, long dateStyle, long timeStyle) throws Throwable {
    var formatter = (MemorySegment)CF.DATE_FORMATTER_CREATE.invokeExact(MemorySegment.NULL, localeRef, dateStyle, timeStyle);
    if (formatter.address() == 0) throw new IllegalStateException("CFDateFormatterCreate: null");
    try {
      return getMacString((MemorySegment)CF.DATE_FORMATTER_GET_FORMAT.invokeExact(formatter));
    }
    finally {
      CF.RELEASE.invokeExact(formatter);
    }
  }

  static String getMacString(MemorySegment ref) throws Throwable {
    if (ref.address() == 0) throw new IllegalStateException("CFString: null");
    var length = Math.toIntExact((long)CF.STRING_GET_LENGTH.invokeExact(ref));
    if (length == 0) return "";
    try (var arena = Arena.ofConfined()) {
      var range = arena.allocate(CF.RANGE);
      range.set(JAVA_LONG, 0, 0L);
      range.set(JAVA_LONG, JAVA_LONG.byteSize(), length);
      var buffer = arena.allocate(JAVA_CHAR, length);
      CF.STRING_GET_CHARACTERS.invokeExact(ref, range, buffer);
      return new String(buffer.toArray(JAVA_CHAR));
    }
  }

  private static @Nullable Locale getUnixLocale() {
    var localeStr = System.getenv("LC_TIME");
    if (LOG.isTraceEnabled()) LOG.trace("LC_TIME=" + localeStr);
    return localeStr == null ? null : getLocaleById(localeStr.trim());
  }

  private static Locale getLocaleById(String localeStr) {
    int p = localeStr.indexOf('.');
    if (p > 0) localeStr = localeStr.substring(0, p);
    p = localeStr.indexOf('@');
    if (p > 0) localeStr = localeStr.substring(0, p);
    p = localeStr.indexOf('_');
    return p < 0 ? Locale.of(localeStr) : Locale.of(localeStr.substring(0, p), localeStr.substring(p + 1));
  }

  private static final class Kernel32 {
    static final int LOCALE_SSHORTDATE = 0x0000001F;
    static final int LOCALE_SSHORTTIME = 0x00000079;
    static final int LOCALE_STIMEFORMAT = 0x00001003;

    static final StructLayout CALL_STATE_LAYOUT = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERROR = CALL_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));
    static final MethodHandle GET_LOCALE_INFO_EX = Linker.nativeLinker().downcallHandle(
      WindowsSystemLibraries.lookup("kernel32.dll").findOrThrow("GetLocaleInfoEx"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT), Linker.Option.captureCallState("GetLastError"));
  }

  static Formats getWindowsFormats() throws Throwable {
    try (var arena = Arena.ofConfined()) {
      var callState = arena.allocate(Kernel32.CALL_STATE_LAYOUT);
      var buffer = arena.allocate(JAVA_CHAR, 128);
      var shortDate = getWindowsFormat(Kernel32.LOCALE_SSHORTDATE, callState, buffer);
      var shortTime = getWindowsFormat(Kernel32.LOCALE_SSHORTTIME, callState, buffer);
      var mediumTime = getWindowsFormat(Kernel32.LOCALE_STIMEFORMAT, callState, buffer);
      var locale = Locale.getDefault(Locale.Category.FORMAT);
      return makeFormats(shortDate, shortTime, mediumTime, locale);
    }
  }

  private static String getWindowsFormat(int localeType, MemorySegment callState, MemorySegment buffer) throws Throwable {
    var capacity = (int)(buffer.byteSize() / JAVA_CHAR.byteSize());
    var result = (int)Kernel32.GET_LOCALE_INFO_EX.invokeExact(callState, MemorySegment.NULL, localeType, buffer, capacity);
    if (result < 2) throw new IllegalStateException("GetLocaleInfoEx: " + (int)Kernel32.LAST_ERROR.get(callState, 0L));
    return fixWindowsFormat(new String(buffer.asSlice(0, (result - 1L) * JAVA_CHAR.byteSize()).toArray(JAVA_CHAR)));
  }

  // https://learn.microsoft.com/en-us/windows/win32/intl/day--month--year--and-era-format-pictures
  // https://learn.microsoft.com/en-us/windows/win32/intl/hour--minute--and-second-format-pictures
  private static String fixWindowsFormat(String format) {
    return format.replace('g', 'G').replace("dddd", "EEEE").replace("ddd", "E").replace("tt", "a").replace("t", "a");
  }

  private static Formats makeFormats(String date, String timeShort, String timeMedium, Locale locale) {
    return makeFormats(date, timeShort, timeMedium, date + ' ' + timeShort, locale);
  }

  private static Formats makeFormats(String date, String timeShort, String timeMedium, String dateTime, Locale locale) {
    return new Formats(
      formatFromString(date, locale),
      formatFromString(timeShort, locale),
      formatFromString(timeMedium, locale),
      formatFromString(dateTime, locale),
      new SimpleDateFormat(date),
      new SimpleDateFormat(dateTime));
  }

  private static DateTimeFormatter formatFromString(String format, Locale locale) {
    try {
      if (LOG.isTraceEnabled()) LOG.trace("'" + format + "' in " + locale);
      return DateTimeFormatter.ofPattern(format.trim(), locale);
    }
    catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("unrecognized format string '" + format + "'");
    }
  }
  //</editor-fold>
}
