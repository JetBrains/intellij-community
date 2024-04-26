class Test {
  public static String testReach(Object o1, Object o2) {
    return switch (o1) {
      case String _ when o2 instanceof String s: yield s;
      case Object _: yield "strange";
    };
  }

  record R(int x, int y) {}
  void test(Object obj) {
    if (obj instanceof R(_, var b)) {
      return;
    }
    if (<warning descr="Condition 'obj instanceof R(var a, var b)' is always 'false'">obj instanceof R(var a, var b)</warning>) {
      return;
    }
    if (<warning descr="Condition 'obj instanceof R(int a, _)' is always 'false'">obj instanceof R(int a, _)</warning>) {
      return;
    }
  }

  public static void main(String[] args) {
    System.out.println(testReach("1", "2"));
  }
}