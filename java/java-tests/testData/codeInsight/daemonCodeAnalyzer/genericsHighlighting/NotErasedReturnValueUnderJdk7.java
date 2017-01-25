import java.util.*;

class Test {

  void foo() {
    set<error descr="'set(java.util.List<java.lang.String>)' in 'Test' cannot be applied to '(java.util.ArrayList<java.lang.Object>)'">(new ArrayList<>(new Vector()))</error>;
  }

  void set(List<String> list) {
  }

}