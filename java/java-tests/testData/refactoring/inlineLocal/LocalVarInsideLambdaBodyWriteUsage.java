class Test {
  {
    Runnable x = () -> {
      int hello = 9;
      System.out.println(hello);
      ++hello;
      System.out.println(he<caret>llo);
    };
  }
}