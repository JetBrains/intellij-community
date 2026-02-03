
import java.util.List;

class Main {
  static List<? extends Child> foo() {
    return get ();
  }

  static <T extends Base> List<? extends T> get() {
    return null;
  }

  static class Base {}
  static class Child extends Base {}
}
