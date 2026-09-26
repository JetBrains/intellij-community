// IDEA-172232
final class MyClass {
  int x;

  public void test(MyClass c) {
    try {
      c.x = 5;
    }
    catch (NullPointerException ignore) {}
    if(c == null) { // Condition 'c == null' is always 'false'
      System.out.println("possible");
    }
  }
  
  void test2(MyClass c) {
    try {
      c.x = 5;
    }
    catch (NullPointerException npe) {
      if (<warning descr="Condition 'c == null' is always 'true'">c == null</warning>) {}
    }
    catch (RuntimeException re) {
      // Never visited by DFA
      if (c == null) {}
    }
  }
  
  void test3(MyClass c) {
    try {
      c.hashCode();
    }
    catch (NullPointerException npe) {
      // Can be thrown from inside the hashCode
      if (c == null) {}
    }
    catch (RuntimeException re) {
      if (<warning descr="Condition 'c == null' is always 'false'">c == null</warning>) {}
    }
  }
}