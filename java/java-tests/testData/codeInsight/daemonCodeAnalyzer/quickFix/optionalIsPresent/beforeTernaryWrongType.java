// "Replace Optional.isPresent() condition with functional style expression" "false"

import java.time.LocalDate;
import java.util.Optional;

public class OptionalTest {
  Optional<LocalDate> date = Optional.of(LocalDate.now());

  @Override
  public String toString() {
    final String x = "{date:" + (date.isPres<caret>ent() ? date.get() : "<missing>") + '}';
    return x;
  }
}
