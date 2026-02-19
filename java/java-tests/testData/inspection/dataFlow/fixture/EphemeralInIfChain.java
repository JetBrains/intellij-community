import org.jetbrains.annotations.*;

class Test {
  enum MyEnum {A, B, C}

  void test(MyEnum x) {
    String s = null;
    if (x == MyEnum.A) {
      s = "A";
    }
    else if (x == MyEnum.B) {
      s = "B";
    }
    else if (x == MyEnum.C) {
      s = "C";
    }
    System.out.println(s.<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>()); // reachable if x is null
  }

  void test1(@NotNull MyEnum x) {
    String s = null;
    if (x == MyEnum.A) {
      s = "A";
    }
    else if (x == MyEnum.B) {
      s = "B";
    }
    else if (x == MyEnum.C) {
      s = "C";
    }
    System.out.println(s.trim()); // ephemerably reachable
  }

  void test2(@NotNull MyEnum x) {
    String s = null;
    if (x == MyEnum.A) {
      s = "A";
    }
    else if (x == MyEnum.B) {
      s = "B";
    }
    else if (x == MyEnum.C) {
      s = "C";
    }
    if (s == null) {
      System.out.println("Incompatible class change!");
      return;
    }
    System.out.println(s.trim());
  }
}