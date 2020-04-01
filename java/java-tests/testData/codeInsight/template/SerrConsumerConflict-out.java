import java.util.List;

class System {

  static void foo(List<String> ls) {
    ls.forEach(java.lang.System.err::println<caret>);
  }
}