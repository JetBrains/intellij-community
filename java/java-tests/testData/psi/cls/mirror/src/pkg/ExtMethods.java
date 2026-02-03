package pkg;

interface ExtMethods {
  void m1();
  void m2() default { System.out.println("Hello there."); }
}
