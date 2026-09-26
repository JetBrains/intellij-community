import java.util.function.Consumer;
import java.util.stream.Stream;

class App {
  void usedOnce() {
    Consumer<String> c = s -> {
      if (<warning descr="Condition 's.isEmpty()' is always 'false'">s.isEmpty()</warning>) {
        System.out.println("Empty string!");
      }
    };
    c.accept("foo");
  }

  void usedTwice() {
    Consumer<String> c = s -> {
      if (s.isEmpty()) {
        System.out.println("Empty string!");
      }
    };
    c.accept("foo");
    c.accept("bar");
  }
  
  void inStream() {
    Consumer<String> c = s -> {
      if (<warning descr="Condition 's.isEmpty()' is always 'false'">s.isEmpty()</warning>) {
        System.out.println("Empty string!");
      }
    };
    Stream.of("foo", "bar", "baz").forEach(c);
  }
  
  void inStream2() {
    Consumer<String> c = s -> {
      if (s.isEmpty()) {
        System.out.println("Empty string!");
      }
    };
    Stream.of("foo", "bar", "").forEach(c);
  }
}