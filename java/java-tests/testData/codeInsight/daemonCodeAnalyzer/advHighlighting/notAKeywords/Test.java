import pkg.Bar;
import pkg.enum.Foo;

class Test {
  void m() {
    Bar b = new Bar();
    b.doSomething(Foo.FOO);  // with language level JDK 1.4 'enum' shouldn't be a keyword (see IDEA-67556)
  }
}