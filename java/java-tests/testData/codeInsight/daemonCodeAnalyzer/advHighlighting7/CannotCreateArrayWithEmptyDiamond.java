import java.util.ArrayList;

class Test {
  void foo() {
    ArrayList<String>[] lists1 = <error descr="Cannot create array with '<>'">new ArrayList<>[5]</error>;
    ArrayList<String>[] lists2 = <error descr="Cannot create array with '<>'">new ArrayList</*blah blah blah*/>[5]</error>;
  }
}
