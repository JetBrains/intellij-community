sealed class Super permits Test, Test1, Test2 {
  void doSmth() {
    System.out.println("hello");
  }
}