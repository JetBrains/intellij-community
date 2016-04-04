class Test {
  {
    String hello = new String("hello");
    Runnable x = () -> {
      System.out.println(<caret>hello);
    };
  }
}