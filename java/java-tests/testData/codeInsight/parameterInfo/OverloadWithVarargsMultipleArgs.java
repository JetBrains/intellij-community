class Main {
  static <T> void of(T t) {}
  static <T> void of(T... t) {}

  void foo(){
    Main.of("<caret>", "");
  }
}