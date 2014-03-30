import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  public <T2> void validate(Stream<ConstraintViolation<T2>> stream) {
    // ...
    Set<Violation> violations = stream.map(this::convertToResult).collect(Collectors.toSet());
    // ...
  }

  private <T1> Violation convertToResult(ConstraintViolation<T1> violation) {
    // ...
    return new Violation();
  }

  class Violation {}

  class ConstraintViolation<T> {
  }
}
