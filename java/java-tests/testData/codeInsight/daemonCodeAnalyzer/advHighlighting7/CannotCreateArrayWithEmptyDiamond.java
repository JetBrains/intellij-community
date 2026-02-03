import java.util.ArrayList;

class Test {
  void foo() {
    ArrayList<String>[] lists1 = new ArrayList<error descr="Array creation with '<>' not allowed"><></error>[5];
    ArrayList<String>[] lists2 = new ArrayList<error descr="Array creation with '<>' not allowed"></*blah blah blah*/></error>[5];
  }
}
