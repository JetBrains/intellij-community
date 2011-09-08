import java.util.*;

class Test {
  void foo(final ArrayList<String> anObject) {
    List<String> ls = anObject;
    List<String> lss = anObject;
    List<Integer> li = new ArrayList<>();
  }
  
  void bar() {
    foo(new ArrayList<String>());
  }
}