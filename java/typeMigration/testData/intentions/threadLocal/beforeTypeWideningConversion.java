// "Convert to 'ThreadLocal'" "true"
import java.util.Date;

class X {
  private static DateFormat <caret>dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");

  public String getDateString() {
    return dateFormat.format(new Date());
  }
}
abstract class DateFormat {

  public final String format(Date date) {
    return date.toString();
  }
}
class SimpleDateFormat extends DateFormat {
  public SimpleDateFormat(String patten) {}
}