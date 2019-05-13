// "Change type of 'list' to 'List<CharSequence>' and remove cast" "true"
import java.util.*;

class Test {
  List<CharSequence> getList() {
    return Collections.singletonList("foo");
  }

  void test() {
    List<? extends CharSequence> list = getList();
    foo((List<<caret>CharSequence>)list);
    //bar((List<String>)list);
  }

  void foo(List<CharSequence> list) {}
  void bar(List<String> list) {}
}