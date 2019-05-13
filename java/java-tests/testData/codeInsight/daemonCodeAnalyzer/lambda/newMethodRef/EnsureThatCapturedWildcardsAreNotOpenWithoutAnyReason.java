import java.util.*;
import java.util.stream.Stream;

abstract class Example {
  abstract <E> Optional<E> findById(Class<E> type);
  void main(Stream<Class<? extends String>> stream) {
    stream.map(this::findById).map(Optional::get);
  }
}