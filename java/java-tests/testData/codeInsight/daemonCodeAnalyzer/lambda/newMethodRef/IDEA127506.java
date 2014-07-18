import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

class Validator {
  static boolean thereAreConstraintViolationsIn1(final Stream<Object> objectStream, Validator validator) {
    Stream<Set<List<Object>>> rStream = objectStream
      .map(validator::validate);
    return rStream
      .flatMap(set -> set.stream())
      .findAny()
      .isPresent();
  }

  static void thereAreConstraintViolationsIn(final Stream<Object> objectStream, Validator validator) {
    Stream<Set<List<Object>>> rStream = objectStream.map(validator ::validate);

    Stream<Set<List<Object>>> lStream = objectStream.map((a) -> validator.validate(a));
  }

  <T> Set<List<T>> validate(T var1, Class<?> ... var2) {
    return null;
  }
}