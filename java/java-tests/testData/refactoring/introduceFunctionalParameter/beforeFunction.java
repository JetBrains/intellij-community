class Test {
  void bar() {
    foo();
  }

  void foo() {
    <selection> 
    String s = "";
    System.out.println(s);
    </selection>
    System.out.println(s);
  }
}