import java.time.LocalDate;

public class Foo {
  void m(LocalDate o) {
    o.toSqlDate<caret>
  }
}