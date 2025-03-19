import java.sql.Date;
import java.time.LocalDate;

public class Foo {
  void m(LocalDate o) {
      Date.valueOf(o)<caret>
  }
}