class Super {
  void foo(String[] params, int... indices) {
      foo(new String[<caret>], 0);
    }
}