import java.util.List;

final class MainTest {
  int x = 0;

  void increment() {
    x++;
  }

  void test(List<String> list) {
    if (list.isEmpty()) return;
    increment(); // mutates this, so should not flush list
    if (<warning descr="Condition 'list.isEmpty()' is always 'false'">list.isEmpty()</warning>) return;
    System.out.println("hello");
  }
}