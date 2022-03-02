class Test {

  public static void test() {
    boolean c = false;
    for(int a = 1; <warning descr="Condition 'c' is always 'false'"><caret>c</warning>; System.out.println("Just anything here: will not be executed anyways")) {
      System.out.println("Hello");
    }
  }
}