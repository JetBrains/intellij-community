import java.util.ArrayList;
import java.util.stream.Stream;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();

    int i = (int) it.filter(String::isEmpty).filter(String.class::isInstance).count();
  }
}