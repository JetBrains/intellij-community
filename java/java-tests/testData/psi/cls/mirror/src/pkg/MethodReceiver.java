package pkg;

class MethodReceiver {
  @interface A { }
  void m(@A MethodReceiver this, int i) { System.out.println("i=" + i); }
}
