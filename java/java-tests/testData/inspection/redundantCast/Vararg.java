public class Vararg {

  public void x() {
    int[] ints = new int[]{1, 2, 3};
    doSomething((Object) ints); // inspection shown with and without cast to Object
    System.out.printf("%s", (Object) new int[] {1, 2, 3});
  }

  void doSomething(Object... args) {}
}