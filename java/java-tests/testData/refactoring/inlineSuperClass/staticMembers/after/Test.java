class Test {
    public static final String S = "";

    static void foo(){
       System.out.println(S);
    }

    void bar() {
    System.out.println(Test.S);
    Test.foo();
  }
}