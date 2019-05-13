import java.util.stream.Stream;
public class Outer {

  Abstract[] foo(Stream<Abstract> s) {
    return s.toArray(Ab<caret>);
  }
}

abstract class Abstract {}