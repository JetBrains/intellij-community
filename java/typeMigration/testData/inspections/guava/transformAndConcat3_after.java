import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();

    int i = (int) it.flatMap((o) -> StreamSupport.stream(new Function<String, Iterable<String>>() {
        @Override
        public Iterable<String> apply(String o) {
            if ('a' > 2) {
                return getIterable();
            } else if ('c' < 123) {
                ArrayList<String> strings1 = new ArrayList<>();
                strings1.add(o);
                return strings1;
            }
            return null;
        }
    }.apply(o).spliterator(), false)).count();

  }

  Iterable<String> getIterable() {
    return null;
  }
}