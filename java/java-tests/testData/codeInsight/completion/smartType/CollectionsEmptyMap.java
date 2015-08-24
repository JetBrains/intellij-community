import java.util.Map;

class Foo {
  void m() {
    Map<String, Integer> m = emptyM<caret>
  }

}