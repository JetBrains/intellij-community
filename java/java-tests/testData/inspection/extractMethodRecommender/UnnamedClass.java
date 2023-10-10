static int testSomething() {
  return 1;
}

public void main() {
  <weak_warning descr="It's possible to extract method returning 'y' from a long surrounding method">int x = testSomething();</weak_warning>
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  x += testSomething();
  int y = x * 2;
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
  System.out.println(y);
}

