// "Replace with collect" "false"
import java.util.List;

class A {
  void foo(List<List<String>> list) {
    for(List<String> l: li<caret>st) {
      l.add("");
    }
  }
}