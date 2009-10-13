class Test {
    String s;

    Test(String s){
    super(s);
    System.out.println("hello");
  }

    void foo() {
      Test s = new Test(null);
      s.bar();
    }

    void bar() {}
}