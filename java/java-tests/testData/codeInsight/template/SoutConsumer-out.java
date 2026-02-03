import java.util.List;

class Foo {

  static void foo(List<String> ls) {
    ls.forEach(System.out::println<caret>);
  }
}