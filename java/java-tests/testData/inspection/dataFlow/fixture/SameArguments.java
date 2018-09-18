class X {
  void test(int x, int y) {
    System.out.println(Math.<warning descr="Arguments of 'max()' are always the same which unlikely makes sense">max</warning>(x, x));
    int z = x;
    System.out.println(Math.<warning descr="Arguments of 'min()' are always the same which unlikely makes sense">min</warning>(x, z));
    String s = "foo";
    String t = "foo";
    System.out.println("foobar".<warning descr="Arguments of 'replace()' are always the same which unlikely makes sense">replace</warning>(s, t));
    System.out.println("foobar".<warning descr="Arguments of 'replace()' are always the same which unlikely makes sense">replace</warning>('x', 'x'));
  }

  int testCondition(int a) {
    if(a == 100) return Math.<warning descr="Arguments of 'max()' are always the same which unlikely makes sense">max</warning>(a, 100);
    return 0;
  }
}