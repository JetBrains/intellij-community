class Main {
  static <T> void of(T t) {}
  static <T> void of(T... t) {}

  void foo(String[] strs){
    Main.of(st<caret>rs);
  }
}