class Test {
  void test1(Object o) {
    int a;
    switch (o) {
      case ((String s)): {
        if (<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {
          System.out.println();
        }
        a = -1;
        break;
      }
      case default: {
        a = 2;
        break;
      }
    }
    if (a < 0) {
      System.out.println(a);
    }
  }

  void test2(Object o) {
    int a;
    switch (o) {
      case (String s): {
        if (<warning descr="Condition 's instanceof CharSequence' is always 'true'">s instanceof CharSequence</warning>) {
          System.out.println(s);
        }
        a = 1;
        break;
      }
      case default: {
        a = 2;
        break;
      }
    }
    if (<warning descr="Condition 'a < 0' is always 'false'">a < 0</warning>) {
      System.out.println(a);
    }
  }

  final String FSD = "fsd";
  int test3() {
    return switch(FSD) {
      case <warning descr="Switch label '(String s && s.length() <= 3) && (s.length() > 1 || s.length() > 10)' is the only reachable in the whole switch">(String s && <warning descr="Condition 's.length() <= 3' is always 'true'">s.length() <= 3</warning>) && (<warning descr="Condition 's.length() > 1 || s.length() > 10' is always 'true'"><warning descr="Condition 's.length() > 1' is always 'true'">s.length() > 1</warning> || s.length() > 10</warning>)</warning> -> 1;
      case "fsd" -> 2;
      case default -> 3;
    };
  }

  int test4(String s) {
    s = FSD;
    return switch (s) {
      case (((String ss) && (<warning descr="Condition 'ss.length() < 3 || ss.length() == 4' is always 'false'"><warning descr="Condition 'ss.length() < 3' is always 'false'">ss.length() < 3</warning> || <warning descr="Condition 'ss.length() == 4' is always 'false' when reached">ss.length() == 4</warning></warning>))) -> 1;
      case <warning descr="Switch label '\"fsd\"' is the only reachable in the whole switch">"fsd"</warning> -> 2;
      case default -> 3;
    };
  }

  void test5() {
    switch (FSD) {
      case <warning descr="Switch label 'String s && s.length() > 2 && s.length() < 3' is unreachable">String s && <warning descr="Condition 's.length() > 2 && s.length() < 3' is always 'false'"><warning descr="Condition 's.length() > 2' is always 'true'">s.length() > 2</warning> && <warning descr="Condition 's.length() < 3' is always 'false' when reached">s.length() < 3</warning></warning></warning> -> System.out.println(1);
      case <warning descr="Switch label '\"abc\"' is unreachable">"abc"</warning> -> System.out.println(2);
      case default -> System.out.println(3);
    };
  }

  void test6() {
    String s = "abc";
    switch (s) {
      case Object o -> System.out.println("total");
    }
  }

  void test7() {
    String s = "abc";
    switch (s) {
      case <error descr="'switch' has both a total pattern and a default label">Object o</error> -> System.out.println("total");
      case <error descr="'switch' has both a total pattern and a default label">default</error> -> System.out.println("default");
    }
  }

  void test8() {
    String s = "abc";
    switch (s) {
      case <warning descr="Switch label '\"\"' is unreachable">""</warning> -> System.out.println("abc");
      case <error descr="'switch' has both a total pattern and a default label">Object o</error> -> System.out.println("total");
      case <error descr="'switch' has both a total pattern and a default label">default</error> -> System.out.println("default");
    }
  }

  void test9() {
    String s = "abc";
    switch (s) {
      case <warning descr="Switch label 'Object o' is the only reachable in the whole switch">Object o</warning> -> System.out.println("total");
      case <error descr="Label is dominated by a preceding case label 'Object o'">"abc"</error> -> System.out.println("abc");
    }
  }
}