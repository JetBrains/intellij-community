class Test {

  public static void test() {
    for(int a = 1; <warning descr="Condition is always false">fal<caret>se</warning>; System.out.println("Just anything here: will not be executed anyways")) {
      <error descr="Unreachable statement">System.out.println("Hello");</error>
    }
  }
}