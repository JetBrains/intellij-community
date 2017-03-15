import java.time.LocalDateTime;

public class LongRangeKnownMethods {
  void testIndexOf(String s) {
    int idx = s.indexOf("xyz");
    if(idx >= 0) {
      System.out.println("Found");
    } else if(<warning descr="Condition 'idx == -1' is always 'true'">idx == -1</warning>) {
      System.out.println("Not found");
    }
  }

  void testLocalDateTime(LocalDateTime ldt) {
    if(<warning descr="Condition 'ldt.getHour() == 24' is always 'false'">ldt.getHour() == 24</warning>) System.out.println(1);
    if(<warning descr="Condition 'ldt.getMinute() >= 0' is always 'true'">ldt.getMinute() >= 0</warning>) System.out.println(2);
    if(<warning descr="Condition 'ldt.getSecond() >= 60' is always 'false'">ldt.getSecond() >= 60</warning>) System.out.println(3);
  }

  private static int twiceIndexOf(String text, int start, int end) {
    int paragraphStart = text.lastIndexOf("\n\n", start);
    int paragraphEnd = text.indexOf("\n\n", end);
    if (paragraphStart >= paragraphEnd) {
      return text.length();
    }
    return (paragraphStart >= 0 ? paragraphStart + 2 : 0) +
           (<warning descr="Condition 'paragraphEnd < 0' is always 'false' when reached">paragraphEnd < 0</warning> ? text.length() : paragraphEnd);
  }
}
