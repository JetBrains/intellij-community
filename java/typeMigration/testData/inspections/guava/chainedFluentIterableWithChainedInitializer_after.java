import java.util.ArrayList;
import java.util.stream.Stream;

class A {
  int m1() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream().map(s -> s + s);

    return (int) it.skip(10).count();
  }
}