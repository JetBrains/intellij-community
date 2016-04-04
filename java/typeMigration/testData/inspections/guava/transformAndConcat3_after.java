import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();

    int i = (int) it.flatMap(o -> {
        if ('a' > 2) {
            return StreamSupport.stream(getIterable().spliterator(), false);
        } else if ('c' < 123) {
            ArrayList<String> strings1 = new ArrayList<>();
            strings1.add(o);
            return strings1.stream();
        }
        return null;
    }).count();

  }

  Iterable<String> getIterable() {
    return null;
  }
}