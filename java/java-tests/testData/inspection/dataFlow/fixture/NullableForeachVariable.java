import foo.Nullable;

import java.util.ArrayList;
import java.util.List;

class Foo {
  void foo1(List<Integer> list) {
    for (@Nullable Integer i : list) {
      System.out.println(i.<warning descr="Method invocation 'intValue' may produce 'java.lang.NullPointerException'">intValue</warning>());
    }
  }
  void foo2(List<@Nullable Integer> list) {
    for (@Nullable Integer i : list) {
      System.out.println(i.<warning descr="Method invocation 'intValue' may produce 'java.lang.NullPointerException'">intValue</warning>());
    }
  }
  void foo3() {
    List<@Nullable String> list = new ArrayList<>();
    list.add(null);
    for (String s : list) {
      System.out.println(s.<warning descr="Method invocation 'length' may produce 'java.lang.NullPointerException'">length</warning>());
    }
  }

}


