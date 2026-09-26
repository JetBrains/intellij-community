import java.util.Arrays;
import java.util.stream.Stream;

class A {
  int m1() {
    String[] strings = new String[10];
    Stream<String> it = Arrays.stream(strings).map(s -> s + s);

    return (int) it.skip(10).count();
  }
}