import foo.Nullable;

import java.util.ArrayList;
import java.util.List;

class Foo {
  void foo1(List<Integer> list) {
    for (@Nullable Integer i : list) {
      System.out.println(<warning descr="Method invocation 'i.intValue()' may produce 'java.lang.NullPointerException'">i.intValue()</warning>);
    }
  }
  void foo2(List<@Nullable Integer> list) {
    for (@Nullable Integer i : list) {
      System.out.println(<warning descr="Method invocation 'i.intValue()' may produce 'java.lang.NullPointerException'">i.intValue()</warning>);
    }
  }
  void foo3() {
    List<@Nullable String> list = new ArrayList<>();
    list.add(null);
    for (String s : list) {
      System.out.println(<warning descr="Method invocation 's.length()' may produce 'java.lang.NullPointerException'">s.length()</warning>);
    }
  }

}


