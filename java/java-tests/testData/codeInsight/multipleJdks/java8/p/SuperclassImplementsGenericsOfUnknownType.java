package p;
import java.util.stream.Stream;
import java.util.List;

public abstract class A implements List<Stream<String>> {
}
abstract class A1 extends A {}

class AList<T> {}
class A2 extends AList<A> {}