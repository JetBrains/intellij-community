class X {
  void test(int x, int y) {
    System.out.println(Math.<warning descr="Arguments of 'max' are the same. Calling this method with the same arguments is meaningless.">max</warning>(x, x));
    int z = x;
    System.out.println(Math.<warning descr="Arguments of 'min' are the same. Calling this method with the same arguments is meaningless.">min</warning>(x, z));
    String s = "foo";
    String t = "foo";
    System.out.println("foobar".<warning descr="Arguments of 'replace' are the same. Calling this method with the same arguments is meaningless.">replace</warning>(s, t));
    System.out.println("foobar".<warning descr="Arguments of 'replace' are the same. Calling this method with the same arguments is meaningless.">replace</warning>('x', 'x'));
  }

  int testCondition(int a) {
    if(a == 100) return Math.<warning descr="Arguments of 'max' are the same. Calling this method with the same arguments is meaningless.">max</warning>(a, 100);
    return 0;
  }
}