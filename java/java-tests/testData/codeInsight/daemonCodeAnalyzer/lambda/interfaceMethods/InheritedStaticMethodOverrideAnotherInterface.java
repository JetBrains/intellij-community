
interface A {

  static void a() {
    System.out.println("1");
  }
}

interface B {

  void a();
}

interface C   extends A, B { //no error

}

interface C1 extends B{
  <error descr="Static method 'a()' in 'C1' cannot override instance method 'a()' in 'B'">static void a()</error>{}; //error
}
