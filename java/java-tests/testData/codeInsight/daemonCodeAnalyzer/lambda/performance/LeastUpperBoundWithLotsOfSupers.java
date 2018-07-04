
import java.util.List;
import java.util.stream.Stream;

class LeastUpperBoundTest {
  void f(Stream<Klass> klasses) {
    klasses.flatMap(d -> concat(Stream.of(d),
                                d.getMethods().stream(),
                                d.getMethods().stream()));
  }

  @SafeVarargs
  private static <T> Stream<T> concat(Stream<? extends T>... streams) {
    return null;
  }
}

interface Node {}
interface A<T extends Node> {}
interface B<T extends Node> {}
interface C<T extends Node> {}
interface D<T extends Node> {}
interface E<T extends Node> {}
interface E1<T extends Node> {}
interface E2<T extends Node> {}

class Klass implements Node, A<Klass>, B<Klass>, C<Klass>, D<Klass>, E<Klass>, E1<Klass> {
  public List<Method> getMethods() {
    return null;
  }
}
class Method implements Node, A<Method>, B<Method>, C<Method>, D<Method>, E<Method>, E1<Method> {}

