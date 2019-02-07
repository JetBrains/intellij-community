interface A {
  void m();
}
class B implements A {
  public void <caret>m() {
    System.out.println("foo");
  } 
  
  static {
    System.out.println("foo");
  }
}