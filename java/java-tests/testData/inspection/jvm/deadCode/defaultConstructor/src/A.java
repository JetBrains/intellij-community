class A {
   public A(int... i) {
     System.out.println(i);
   }
}

class B extends A {
  public static void main(String[] args) {
    System.out.println(new B());
  }
}

class C {}
class D extends C {
  public static void main(String[] args) {
    System.out.println(new D());
  }
}
