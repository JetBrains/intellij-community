interface A {
  static void foo110() {}
}

abstract class B implements A {
  static void foo110(String... strs){
    System.out.println(strs);
  }
  static void bar110(){}
}

abstract class C extends B {
  static void bar110(String... strs){
    System.out.println(strs);
  }
}

class D {
  public static void main(String[] args) {
    B.foo110();
    C.bar110();
  }
}