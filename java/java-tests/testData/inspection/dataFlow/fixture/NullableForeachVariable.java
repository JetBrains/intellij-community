import foo.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Foo {
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

}


