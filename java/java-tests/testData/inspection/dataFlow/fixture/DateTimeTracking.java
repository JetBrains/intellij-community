import java.time.*;
import java.time.chrono.*;
import java.time.temporal.ChronoUnit;

public class DateTimeTracking {
  public static void compareLocalDate() {
    LocalDate localDate1 = LocalDate.of(2011, 1, 1);
    LocalDate localDate2 = LocalDate.of(2011, Month.APRIL, 1);
    if (<warning descr="Condition 'localDate1.isBefore(localDate2)' is always 'true'">localDate1.isBefore(localDate2)</warning>) {

    }

    if (<warning descr="Condition 'localDate1.plus(1, ChronoUnit.YEARS).isAfter(localDate2)' is always 'true'">localDate1.plus(1, ChronoUnit.YEARS).isAfter(localDate2)</warning>) {

    }

    if (<warning descr="Condition 'localDate1.plusYears(1).getYear() == 2012' is always 'true'">localDate1.plusYears(1).getYear() == 2012</warning>) {
    }

    if (<warning descr="Condition 'LocalDate.ofYearDay(2011, 1).getMonthValue() == 1' is always 'true'">LocalDate.ofYearDay(2011, 1).getMonthValue() == 1</warning>) {
    }

    LocalDate now = LocalDate.now();
    if (now.isAfter(localDate2)) {
      if (<warning descr="Condition 'now.isAfter(localDate1)' is always 'true'">now.isAfter(localDate1)</warning>) {

      }
    }

    LocalDate now2 = LocalDate.now();
    if (now2.isEqual(localDate1)) {
      if (<warning descr="Condition 'now2.isAfter(localDate1)' is always 'false'">now2.isAfter(localDate1)</warning>) {

      }
    }

    if (now.isEqual(now2)) {
      if (<warning descr="Condition 'now.equals(now2)' is always 'true'">now.equals(now2)</warning>) {

      }
    }

    if (now.isBefore(now2)) {
      if (<warning descr="Condition 'now.minusYears(1).isBefore(now2.plus(1, ChronoUnit.YEARS))' is always 'true'">now.minusYears(1).isBefore(now2.plus(1, ChronoUnit.YEARS))</warning>) {

      }
    }

    if (now.isBefore(localDate1)) {
      if (now2.isAfter(localDate2)) {
        if (<warning descr="Condition 'now.isBefore(now2)' is always 'true'">now.isBefore(now2)</warning>) {

        }
      }
    }

    ChronoLocalDate outTracking = new ChronoLocalDate() {
    };

    if (localDate2.isAfter(outTracking)) {
      if (localDate1.isAfter(outTracking)) {

      }
    }
  }

  public static void compareLocalTime() {
    LocalTime localTime1 = LocalTime.of(10, 10, 2);
    LocalTime localTime2 = LocalTime.of(10, 10, 2, 1);
    if (<warning descr="Condition 'localTime1.isBefore(localTime2)' is always 'true'">localTime1.isBefore(localTime2)</warning>) {

    }

    if (<warning descr="Condition 'localTime1.minus(-2, ChronoUnit.NANOS).isAfter(localTime2)' is always 'true'">localTime1.minus(-2, ChronoUnit.NANOS).isAfter(localTime2)</warning>) {

    }

    if (<warning descr="Condition 'localTime1.withHour(1).getHour() == 1' is always 'true'">localTime1.withHour(1).getHour() == 1</warning>) {

    }

    LocalTime now = LocalTime.now();
    if (now.isAfter(localTime2)) {
      if (<warning descr="Condition 'now.isAfter(localTime1)' is always 'true'">now.isAfter(localTime1)</warning>) {

      }
    }

    LocalTime now2 = LocalTime.now();
    if (now2.equals(localTime1)) {
      if (<warning descr="Condition 'now2.isAfter(localTime1)' is always 'false'">now2.isAfter(localTime1)</warning>) {

      }
    }

    if (now.isBefore(now2)) {
      if (<warning descr="Condition 'now.plusHours(-1).isBefore(now2.minus(-1, ChronoUnit.HOURS))' is always 'true'">now.plusHours(-1).isBefore(now2.minus(-1, ChronoUnit.HOURS))</warning>) {

      }
    }

    if (now.isBefore(localTime1)) {
      if (now2.isAfter(localTime2)) {
        if (<warning descr="Condition 'now.isBefore(now2)' is always 'true'">now.isBefore(now2)</warning>) {

        }
      }
    }
  }

