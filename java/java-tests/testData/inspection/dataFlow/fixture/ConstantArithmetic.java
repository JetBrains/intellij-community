class Test {
  {
    int a = 0;
    int b = a;
    System.out.println(<warning descr="Condition 'a == b' is always 'true'">a == b</warning>);
    System.out.println(a - b);
  }

  {
    int a = 1;
    int b = -3;
    System.out.println(<warning descr="Condition 'a == b' is always 'false'">a == b</warning>);
    if (<warning descr="Condition 'a + b + 2 == 0' is always 'true'">a + b + 2 == 0</warning>) {
      System.out.println("a");
    }
  }
}