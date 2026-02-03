import java.util.List;

class System {

  // Conflict resolution deliberately not supported
  static void foo(List<String> ls) {
    ls.forEach(System.err::println<caret>);
  }
}