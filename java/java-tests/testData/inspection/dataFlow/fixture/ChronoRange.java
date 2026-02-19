import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.temporal.ChronoField;

class Test {
  private static void skipped(OffsetTime offsetTime, LocalDateTime localDateTime, LocalDate localDate, LocalTime localTime) {
    if (offsetTime.get(ChronoField.INSTANT_SECONDS) == 0)
    {
      System.out.println("1");
    }
    if (localDateTime.get(ChronoField.OFFSET_SECONDS) > 1_000_000_000)
    {
      System.out.println("2");
    }
    if (localDate.get(ChronoField.MINUTE_OF_HOUR) > 1000)
    {
      System.out.println("3");
    }
    if (localTime.get(ChronoField.ERA) > 4)
    {
      System.out.println("4");
    }
    localDate.get(ChronoField.EPOCH_DAY);
  }
  private static void checked(OffsetTime offsetTime, LocalDateTime localDateTime, LocalDate localDate, LocalTime localTime) {
    if(<warning descr="Condition 'offsetTime.get(ChronoField.OFFSET_SECONDS)>1_000_000_000' is always 'false'">offsetTime.get(ChronoField.OFFSET_SECONDS)>1_000_000_000</warning>)
    {
      System.out.println("1");
    }
    if (<warning descr="Condition 'localDate.get(ChronoField.ERA) > 2' is always 'false'">localDate.get(ChronoField.ERA) > 2</warning>)
    {
      System.out.println("2");
    }
    if (<warning descr="Condition 'localTime.getLong(ChronoField.MINUTE_OF_HOUR) < 0' is always 'false'">localTime.getLong(ChronoField.MINUTE_OF_HOUR)  < 0</warning>)
    {
      System.out.println("3");
    }
    if (<warning descr="Condition 'localDateTime.getLong(ChronoField.MINUTE_OF_HOUR) < 0' is always 'false'">localDateTime.getLong(ChronoField.MINUTE_OF_HOUR) < 0</warning>)
    {
      System.out.println("4");
    }
  }
}