import java.time.LocalDateTime;
import java.time.LocalTime;

public class DateTimeComparing {
  public void testLocalDateTime() {
    LocalDateTime now1 = LocalDateTime.now();
    LocalDateTime now2 = LocalDateTime.now();
    if (now1.isAfter(now2)) {
      if (<warning descr="Condition 'now1.isBefore(now2)' is always 'false'">now1.isBefore(now2)</warning>) {

      }
      if (<warning descr="Condition 'now2.isBefore(now1)' is always 'true'">now2.isBefore(now1)</warning>) {
      }
    }
  }

  public void testLocalTime() {
    LocalTime now1 = LocalTime.now();
    LocalTime now2 = LocalTime.now();
    if (now1.isAfter(now2)) {

      if (<warning descr="Condition 'now1.isBefore(now2)' is always 'false'">now1.isBefore(now2)</warning>) {

      }
      if (<warning descr="Condition 'now2.isBefore(now1)' is always 'true'">now2.isBefore(now1)</warning>) {

      }
    }
  }
}
