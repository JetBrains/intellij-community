// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.DynamicBundle;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.UtilBundle;
import com.intellij.util.text.DateTimeFormatManager.Formats;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

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

  /** @deprecated use {@link java.util.concurrent.TimeUnit#toMillis} */
  @Deprecated(forRemoval = true) public static final long SECOND = 1_000L;
  /** @deprecated use {@link java.util.concurrent.TimeUnit#toMillis} */
  @Deprecated(forRemoval = true) public static final long MINUTE = 60 * SECOND;
  /** @deprecated use {@link java.util.concurrent.TimeUnit#toMillis} */
  @Deprecated(forRemoval = true) public static final long HOUR = 60 * MINUTE;
  /** @deprecated use {@link java.util.concurrent.TimeUnit#toMillis} */
  @Deprecated(forRemoval = true) public static final long DAY = 24 * HOUR;
  /** @deprecated use {@link java.util.concurrent.TimeUnit#toMillis} */
  @Deprecated(forRemoval = true) public static final long WEEK = 7 * DAY;
  /** @deprecated use {@link java.util.concurrent.TimeUnit#toMillis} */
  @Deprecated(forRemoval = true) public static final long MONTH = 30 * DAY;
  /** @deprecated use {@link java.util.concurrent.TimeUnit#toMillis} */
  @Deprecated(forRemoval = true) public static final long YEAR = 365 * DAY;
  /** @deprecated use {@link java.util.concurrent.TimeUnit#toMillis} */
  @Deprecated(forRemoval = true) public static final long DAY_FACTOR = DAY;

  public static final String TIME_SHORT_12H = "h:mm\u202Fa";
  public static final String TIME_SHORT_24H = "HH:mm";

  private static final String TIME_MEDIUM_12H = "h:mm:ss\u202Fa";
  private static final String TIME_MEDIUM_24H = "HH:mm:ss";

  private static final long[] DENOMINATORS = {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE};
  private enum Period {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE}
  private static final Period[] PERIODS = {Period.YEAR, Period.MONTH, Period.WEEK, Period.DAY, Period.HOUR, Period.MINUTE};

  private DateFormatUtil() { }

  /** @deprecated use {@link Duration#between} */
  @Deprecated(forRemoval = true)
  public static long getDifferenceInDays(@NotNull Date startDate, @NotNull Date endDate) {
    return (endDate.getTime() - startDate.getTime() + DAY - 1000) / DAY;
  }

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

  /** @deprecated use {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("removal")
  public static @NotNull SyncDateFormat getIso8601Format() {
    var iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));
    return new SyncDateFormat(iso8601);
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
        if (SystemInfo.isMac && JnaLoader.isLoaded()) return getMacFormats();
        if (SystemInfo.isWindows && JnaLoader.isLoaded()) return getWindowsFormats();
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

  private interface CF extends Library {
    long kCFDateFormatterNoStyle = 0;
    long kCFDateFormatterShortStyle = 1;
    long kCFDateFormatterMediumStyle = 2;

    @Structure.FieldOrder({"location", "length"})
    final class CFRange extends Structure implements Structure.ByValue {
      public long location, length;

      public CFRange(long location, long length) {
        this.location = location;
        this.length = length;
      }
    }

    Pointer CFLocaleCopyCurrent();
    Pointer CFLocaleGetIdentifier(Pointer locale);
    Pointer CFDateFormatterCreate(Pointer allocator, Pointer locale, long dateStyle, long timeStyle);
    Pointer CFDateFormatterGetFormat(Pointer formatter);
    long CFStringGetLength(Pointer str);
    void CFStringGetCharacters(Pointer str, CFRange range, char[] buffer);
    void CFRelease(Pointer p);
  }

  private static Formats getMacFormats() {
    var cf = Native.load("CoreFoundation", CF.class);
    var localeRef = cf.CFLocaleCopyCurrent();
    try {
      var date = getMacFormat(cf, localeRef, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterNoStyle);
      var timeShort = getMacFormat(cf, localeRef, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterShortStyle);
      var timeMedium = getMacFormat(cf, localeRef, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterMediumStyle);
      var dateTime = getMacFormat(cf, localeRef, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterShortStyle);
      var localeId = getMacString(cf, cf.CFLocaleGetIdentifier(localeRef));
      if (LOG.isTraceEnabled()) LOG.trace("id=" + localeId);
      var locale = getLocaleById(localeId);
      return makeFormats(date, timeShort, timeMedium, dateTime, locale);
    }
    finally {
      cf.CFRelease(localeRef);
    }
  }

  private static String getMacFormat(CF cf, Pointer localeRef, long dateStyle, long timeStyle) {
    var formatter = cf.CFDateFormatterCreate(null, localeRef, dateStyle, timeStyle);
    if (formatter == null) throw new IllegalStateException("CFDateFormatterCreate: null");
    try {
      var format = cf.CFDateFormatterGetFormat(formatter);
      return getMacString(cf, format);
    }
    finally {
      cf.CFRelease(formatter);
    }
  }

  private static String getMacString(CF cf, Pointer ref) {
    var length = (int)cf.CFStringGetLength(ref);
    var buffer = new char[length];
    cf.CFStringGetCharacters(ref, new CF.CFRange(0, length), buffer);
    return new String(buffer);
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
    return p < 0 ? new Locale(localeStr) : new Locale(localeStr.substring(0, p), localeStr.substring(p + 1));
  }

  @SuppressWarnings("SpellCheckingInspection")
  private interface Kernel32 extends StdCallLibrary {
    int LOCALE_SSHORTDATE  = 0x0000001F;
    int LOCALE_SSHORTTIME  = 0x00000079;
    int LOCALE_STIMEFORMAT = 0x00001003;

    int GetLocaleInfoEx(String localeName, int lcType, char[] lcData, int dataSize);
    int GetLastError();
  }

  private static Formats getWindowsFormats() {
    var kernel32 = Native.load("Kernel32", Kernel32.class);
    int bufferSize = 128, rv;
    var buffer = new char[bufferSize];

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_SSHORTDATE, buffer, bufferSize);
    if (rv < 2) throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    var shortDate = fixWindowsFormat(new String(buffer, 0, rv - 1));

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_SSHORTTIME, buffer, bufferSize);
    if (rv < 2) throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    var shortTime = fixWindowsFormat(new String(buffer, 0, rv - 1));

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_STIMEFORMAT, buffer, bufferSize);
    if (rv < 2) throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    var mediumTime = fixWindowsFormat(new String(buffer, 0, rv - 1));

    var locale = Locale.getDefault(Locale.Category.FORMAT);
    return makeFormats(shortDate, shortTime, mediumTime, locale);
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
