class Test {
  {
    Runnable x = () -> {
      String hello = new String("hello");
      System.out.println(<caret>hello);
    };
  }
}