  public static void compareLocalDateTime() {
    LocalDateTime localDateTime1 = LocalDateTime.of(2011, 1, 1, 12, 10);
    LocalDateTime localDateTime2 = LocalDateTime.of(2011, Month.APRIL, 1, 12, 10, 5);
    if (<warning descr="Condition 'localDateTime1.isBefore(localDateTime2)' is always 'true'">localDateTime1.isBefore(localDateTime2)</warning>) {

    }

    if (<warning descr="Condition 'localDateTime1.minus(-2, ChronoUnit.NANOS).isAfter(localDateTime2)' is always 'false'">localDateTime1.minus(-2, ChronoUnit.NANOS).isAfter(localDateTime2)</warning>) {

    }

    if (<warning descr="Condition 'localDateTime1.plusHours(13).getHour() == 1' is always 'true'">localDateTime1.plusHours(13).getHour() == 1</warning>) {

    }

    LocalDateTime now = LocalDateTime.now();
    if (now.isAfter(localDateTime2)) {
      if (<warning descr="Condition 'now.isAfter(localDateTime1)' is always 'true'">now.isAfter(localDateTime1)</warning>) {

      }
    }

    LocalDateTime now2 = LocalDateTime.now();
    if (now2.isEqual(localDateTime1)) {
      if (<warning descr="Condition 'now2.isAfter(localDateTime1)' is always 'false'">now2.isAfter(localDateTime1)</warning>) {

      }
    }

    if (now.isEqual(now2)) {
      if (<warning descr="Condition 'now.equals(now2)' is always 'true'">now.equals(now2)</warning>) {

      }
    }

    if (now.isBefore(localDateTime1)) {
      if (now2.isAfter(localDateTime2)) {
        if (<warning descr="Condition 'now.isBefore(now2)' is always 'true'">now.isBefore(now2)</warning>) {

        }
      }
    }

    ChronoLocalDateTime<LocalDate> outTracking = new ChronoLocalDateTime<LocalDate>() {};

    if (localDateTime1.isBefore(outTracking)) {
      if (localDateTime2.isBefore(outTracking)) {

      }
    }
  }

  public static void compareOffsetChronos() {
    OffsetDateTime offsetDateTime1 = OffsetDateTime.of(LocalDate.now(), LocalTime.now(), ZoneOffset.UTC);
    OffsetDateTime offsetDateTime2 = OffsetDateTime.of(LocalDate.now(), LocalTime.now(), ZoneOffset.UTC);

    if (offsetDateTime1.isBefore(offsetDateTime2)) {
      if (<warning descr="Condition 'offsetDateTime1.isEqual(offsetDateTime2)' is always 'false'">offsetDateTime1.isEqual(offsetDateTime2)</warning>) {

      }
    }


    OffsetTime offsetTime1 = OffsetTime.of(LocalTime.now(), ZoneOffset.UTC);
    OffsetTime offsetTime2 = OffsetTime.of(LocalTime.now(), ZoneOffset.UTC);

    if (offsetTime1.isBefore(offsetTime2)) {
      if (<warning descr="Condition 'offsetTime1.isAfter(offsetTime2)' is always 'false'">offsetTime1.isAfter(offsetTime2)</warning>) {

      }
    }

    if (offsetTime1.isBefore(offsetTime2)) {
      if (<warning descr="Condition 'offsetTime1.isBefore(offsetTime2.plusHours(1))' is always 'true'">offsetTime1.isBefore(offsetTime2.plusHours(1))</warning>) {

      }
    }

    try {
      OffsetTime plus = offsetTime1.<warning descr="The call to 'plus' always fails as an argument is out of bounds">plus</warning>(1, ChronoUnit.YEARS);
    } catch (Exception e) {

    }

    ZonedDateTime zonedDateTime3 = ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC);
    ZonedDateTime zonedDateTime2 = ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.UTC);


    if (zonedDateTime3.isBefore(zonedDateTime2)) {
      if (<warning descr="Condition 'zonedDateTime3.isAfter(zonedDateTime2)' is always 'false'">zonedDateTime3.isAfter(zonedDateTime2)</warning>) {

      }
    }
  }
}