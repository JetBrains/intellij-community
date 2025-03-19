import java.time.LocalDate;
import java.util.Date;

public class Foo {
  void m(Date o) {
      LocalDate.ofInstant(o.toInstant(), <caret>)
  }
}