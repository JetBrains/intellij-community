class Test {
  public void print<caret>Array(String... p) {
    System.out.println(p[0]);
    System.out.println(p[1]);
  }

  public void doSmth() {
    String[] arr = {"smth1", "smth2", "smth3"};
    System.out.println(arr[0]);
    System.out.println(arr[1]);
  }

}