import java.util.Objects;

// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo(Object o) {
      if (Objects.requireNonNull(o) instanceof String) {
          System.out.println("one");
      } else if (o instanceof Integer) {
          System.out.println("two");
      } else {
          System.out.println("default");
      }
  }
}