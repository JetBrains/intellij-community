import java.util.*;

public class February31 {
  private static final int[] kDaysInMonth = {
    0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
  };

  boolean ValidateDateTime(DateTime time) {
    if (time.year < 1 || time.year > 9999 ||
        time.month < 1 || time.month > 12 ||
        time.day < 1 || time.day > 31 ||
        time.hour < 0 || time.hour > 23 ||
        time.minute < 0 || time.minute > 59 ||
        time.second < 0 || time.second > 59) {
      return false;
    }
    if (time.month == 2 && IsLeapYear(time.year)) {
      return <warning descr="Condition 'time.month <= kDaysInMonth[time.month] + 1' is always 'true'">time.month <= kDaysInMonth[time.month] + 1</warning>;
    } else {
      return time.month <= kDaysInMonth[time.month];
    }
  }

  static boolean IsLeapYear(int year) {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
  }

  static class DateTime {
    int year, month, day, hour, minute, second;
  }
}