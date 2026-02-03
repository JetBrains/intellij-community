import foo.bar.one.*;
import foo.bar.two.*;
class Main {
  void foo() throws ReflectiveOperationException {
    Class.forName("foo.bar.one");
  }
}