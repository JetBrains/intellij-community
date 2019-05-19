class Test {
    static {
      System.out.println("static");
    }

    {
      System.out.println("instance");
    }

    void foo() {
    Test s = new Test();
  }
}