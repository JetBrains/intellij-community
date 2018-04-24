import java.util.stream.Stream;
public class Outer {

  Abstract[] foo(Stream<Abstract> s) {
    return s.toArray(Abstract[]::new)<caret>;
  }
}

abstract class Abstract {}