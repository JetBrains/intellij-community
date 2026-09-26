class Test {
  static class Cls2 {
    static int value;
  }
  
  void test() {
    try {
      int x = Cls2.value;
    }
    catch (NoClassDefFoundError ex) {
      System.out.println("No class");
    }
  }
  
  void test2() {
    try {
      int x = Cls2.value;
    }
    catch (StackOverflowError ex) {
      System.out.println("SO");
    }
  }
  
  void test3() {
    try {
      int x = Cls2.value;
    }
    catch (AssertionError ex) <warning descr="Unreachable code">{
      System.out.println("AE");
    }</warning>
  }
  
  void test4() {
    try {
      int x = Cls2.value;
    }
    catch (LinkageError ex) {
      System.out.println("No class");
    }
  }

  void test5() {
    try {
      int x = Cls2.value;
    }
    catch (Error ex) {
      System.out.println("No class");
    }
  }

  void test6() {
    try {
      int x = Cls2.value;
    }
    catch (Throwable ex) {
      System.out.println("No class");
    }
  }

  void test7() {
    try {
      int x = Cls2.value;
    }
    catch (Exception ex) <warning descr="Unreachable code">{
      System.out.println("No class");
    }</warning>
  }
}